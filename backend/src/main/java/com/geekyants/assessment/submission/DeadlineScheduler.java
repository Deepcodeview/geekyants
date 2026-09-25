package com.geekyants.assessment.submission;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.audit.AuditLog;
import java.time.Instant;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Submits the head of the default branch for assignments whose development time has run out. */
@Component
class DeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineScheduler.class);

    private final AssignmentRepository assignments;
    private final SubmissionService submissions;

    DeadlineScheduler(AssignmentRepository assignments, SubmissionService submissions) {
        this.assignments = assignments;
        this.submissions = submissions;
    }

    @Scheduled(fixedDelayString = "PT1M")
    void submitOverdue() {
        for (Assignment a : assignments.findByStatusInAndDueAtBefore(
                EnumSet.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS), Instant.now())) {
            try {
                submissions.submit(a.getPublicId(), Submission.Trigger.DEADLINE, AuditLog.SYSTEM);
            } catch (RuntimeException e) {
                // Retried on the next tick; a candidate submit racing this one is expected to win or lose cleanly.
                log.warn("Deadline submission failed for {}", a.getPublicId(), e);
            }
        }
    }
}
