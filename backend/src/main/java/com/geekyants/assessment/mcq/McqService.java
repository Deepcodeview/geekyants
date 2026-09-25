package com.geekyants.assessment.mcq;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.CodeAnalysisCompleted;
import com.geekyants.assessment.assignment.PipelineEvents.McqCompleted;
import com.geekyants.assessment.assignment.StepRunner;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.blueprint.BlueprintService;
import com.geekyants.assessment.codeanalysis.CodeAnalysisService;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.common.NotFoundException;
import com.geekyants.assessment.config.AppProperties;
import com.geekyants.assessment.evaluation.EvaluationService;
import com.geekyants.assessment.llm.LlmClient;
import com.geekyants.assessment.llm.LlmClient.LlmRequest;
import com.geekyants.assessment.llm.LlmException;
import com.geekyants.assessment.llm.PromptTemplates;
import com.geekyants.assessment.organization.JobProfile;
import com.geekyants.assessment.submission.Submission;
import com.geekyants.assessment.submission.SubmissionRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class McqService {

    private final SubmissionRepository submissions;
    private final McqAssessmentRepository assessments;
    private final BlueprintService blueprints;
    private final CodeAnalysisService codeAnalysis;
    private final EvaluationService evaluations;
    private final LlmClient llm;
    private final PromptTemplates prompts;
    private final Json json;
    private final AuditLog audit;
    private final StepRunner steps;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final AppProperties.Assessment config;
    private final Random random = new SecureRandom();

    public McqService(SubmissionRepository submissions, McqAssessmentRepository assessments,
                      BlueprintService blueprints, CodeAnalysisService codeAnalysis, EvaluationService evaluations,
                      LlmClient llm, PromptTemplates prompts, Json json, AuditLog audit, StepRunner steps,
                      TransactionTemplate tx, ApplicationEventPublisher events, AppProperties props) {
        this.submissions = submissions;
        this.assessments = assessments;
        this.blueprints = blueprints;
        this.codeAnalysis = codeAnalysis;
        this.evaluations = evaluations;
        this.llm = llm;
        this.prompts = prompts;
        this.json = json;
        this.audit = audit;
        this.steps = steps;
        this.tx = tx;
        this.events = events;
        this.config = props.assessment();
    }

    @Async
    @TransactionalEventListener
    public void onCodeAnalysisCompleted(CodeAnalysisCompleted event) {
        steps.run(event.assignmentId(), "mcq-generation", () -> generate(event.submissionId()));
    }

    void generate(String submissionId) {
        String input = tx.execute(s -> {
            Submission submission = submissions.findByPublicId(submissionId).orElseThrow();
            Assignment a = submission.getAssignment();
            if (a.getStatus() != AssignmentStatus.GENERATING_MCQ) {
                return null;
            }
            JobProfile job = json.read(a.getJob().getDescriptionJson(), JobProfile.class);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("questionCount", Map.of("min", config.mcqMinQuestions(), "max", config.mcqMaxQuestions()));
            data.put("job", Map.of("title", job.title(), "seniority", String.valueOf(job.seniority()),
                    "requiredSkills", job.requiredSkills() == null ? List.of() : job.requiredSkills()));
            data.put("requirementAnalysis", json.read(a.getRequirementAnalysisJson(), Object.class));
            data.put("blueprint", blueprints.content(blueprints.approved(a)));
            data.put("automatedEvaluation", evaluations.latestResult(submission));
            data.put("codeAnalysis", codeAnalysis.result(submission));
            return json.write(data);
        });
        if (input == null) {
            return;
        }

        GeneratedMcqs generated = llm.generate(new LlmRequest("mcq-generation", prompts.system("mcq-generation"),
                "<input>\n" + input + "\n</input>"), GeneratedMcqs.class);
        List<String> errors = McqValidator.validate(generated, config.mcqMinQuestions(), config.mcqMaxQuestions());
        if (!errors.isEmpty()) {
            throw new LlmException("Generated MCQs are invalid: " + String.join("; ", errors));
        }

        tx.executeWithoutResult(s -> {
            Submission submission = submissions.findByPublicId(submissionId).orElseThrow();
            Assignment a = submission.getAssignment();
            McqAssessment assessment = new McqAssessment(Ids.next(Ids.ASSESSMENT), a, submission,
                    config.mcqTimeLimitMinutes());
            int position = 1;
            for (GeneratedMcqs.Question q : generated.questions()) {
                // Models tend to favour certain answer positions; shuffle so position carries no signal.
                List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
                Collections.shuffle(order, random);
                List<String> options = order.stream().map(i -> q.options().get(i)).toList();
                assessment.addQuestion(new McqQuestion(assessment, Ids.sequence("MCQ", position), position,
                        q.category(), q.stem(), json.write(options), order.indexOf(q.correctOptionIndex()),
                        q.explanation(), blankToNull(q.sourceRef()), blankToNull(q.requirementCode())));
                position++;
            }
            assessments.save(assessment);
            a.transitionTo(AssignmentStatus.MCQ_READY);
            audit.record(a, "MCQ_GENERATED", AuditLog.SYSTEM,
                    "%s questions=%d".formatted(assessment.getPublicId(), generated.questions().size()));
        });
    }

    @Transactional
    public McqAssessment start(Assignment assignment, String actor) {
        McqAssessment assessment = current(assignment);
        assessment.start(Instant.now());
        // The caller's Assignment may be detached; change the one managed by this transaction.
        Assignment managed = assessment.getAssignment();
        managed.transitionTo(AssignmentStatus.MCQ_IN_PROGRESS);
        audit.record(managed, "MCQ_STARTED", actor, assessment.getPublicId());
        return assessment;
    }

    @Transactional
    public void answer(Assignment assignment, String questionCode, int selectedOption) {
        McqAssessment assessment = current(assignment);
        if (!assessment.acceptsAnswers(Instant.now())) {
            throw new ConflictException("The assessment is not accepting answers");
        }
        if (selectedOption < 0 || selectedOption >= McqValidator.OPTIONS) {
            throw new IllegalArgumentException("selectedOption must be between 0 and " + (McqValidator.OPTIONS - 1));
        }
        McqQuestion question = assessment.getQuestions().stream()
                .filter(q -> q.getCode().equals(questionCode)).findFirst()
                .orElseThrow(() -> new NotFoundException("Question", questionCode));
        question.answer(selectedOption, Instant.now());
    }

    @Transactional
    public McqAssessment finish(Assignment assignment, String actor) {
        McqAssessment assessment = current(assignment);
        complete(assessment, actor);
        return assessment;
    }

    @Transactional(readOnly = true)
    public McqAssessment current(Assignment assignment) {
        return assessments.findFirstByAssignmentOrderByCreatedAtDesc(assignment)
                .orElseThrow(() -> new NotFoundException("Assessment for", assignment.getPublicId()));
    }

    /** Auto-submits attempts whose time ran out (browser closed, connection lost). */
    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    public void finishExpired() {
        for (McqAssessment assessment : assessments.findByStatusAndDeadlineAtBefore(McqAssessment.Status.IN_PROGRESS,
                Instant.now())) {
            complete(assessment, AuditLog.SYSTEM);
        }
    }

    private void complete(McqAssessment assessment, String actor) {
        int score = (int) assessment.getQuestions().stream()
                .filter(q -> q.getAnswer() != null && q.getAnswer().isCorrect()).count();
        assessment.complete(Instant.now(), score);
        Assignment a = assessment.getAssignment();
        a.transitionTo(AssignmentStatus.MCQ_COMPLETED);
        audit.record(a, "MCQ_COMPLETED", actor, "%s score=%d/%d".formatted(assessment.getPublicId(), score,
                assessment.getMaxScore()));
        events.publishEvent(new McqCompleted(a.getPublicId(), assessment.getPublicId()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
