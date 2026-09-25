package com.geekyants.assessment.assignment;

import com.geekyants.assessment.audit.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs an asynchronous pipeline step and moves the assignment to FAILED if the step throws. */
@Component
public class StepRunner {

    private static final Logger log = LoggerFactory.getLogger(StepRunner.class);

    private final TransactionTemplate tx;
    private final AssignmentRepository assignments;
    private final AuditLog audit;

    public StepRunner(TransactionTemplate tx, AssignmentRepository assignments, AuditLog audit) {
        this.tx = tx;
        this.assignments = assignments;
        this.audit = audit;
    }

    public void run(String assignmentId, String step, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.error("Step {} failed for assignment {}", step, assignmentId, e);
            tx.executeWithoutResult(status -> assignments.findByPublicId(assignmentId).ifPresent(a -> {
                a.fail(step + ": " + e.getMessage());
                audit.record(a, "STEP_FAILED", AuditLog.SYSTEM, step + ": " + e.getMessage());
            }));
        }
    }
}
