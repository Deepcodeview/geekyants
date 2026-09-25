package com.geekyants.assessment.evaluation;

import com.geekyants.assessment.submission.Submission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "evaluation_runs")
public class EvaluationRun {

    public enum Status { DISPATCHED, COMPLETED, ABANDONED, TIMED_OUT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "pipeline_run_id")
    private Long pipelineRunId;

    @Column(name = "pipeline_url")
    private String pipelineUrl;

    @Column(name = "result_json")
    private String resultJson;

    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected EvaluationRun() {
    }

    public EvaluationRun(String publicId, Submission submission) {
        this.publicId = publicId;
        this.submission = submission;
        this.status = Status.DISPATCHED;
        this.dispatchedAt = Instant.now();
    }

    public void complete(Long runId, String runUrl, String resultJson) {
        this.pipelineRunId = runId;
        this.pipelineUrl = runUrl;
        this.resultJson = resultJson;
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void end(Status status) {
        this.status = status;
        this.completedAt = Instant.now();
    }

    public String getPublicId() {
        return publicId;
    }

    public Submission getSubmission() {
        return submission;
    }

    public Status getStatus() {
        return status;
    }

    public String getPipelineUrl() {
        return pipelineUrl;
    }

    public String getResultJson() {
        return resultJson;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }
}
