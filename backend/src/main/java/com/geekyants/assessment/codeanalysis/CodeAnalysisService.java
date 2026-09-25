package com.geekyants.assessment.codeanalysis;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.CodeAnalysisCompleted;
import com.geekyants.assessment.assignment.PipelineEvents.EvaluationCompleted;
import com.geekyants.assessment.assignment.StepRunner;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.BlueprintService;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.CoverageStatus;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.RequirementCoverage;
import com.geekyants.assessment.codeanalysis.SourceSelector.Selection;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.config.AppProperties;
import com.geekyants.assessment.evaluation.EvaluationService;
import com.geekyants.assessment.github.GitHubClient;
import com.geekyants.assessment.github.GitHubClient.TreeEntry;
import com.geekyants.assessment.github.GithubRepoRepository;
import com.geekyants.assessment.llm.LlmClient;
import com.geekyants.assessment.llm.LlmClient.LlmRequest;
import com.geekyants.assessment.llm.PromptTemplates;
import com.geekyants.assessment.submission.Submission;
import com.geekyants.assessment.submission.SubmissionRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** Analyses the exact submitted commit against the approved blueprint. */
@Service
public class CodeAnalysisService {

    private final SubmissionRepository submissions;
    private final CodeAnalysisRepository analyses;
    private final GithubRepoRepository repos;
    private final BlueprintService blueprints;
    private final EvaluationService evaluations;
    private final GitHubClient github;
    private final LlmClient llm;
    private final PromptTemplates prompts;
    private final Json json;
    private final AuditLog audit;
    private final StepRunner steps;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public CodeAnalysisService(SubmissionRepository submissions, CodeAnalysisRepository analyses,
                               GithubRepoRepository repos, BlueprintService blueprints, EvaluationService evaluations,
                               GitHubClient github, LlmClient llm, PromptTemplates prompts, Json json,
                               AuditLog audit, StepRunner steps, TransactionTemplate tx,
                               ApplicationEventPublisher events, AppProperties props) {
        this.submissions = submissions;
        this.analyses = analyses;
        this.repos = repos;
        this.blueprints = blueprints;
        this.evaluations = evaluations;
        this.github = github;
        this.llm = llm;
        this.prompts = prompts;
        this.json = json;
        this.audit = audit;
        this.steps = steps;
        this.tx = tx;
        this.events = events;
        this.props = props;
    }

    @Async
    @TransactionalEventListener
    public void onEvaluationCompleted(EvaluationCompleted event) {
        steps.run(event.assignmentId(), "code-analysis", () -> analyze(event.submissionId()));
    }

    record Input(String repoName, String sha, BlueprintContent blueprint, Object evaluation) {
    }

    void analyze(String submissionId) {
        Input input = tx.execute(s -> {
            Submission submission = submissions.findByPublicId(submissionId).orElseThrow();
            Assignment a = submission.getAssignment();
            if (a.getStatus() == AssignmentStatus.EVALUATING) {
                a.transitionTo(AssignmentStatus.ANALYZING_CODE);
            } else if (a.getStatus() != AssignmentStatus.ANALYZING_CODE) {
                return null;
            }
            return new Input(repos.findByAssignment(a).orElseThrow().getName(), submission.getCommitSha(),
                    blueprints.content(blueprints.approved(a)), evaluations.latestResult(submission));
        });
        if (input == null) {
            return;
        }

        List<TreeEntry> files = github.listFiles(input.repoName(), input.sha());
        Selection selection = SourceSelector.select(files, props.assessment().analysisMaxSourceChars());
        StringBuilder sources = new StringBuilder();
        for (TreeEntry f : selection.selected()) {
            String content = new String(github.readBlob(input.repoName(), f.sha()), StandardCharsets.UTF_8);
            sources.append("<file path=\"").append(f.path()).append("\">\n").append(content).append("\n</file>\n");
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("commitSha", input.sha());
        context.put("blueprint", input.blueprint());
        context.put("automatedEvaluation", input.evaluation());
        context.put("filesNotIncluded", selection.skipped());
        String user = "<context>\n" + json.write(context) + "\n</context>\n<repository>\n" + sources + "</repository>";

        CodeAnalysisResult raw = llm.generate(new LlmRequest("code-analysis", prompts.system("code-analysis"), user),
                CodeAnalysisResult.class);
        CodeAnalysisResult result = alignCoverage(raw, input.blueprint());

        tx.executeWithoutResult(s -> {
            Submission submission = submissions.findByPublicId(submissionId).orElseThrow();
            CodeAnalysis analysis = analyses.findBySubmission(submission)
                    .orElseGet(() -> new CodeAnalysis(Ids.next(Ids.CODE_ANALYSIS), submission));
            analysis.complete(json.write(result), selection.selected().size(), json.write(selection.skipped()));
            analyses.save(analysis);
            Assignment a = submission.getAssignment();
            a.transitionTo(AssignmentStatus.GENERATING_MCQ);
            audit.record(a, "CODE_ANALYSIS_COMPLETED", AuditLog.SYSTEM, "%s files=%d skipped=%d"
                    .formatted(analysis.getPublicId(), selection.selected().size(), selection.skipped().size()));
            events.publishEvent(new CodeAnalysisCompleted(a.getPublicId(), submissionId));
        });
    }

    @Transactional(readOnly = true)
    public CodeAnalysisResult result(Submission submission) {
        return analyses.findBySubmission(submission)
                .map(a -> json.read(a.getResultJson(), CodeAnalysisResult.class))
                .orElse(null);
    }

    /** Exactly one coverage entry per blueprint requirement, in blueprint order; unknown codes dropped. */
    static CodeAnalysisResult alignCoverage(CodeAnalysisResult raw, BlueprintContent blueprint) {
        Map<String, RequirementCoverage> byCode = (raw.requirementCoverage() == null ? List.<RequirementCoverage>of()
                : raw.requirementCoverage()).stream()
                .collect(Collectors.toMap(RequirementCoverage::requirementCode, Function.identity(), (x, y) -> x));
        List<RequirementCoverage> aligned = new ArrayList<>();
        for (BlueprintContent.Requirement r : blueprint.requirements()) {
            aligned.add(byCode.getOrDefault(r.code(), new RequirementCoverage(r.code(), CoverageStatus.NOT_ASSESSED,
                    List.of(), List.of(), "Not covered by the analysis output")));
        }
        return new CodeAnalysisResult(raw.overallSummary(), aligned, raw.areas(), raw.findings(),
                raw.implementationDecisions());
    }
}
