package com.geekyants.assessment.report;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.McqCompleted;
import com.geekyants.assessment.assignment.StepRunner;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.blueprint.Blueprint;
import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.BlueprintService;
import com.geekyants.assessment.blueprint.ProjectConfiguration;
import com.geekyants.assessment.codeanalysis.CodeAnalysis;
import com.geekyants.assessment.codeanalysis.CodeAnalysisRepository;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.common.NotFoundException;
import com.geekyants.assessment.evaluation.EvaluationResult;
import com.geekyants.assessment.evaluation.EvaluationRun;
import com.geekyants.assessment.evaluation.EvaluationRunRepository;
import com.geekyants.assessment.generation.Project;
import com.geekyants.assessment.generation.ProjectRepository;
import com.geekyants.assessment.github.GithubRepo;
import com.geekyants.assessment.github.GithubRepoRepository;
import com.geekyants.assessment.mcq.McqAssessment;
import com.geekyants.assessment.mcq.McqCategory;
import com.geekyants.assessment.mcq.McqQuestion;
import com.geekyants.assessment.mcq.McqService;
import com.geekyants.assessment.submission.Submission;
import com.geekyants.assessment.submission.SubmissionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

@Service
public class ReportService {

    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final ProjectRepository projects;
    private final GithubRepoRepository repos;
    private final BlueprintService blueprints;
    private final EvaluationRunRepository evaluationRuns;
    private final CodeAnalysisRepository analyses;
    private final McqService mcqs;
    private final FinalReportRepository reports;
    private final Json json;
    private final AuditLog audit;
    private final StepRunner steps;
    private final TransactionTemplate tx;

