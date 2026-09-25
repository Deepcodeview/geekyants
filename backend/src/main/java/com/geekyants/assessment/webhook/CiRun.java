package com.geekyants.assessment.webhook;

import com.geekyants.assessment.common.BaseEntity;
import com.geekyants.assessment.github.GithubRepo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A run of the candidate-visible feedback workflow. The candidate can edit that workflow,
 * so these results are shown as feedback only and never used for scoring.
 */
@Entity
@Table(name = "ci_runs")
public class CiRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id")
    private GithubRepo repository;

    @Column(name = "github_run_id", nullable = false)
    private long githubRunId;

    @Column(name = "head_sha", nullable = false)
    private String headSha;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(nullable = false)
    private String status;

    private String conclusion;

    @Column(name = "html_url")
    private String htmlUrl;

    protected CiRun() {
    }

    public CiRun(GithubRepo repository, long githubRunId) {
        this.repository = repository;
        this.githubRunId = githubRunId;
    }

    public void update(String headSha, String workflowName, String status, String conclusion, String htmlUrl) {
        this.headSha = headSha;
        this.workflowName = workflowName;
        this.status = status;
        this.conclusion = conclusion;
        this.htmlUrl = htmlUrl;
    }

    public String getHeadSha() {
        return headSha;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getStatus() {
        return status;
    }

    public String getConclusion() {
        return conclusion;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }
}
