package com.geekyants.assessment.admin;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentService;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.mcq.McqService;
import com.geekyants.assessment.submission.SubmissionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resumes a FAILED assignment at the step that failed. Every step handler tolerates being re-run. */
@Service
public class PipelineRetryService {

    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private final AssignmentService assignments;
    private final SubmissionRepository submissions;
    private final McqService mcqs;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;

    public PipelineRetryService(AssignmentService assignments, SubmissionRepository submissions, McqService mcqs,
                                AuditLog audit, ApplicationEventPublisher events) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.mcqs = mcqs;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public Assignment retry(String assignmentId, String actor) {
        Assignment a = assignments.get(assignmentId);
        // A step interrupted by a restart never reaches FAILED; treat it as failed once it has been idle a while.
        // EVALUATING is excluded: the runner may still report back, and its own timeout handles silence.
        if (a.getStatus().isBackgroundStep() && a.getStatus() != AssignmentStatus.EVALUATING
                && a.getUpdatedAt().isBefore(Instant.now().minus(STALE_AFTER))) {
            a.fail("step interrupted (no progress for " + STALE_AFTER.toMinutes() + " min)");
        }
        AssignmentStatus resumed = a.retry();
        audit.record(a, "STEP_RETRIED", actor, "resumed at " + resumed);
        Supplier<String> submission = () -> submissions.findFirstByAssignmentOrderByAttemptNoDesc(a)
                .orElseThrow().getPublicId();
        Object event = switch (resumed) {
            case ANALYZING_REQUIREMENTS -> new PipelineEvents.RequirementAnalysisRequested(assignmentId);
            case BLUEPRINT_GENERATING -> new PipelineEvents.BlueprintRequested(assignmentId, null);
            case PROVISIONING -> new PipelineEvents.ProvisioningRequested(assignmentId);
            case AWAITING_GITHUB_USERNAME -> new PipelineEvents.CandidateAccessRequested(assignmentId);
            case SUBMITTED, EVALUATING -> new PipelineEvents.SubmissionFrozen(assignmentId, submission.get());
            case ANALYZING_CODE -> new PipelineEvents.EvaluationCompleted(assignmentId, submission.get());
            case GENERATING_MCQ -> new PipelineEvents.CodeAnalysisCompleted(assignmentId, submission.get());
            case MCQ_COMPLETED -> new PipelineEvents.McqCompleted(assignmentId, mcqs.current(a).getPublicId());
            default -> null;
        };
        if (event != null) {
            events.publishEvent(event);
        }
        return a;
    }
}