    public ReportService(AssignmentRepository assignments, SubmissionRepository submissions,
                         ProjectRepository projects, GithubRepoRepository repos, BlueprintService blueprints,
                         EvaluationRunRepository evaluationRuns, CodeAnalysisRepository analyses, McqService mcqs,
                         FinalReportRepository reports, Json json, AuditLog audit, StepRunner steps,
                         TransactionTemplate tx) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.projects = projects;
        this.repos = repos;
        this.blueprints = blueprints;
        this.evaluationRuns = evaluationRuns;
        this.analyses = analyses;
        this.mcqs = mcqs;
        this.reports = reports;
        this.json = json;
        this.audit = audit;
        this.steps = steps;
        this.tx = tx;
    }

    @Async
    @TransactionalEventListener
    public void onMcqCompleted(McqCompleted event) {
        // Self-invocation skips the @Transactional proxy, so open the transaction explicitly.
        steps.run(event.assignmentId(), "report", () -> tx.executeWithoutResult(s -> generate(event.assignmentId())));
    }

    @Transactional
    public FinalReport generate(String assignmentId) {
        Assignment a = assignments.findByPublicId(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment", assignmentId));
        Submission submission = submissions.findFirstByAssignmentOrderByAttemptNoDesc(a).orElseThrow();
        FinalReport report = reports.findByAssignment(a)
                .orElseGet(() -> new FinalReport(Ids.next(Ids.REPORT), a, submission));
        Instant now = Instant.now();
        ReportContent content = build(report.getPublicId(), now, a, submission);
        report.update(submission, json.write(content), now);
        reports.save(report);
        a.transitionTo(AssignmentStatus.REPORTED);
        audit.record(a, "REPORT_GENERATED", AuditLog.SYSTEM, report.getPublicId());
        return report;
    }

    @Transactional(readOnly = true)
    public ReportContent get(String assignmentId) {
        Assignment a = assignments.findByPublicId(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment", assignmentId));
        FinalReport report = reports.findByAssignment(a)
                .orElseThrow(() -> new NotFoundException("Report for", assignmentId));
        return json.read(report.getContentJson(), ReportContent.class);
    }

    private ReportContent build(String reportId, Instant now, Assignment a, Submission submission) {
        Blueprint blueprint = blueprints.approved(a);
        BlueprintContent bp = blueprints.content(blueprint);
        ProjectConfiguration config = json.read(a.getProjectConfigJson(), ProjectConfiguration.class);
        Project project = projects.findByAssignment(a).orElseThrow();
        GithubRepo repo = repos.findByAssignment(a).orElseThrow();

        EvaluationRun run = evaluationRuns.findFirstBySubmissionAndStatusOrderByDispatchedAtDesc(submission,
                EvaluationRun.Status.COMPLETED).orElse(null);
        EvaluationResult evaluation = run == null ? null : json.read(run.getResultJson(), EvaluationResult.class);
        CodeAnalysis analysis = analyses.findBySubmission(submission).orElse(null);
        CodeAnalysisResult codeResult = analysis == null ? null
                : json.read(analysis.getResultJson(), CodeAnalysisResult.class);
        McqAssessment assessment = mcqs.current(a);

        List<ReportContent.RequirementStatus> requirements = requirementStatuses(bp, codeResult);
        ReportContent.Mcq mcq = mcqSection(assessment);

        return new ReportContent(
                reportId, now,
                new ReportContent.Candidate(a.getCandidate().getExternalId(), a.getCandidate().getFullName(),
                        a.getJob().getExternalId(), a.getJob().getTitle(), a.getCompany().getExternalId(),
                        a.getCompany().getName()),
                new ReportContent.Project(a.getPublicId(), project.getPublicId(), blueprint.getPublicId(),
                        bp.projectName(), config.domain(), config.complexity().name(), config.technologies()),
                new ReportContent.Repository(repo.getPublicId(), repo.getFullName(), repo.getHtmlUrl(),
                        submission.getPublicId(), submission.getCommitSha(), submission.getTagRef(),
                        submission.getSubmittedAt(), submission.getTrigger().name()),
                evaluation == null ? null : new ReportContent.AutomatedEvaluation(run.getPublicId(),
                        run.getPipelineUrl(), evaluation.stages(), evaluation.visibleTests(),
                        evaluation.hiddenTests(), evaluation.failedTests()),
                requirements,
                codeResult == null ? null : new ReportContent.CodeQuality(analysis.getPublicId(),
                        codeResult.overallSummary(), codeResult.areas(), codeResult.findings(),
                        analysis.getFilesAnalyzed(),
                        json.read(analysis.getFilesSkippedJson(), new TypeReference<List<Map<String, String>>>() {
                        })),
                mcq,
                ScoreCalculator.scores(
                        ScoreCalculator.requirements(requirements),
                        ScoreCalculator.automatedEvaluation(evaluation),
                        codeResult == null ? null : ScoreCalculator.codeQuality(codeResult.areas()),
                        ScoreCalculator.mcq(mcq.correct(), mcq.total())));
    }

    private static List<ReportContent.RequirementStatus> requirementStatuses(BlueprintContent bp,
                                                                             CodeAnalysisResult result) {
        Map<String, CodeAnalysisResult.RequirementCoverage> coverage = result == null ? Map.of()
                : result.requirementCoverage().stream().collect(Collectors.toMap(
                CodeAnalysisResult.RequirementCoverage::requirementCode, Function.identity(), (x, y) -> x));
        List<ReportContent.RequirementStatus> out = new ArrayList<>();
        for (BlueprintContent.Requirement r : bp.requirements()) {
            CodeAnalysisResult.RequirementCoverage c = coverage.get(r.code());
            out.add(new ReportContent.RequirementStatus(r.code(), r.title(), r.mandatory(),
                    c == null ? CodeAnalysisResult.CoverageStatus.NOT_ASSESSED : c.status(),
                    c == null ? List.of() : c.implementationRefs(), c == null ? List.of() : c.testRefs(),
                    c == null ? null : c.notes()));
        }
        return out;
    }

    private ReportContent.Mcq mcqSection(McqAssessment assessment) {
        Map<McqCategory, int[]> tally = new EnumMap<>(McqCategory.class);
        List<ReportContent.QuestionResult> questions = new ArrayList<>();
        int correct = 0;
        for (McqQuestion q : assessment.getQuestions()) {
            boolean isCorrect = q.getAnswer() != null && q.getAnswer().isCorrect();
            correct += isCorrect ? 1 : 0;
            int[] t = tally.computeIfAbsent(q.getCategory(), k -> new int[2]);
            t[0] += isCorrect ? 1 : 0;
            t[1]++;
            questions.add(new ReportContent.QuestionResult(q.getCode(), q.getCategory(), q.getStem(),
                    json.read(q.getOptionsJson(), new TypeReference<List<String>>() {
                    }),
                    q.getAnswer() == null ? null : q.getAnswer().getSelectedOption(), q.getCorrectOption(), isCorrect,
                    q.getSourceRef(), q.getRequirementCode(), q.getExplanation()));
        }
        Map<McqCategory, ReportContent.CategoryScore> byCategory = new EnumMap<>(McqCategory.class);
        tally.forEach((k, v) -> byCategory.put(k, new ReportContent.CategoryScore(v[0], v[1])));
        return new ReportContent.Mcq(assessment.getPublicId(), questions.size(), correct, byCategory, questions);
    }
}
