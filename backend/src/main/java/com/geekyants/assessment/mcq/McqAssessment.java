package com.geekyants.assessment.mcq;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.submission.Submission;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mcq_assessments")
public class McqAssessment {

    public enum Status { READY, IN_PROGRESS, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "time_limit_minutes", nullable = false)
    private int timeLimitMinutes;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<McqQuestion> questions = new ArrayList<>();

    protected McqAssessment() {
    }

    public McqAssessment(String publicId, Assignment assignment, Submission submission, int timeLimitMinutes) {
        this.publicId = publicId;
        this.assignment = assignment;
        this.submission = submission;
        this.timeLimitMinutes = timeLimitMinutes;
        this.status = Status.READY;
        this.createdAt = Instant.now();
    }

    public void addQuestion(McqQuestion question) {
        questions.add(question);
    }

    public void start(Instant now) {
        if (status != Status.READY) {
            throw new ConflictException("Assessment already " + status);
        }
        status = Status.IN_PROGRESS;
        startedAt = now;
        deadlineAt = now.plus(Duration.ofMinutes(timeLimitMinutes));
    }

    public boolean acceptsAnswers(Instant now) {
        return status == Status.IN_PROGRESS && !now.isAfter(deadlineAt);
    }

    public void complete(Instant now, int score) {
        if (status != Status.IN_PROGRESS) {
            throw new ConflictException("Assessment is " + status);
        }
        this.status = Status.COMPLETED;
        this.completedAt = now;
        this.score = score;
        this.maxScore = questions.size();
    }

    public String getPublicId() {
        return publicId;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public Submission getSubmission() {
        return submission;
    }

    public Status getStatus() {
        return status;
    }

    public int getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getScore() {
        return score;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public List<McqQuestion> getQuestions() {
        return questions;
    }
}
