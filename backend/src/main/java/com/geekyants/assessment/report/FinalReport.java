package com.geekyants.assessment.report;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.submission.Submission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "final_reports")
public class FinalReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(name = "content_json", nullable = false)
    private String contentJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected FinalReport() {
    }

    public FinalReport(String publicId, Assignment assignment, Submission submission) {
        this.publicId = publicId;
        this.assignment = assignment;
        this.submission = submission;
    }

    public void update(Submission submission, String contentJson, Instant generatedAt) {
        this.submission = submission;
        this.contentJson = contentJson;
        this.generatedAt = generatedAt;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getContentJson() {
        return contentJson;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
