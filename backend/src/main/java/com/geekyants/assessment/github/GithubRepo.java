package com.geekyants.assessment.github;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.common.BaseEntity;
import com.geekyants.assessment.generation.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Mapping between an assignment and its dedicated GitHub repository. */
@Entity
@Table(name = "github_repositories")
public class GithubRepo extends BaseEntity {

    /** Provisioning progress; each step is idempotent so a failed provisioning can be retried. */
    public enum Status { PENDING, CREATED, INITIALIZED, CONFIGURED }

    public enum Access { NONE, WRITE, READ }

    @Column(name = "public_id", nullable = false, updatable = false)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "github_repo_id")
    private Long githubRepoId;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch;

    @Column(name = "webhook_id")
    private Long webhookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_access", nullable = false)
    private Access candidateAccess;

    protected GithubRepo() {
    }

    public GithubRepo(String publicId, Assignment assignment, Project project, String owner, String name) {
        this.publicId = publicId;
        this.assignment = assignment;
        this.project = project;
        this.owner = owner;
        this.name = name;
        this.defaultBranch = "main";
        this.status = Status.PENDING;
        this.candidateAccess = Access.NONE;
    }

    public void markCreated(long githubRepoId, String htmlUrl, String defaultBranch) {
        this.githubRepoId = githubRepoId;
        this.htmlUrl = htmlUrl;
        this.defaultBranch = defaultBranch;
        this.status = Status.CREATED;
    }

    public void markInitialized() {
        this.status = Status.INITIALIZED;
    }

    public void markConfigured(long webhookId) {
        this.webhookId = webhookId;
        this.status = Status.CONFIGURED;
    }

    public void setCandidateAccess(Access candidateAccess) {
        this.candidateAccess = candidateAccess;
    }

    public String getPublicId() {
        return publicId;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public Project getProject() {
        return project;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return owner + "/" + name;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public Status getStatus() {
        return status;
    }

    public Access getCandidateAccess() {
        return candidateAccess;
    }
}
