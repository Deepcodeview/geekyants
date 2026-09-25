package com.geekyants.assessment.audit;

import com.geekyants.assessment.assignment.Assignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLog {

    public static final String SYSTEM = "system";

    private final AuditEventRepository events;

    public AuditLog(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Assignment assignment, String eventType, String actor, String detail) {
        events.save(new AuditEvent(assignment, eventType, actor, detail));
    }
}
