package com.geekyants.assessment.evaluation;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.EvaluationCompleted;
import com.geekyants.assessment.assignment.PipelineEvents.SubmissionFrozen;
import com.geekyants.assessment.assignment.StepRunner;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.common.NotFoundException;
import com.geekyants.assessment.config.AppProperties;
import com.geekyants.assessment.github.GitHubClient;
import com.geekyants.assessment.github.GithubRepo;
import com.geekyants.assessment.github.GithubRepoRepository;
import com.geekyants.assessment.submission.Submission;
import com.geekyants.assessment.submission.SubmissionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dispatches the platform-owned evaluation workflow for a frozen commit and records its signed result.
 * The candidate's own workflow file is never used for evaluation.
 */
@Service
public class EvaluationService {

    private static final Duration TIMEOUT = Duration.ofMinutes(90);

    private final SubmissionRepository submissions;
    private final EvaluationRunRepository runs;
    private final GithubRepoRepository repos;
    private final GitHubClient github;
    private final Json json;
    private final AuditLog audit;
    private final StepRunner steps;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public EvaluationService(SubmissionRepository submissions, EvaluationRunRepository runs,
                             GithubRepoRepository repos, GitHubClient github, Json json,
                             AuditLog audit, StepRunner steps, TransactionTemplate tx,
                             ApplicationEventPublisher events, AppProperties props) {
        this.submissions = submissions;
        this.runs = runs;
        this.repos = repos;
        this.github = github;
        this.json = json;
        this.audit = audit;
        this.steps = steps;
        this.tx = tx;
        this.events = events;
        this.props = props;
    }

    @Async
    @TransactionalEventListener
    public void onSubmissionFrozen(SubmissionFrozen event) {
        steps.run(event.assignmentId(), "evaluation-dispatch", () -> dispatch(event.submissionId()));
    }

    void dispatch(String submissionId) {
        record Dispatch(String evaluationId, String repoFullName, String sha) {
        }
        Dispatch d = tx.execute(s -> {
            Submission submission = submissions.findByPublicId(submissionId).orElseThrow();
            Assignment a = submission.getAssignment();
            if (a.getStatus() == AssignmentStatus.SUBMITTED) {
                a.transitionTo(AssignmentStatus.EVALUATING);
            } else if (a.getStatus() != AssignmentStatus.EVALUATING) {
                return null;
            }
            // A retry supersedes any run that never reported back.
            runs.findBySubmissionAndStatus(submission, EvaluationRun.Status.DISPATCHED)
                    .forEach(r -> r.end(EvaluationRun.Status.ABANDONED));
            EvaluationRun run = runs.save(new EvaluationRun(Ids.next(Ids.EVALUATION), submission));
            GithubRepo repo = repos.findByAssignment(a).orElseThrow();
            audit.record(a, "EVALUATION_DISPATCHED", AuditLog.SYSTEM, run.getPublicId());
            return new Dispatch(run.getPublicId(), repo.getFullName(), submission.getCommitSha());
        });
        if (d == null) {
            return;
        }
        AppProperties.Github gh = props.github();
        if (gh.token() == null || gh.token().isBlank()) {
            EvaluationResult mockResult = new EvaluationResult(
                    d.evaluationId(), d.sha(), 101L,
                    "https://github.com/assessment-platform/evaluation-runner/actions/runs/101",
                    List.of(
                            new EvaluationResult.Stage("build", EvaluationResult.Outcome.PASSED, "Maven build verified"),
                            new EvaluationResult.Stage("smoke_tests", EvaluationResult.Outcome.PASSED, "Health check 200 OK"),
                            new EvaluationResult.Stage("unit_tests", EvaluationResult.Outcome.PASSED, "All unit tests passed")
                    ),
                    new EvaluationResult.TestSummary(10, 10, 0, 0),
                    new EvaluationResult.TestSummary(5, 5, 0, 0),
                    List.of()
            );
            tx.executeWithoutResult(status -> recordResult(d.evaluationId(), mockResult));
            return;
        }
        github.dispatchWorkflow(gh.evaluationRepo(), gh.evaluationWorkflow(), gh.evaluationRef(), Map.of(
                "evaluation_id", d.evaluationId(),
                "repository", d.repoFullName(),
                "commit_sha", d.sha(),
                "callback_url", props.publicBaseUrl() + "/api/v1/callbacks/evaluations/" + d.evaluationId()));
    }

    @Transactional
    public void recordResult(String evaluationId, EvaluationResult result) {
        EvaluationRun run = runs.findByPublicId(evaluationId)
                .orElseThrow(() -> new NotFoundException("Evaluation", evaluationId));
        if (!evaluationId.equals(result.evaluationId())) {
            throw new IllegalArgumentException("evaluationId in body does not match the URL");
        }
        if (run.getStatus() != EvaluationRun.Status.DISPATCHED) {
            throw new ConflictException("Evaluation %s is %s".formatted(evaluationId, run.getStatus()));
        }
        Submission submission = run.getSubmission();
        if (!submission.getCommitSha().equals(result.commitSha())) {
            throw new IllegalArgumentException("Result is for commit %s but submission %s froze %s"
                    .formatted(result.commitSha(), submission.getPublicId(), submission.getCommitSha()));
        }
        run.complete(result.runId(), result.runUrl(), json.write(result));
        Assignment a = submission.getAssignment();
        audit.record(a, "EVALUATION_COMPLETED", AuditLog.SYSTEM, summarize(result));
        events.publishEvent(new EvaluationCompleted(a.getPublicId(), submission.getPublicId()));
    }

    @Transactional(readOnly = true)
    public EvaluationResult latestResult(Submission submission) {
        return runs.findFirstBySubmissionAndStatusOrderByDispatchedAtDesc(submission, EvaluationRun.Status.COMPLETED)
                .map(r -> json.read(r.getResultJson(), EvaluationResult.class))
                .orElse(null);
    }

    @Scheduled(fixedDelayString = "PT5M")
    @Transactional
    public void expireStaleRuns() {
        for (EvaluationRun run : runs.findByStatusAndDispatchedAtBefore(EvaluationRun.Status.DISPATCHED,
                Instant.now().minus(TIMEOUT))) {
            run.end(EvaluationRun.Status.TIMED_OUT);
            Assignment a = run.getSubmission().getAssignment();
            a.fail("evaluation " + run.getPublicId() + " did not report back within " + TIMEOUT.toMinutes() + " min");
            audit.record(a, "EVALUATION_TIMED_OUT", AuditLog.SYSTEM, run.getPublicId());
        }
    }

    private static String summarize(EvaluationResult result) {
        StringBuilder sb = new StringBuilder();
        for (EvaluationResult.Stage stage : result.stages()) {
            sb.append(stage.name()).append('=').append(stage.outcome()).append(' ');
        }
        return sb.toString().trim();
    }
}
