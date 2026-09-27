package com.geekyants.assessment.audit;

import com.geekyants.assessment.assignment.Assignment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String actor;

    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    AuditEvent(Assignment assignment, String eventType, String actor, String detail) {
        this.assignment = assignment;
        this.eventType = eventType;
        this.actor = actor;
        this.detail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 4000));
        this.createdAt = Instant.now();
    }

    public String getEventType() {
        return eventType;
    }

    public String getActor() {
        return actor;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getId() {
        return id;
    }

    public Assignment getAssignment() {
        return assignment;
    }
}
