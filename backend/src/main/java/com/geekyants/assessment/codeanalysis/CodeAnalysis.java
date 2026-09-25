package com.geekyants.assessment.codeanalysis;

import com.geekyants.assessment.submission.Submission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "code_analyses")
public class CodeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(nullable = false)
    private String status;

    @Column(name = "result_json")
    private String resultJson;

    @Column(name = "files_analyzed", nullable = false)
    private int filesAnalyzed;

    /** Files left out (size budget, binary, generated) so reviewers know what the analysis did not see. */
    @Column(name = "files_skipped_json")
    private String filesSkippedJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CodeAnalysis() {
    }

    public CodeAnalysis(String publicId, Submission submission) {
        this.publicId = publicId;
        this.submission = submission;
        this.status = "COMPLETED";
        this.createdAt = Instant.now();
    }

    public void complete(String resultJson, int filesAnalyzed, String filesSkippedJson) {
        this.resultJson = resultJson;
        this.filesAnalyzed = filesAnalyzed;
        this.filesSkippedJson = filesSkippedJson;
        this.completedAt = Instant.now();
    }

    public String getPublicId() {
        return publicId;
    }

    public String getResultJson() {
        return resultJson;
    }

    public int getFilesAnalyzed() {
        return filesAnalyzed;
    }

    public String getFilesSkippedJson() {
        return filesSkippedJson;
    }
}
