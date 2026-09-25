package com.geekyants.assessment.submission;

import com.geekyants.assessment.assignment.Assignment;
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

/** An immutable pointer to the exact commit that is evaluated. Never updated after creation. */
@Entity
@Table(name = "submissions")
public class Submission {

    public enum Trigger { CANDIDATE, DEADLINE }

    public enum Status { FROZEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", updatable = false)
    private Assignment assignment;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Column(name = "commit_sha", nullable = false, updatable = false)
    private String commitSha;

    @Column(name = "tag_ref", nullable = false, updatable = false)
    private String tagRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, updatable = false)
    private Trigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Status status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    protected Submission() {
    }

    public Submission(String publicId, Assignment assignment, int attemptNo, String commitSha, String tagRef,
                      Trigger trigger, Instant submittedAt) {
        this.publicId = publicId;
        this.assignment = assignment;
        this.attemptNo = attemptNo;
        this.commitSha = commitSha;
        this.tagRef = tagRef;
        this.trigger = trigger;
        this.status = Status.FROZEN;
        this.submittedAt = submittedAt;
    }

    public String getPublicId() {
        return publicId;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getTagRef() {
        return tagRef;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
