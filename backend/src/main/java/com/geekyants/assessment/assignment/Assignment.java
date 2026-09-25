package com.geekyants.assessment.assignment;

import com.geekyants.assessment.common.BaseEntity;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.organization.Candidate;
import com.geekyants.assessment.organization.Company;
import com.geekyants.assessment.organization.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "assignments")
public class Assignment extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

    @Column(name = "requirement_analysis_json")
    private String requirementAnalysisJson;

    @Column(name = "project_config_json")
    private String projectConfigJson;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "failed_from_status")
    private AssignmentStatus failedFromStatus;

    @Version
    @Column(name = "row_version")
    private long rowVersion;

    protected Assignment() {
    }

    public Assignment(String publicId, Company company, Job job, Candidate candidate, int durationDays) {
        this.publicId = publicId;
        this.company = company;
        this.job = job;
        this.candidate = candidate;
        this.durationDays = durationDays;
        this.status = AssignmentStatus.CREATED;
    }

    public void transitionTo(AssignmentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Assignment %s cannot move from %s to %s"
                    .formatted(publicId, status, target));
        }
        status = target;
        failureReason = null;
    }

    public void fail(String reason) {
        if (status == AssignmentStatus.FAILED) {
            return;
        }
        failedFromStatus = status;
        status = AssignmentStatus.FAILED;
        failureReason = reason == null ? "unknown error"
                : reason.substring(0, Math.min(reason.length(), 2000));
    }

    /** Returns to the state that failed so the admin can re-run that step. */
    public AssignmentStatus retry() {
        if (status != AssignmentStatus.FAILED || failedFromStatus == null) {
            throw new ConflictException("Assignment %s is not in a retryable state".formatted(publicId));
        }
        status = failedFromStatus;
        failedFromStatus = null;
        failureReason = null;
        return status;
    }

    /** Starts the development clock the first time the candidate is given access. */
    public void markAssigned(Instant now) {
        transitionTo(AssignmentStatus.ASSIGNED);
        assignedAt = now;
        dueAt = now.plus(Duration.ofDays(durationDays));
    }

    public boolean isPastDue(Instant now) {
        return dueAt != null && now.isAfter(dueAt);
    }

    public void setRequirementAnalysisJson(String requirementAnalysisJson) {
        this.requirementAnalysisJson = requirementAnalysisJson;
    }

    public void configure(String projectConfigJson, int durationDays) {
        this.projectConfigJson = projectConfigJson;
        this.durationDays = durationDays;
    }

    public String getPublicId() {
        return publicId;
    }

    public Company getCompany() {
        return company;
    }

    public Job getJob() {
        return job;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public String getRequirementAnalysisJson() {
        return requirementAnalysisJson;
    }

    public String getProjectConfigJson() {
        return projectConfigJson;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
