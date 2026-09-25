package com.geekyants.assessment.github;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.CandidateAccessRequested;
import com.geekyants.assessment.assignment.PipelineEvents.ProvisioningRequested;
import com.geekyants.assessment.assignment.StepRunner;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.blueprint.Blueprint;
import com.geekyants.assessment.blueprint.BlueprintService;
import com.geekyants.assessment.blueprint.ProjectConfiguration;
import com.geekyants.assessment.common.ExternalServiceException;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.config.AppProperties;
import com.geekyants.assessment.generation.Project;
import com.geekyants.assessment.generation.ProjectGenerator;
import com.geekyants.assessment.generation.ProjectGenerator.GeneratedProject;
import com.geekyants.assessment.generation.ProjectGenerator.GenerationInput;
import com.geekyants.assessment.generation.ProjectRepository;
import com.geekyants.assessment.github.GitHubClient.FileToCommit;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the candidate's private repository, pushes the generated project, and grants access.
 * Each step records its progress so a retry resumes where the last attempt stopped.
 */
@Service
public class ProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningService.class);

    private final AssignmentRepository assignments;
    private final ProjectRepository projects;
    private final GithubRepoRepository repos;
    private final BlueprintService blueprints;
    private final ProjectGenerator generator;
    private final GitHubClient github;
    private final Json json;
    private final AuditLog audit;
    private final StepRunner steps;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public ProvisioningService(AssignmentRepository assignments, ProjectRepository projects,
                               GithubRepoRepository repos, BlueprintService blueprints, ProjectGenerator generator,
                               GitHubClient github, Json json, AuditLog audit, StepRunner steps,
                               TransactionTemplate tx, ApplicationEventPublisher events, AppProperties props) {
        this.assignments = assignments;
        this.projects = projects;
        this.repos = repos;
        this.blueprints = blueprints;
        this.generator = generator;
        this.github = github;
        this.json = json;
        this.audit = audit;
        this.steps = steps;
        this.tx = tx;
        this.events = events;
        this.props = props;
    }

    @Async
    @TransactionalEventListener
    public void onProvisioningRequested(ProvisioningRequested event) {
        steps.run(event.assignmentId(), "provisioning", () -> provision(event.assignmentId()));
    }

    @Async
    @TransactionalEventListener
    public void onCandidateAccessRequested(CandidateAccessRequested event) {
        steps.run(event.assignmentId(), "candidate-access", () -> grantAccess(event.assignmentId()));
    }

    record Plan(String repoName, GithubRepo.Status status, List<FileToCommit> files, String assignmentId,
                String projectName) {
    }

    void provision(String assignmentId) {
        Plan plan = tx.execute(s -> preparePlan(assignmentId));
        GithubRepo.Status status = plan.status();

        if (status == GithubRepo.Status.PENDING) {
            GitHubClient.RepoInfo info = github.findRepository(plan.repoName()).orElseGet(() ->
                    github.createPrivateRepository(plan.repoName(),
                            "Assessment %s: %s".formatted(plan.assignmentId(), plan.projectName())));
            updateRepo(assignmentId, r -> r.markCreated(info.id(), info.htmlUrl(), info.defaultBranch()));
            status = GithubRepo.Status.CREATED;
        }
        if (status == GithubRepo.Status.CREATED) {
            GithubRepo repo = tx.execute(s -> repoFor(assignmentId));
            github.commitFiles(plan.repoName(), repo.getDefaultBranch(), plan.files(),
                    "Assessment project for " + plan.assignmentId());
            updateRepo(assignmentId, GithubRepo::markInitialized);
            status = GithubRepo.Status.INITIALIZED;
        }
        if (status == GithubRepo.Status.INITIALIZED) {
            applyRulesets(plan.repoName());
            long hookId = github.createWebhook(plan.repoName(),
                    props.publicBaseUrl() + "/api/v1/webhooks/github", props.github().webhookSecret());
            updateRepo(assignmentId, r -> r.markConfigured(hookId));
        }

        tx.executeWithoutResult(s -> {
            Assignment a = assignments.findByPublicId(assignmentId).orElseThrow();
            a.transitionTo(AssignmentStatus.AWAITING_GITHUB_USERNAME);
            audit.record(a, "REPOSITORY_PROVISIONED", AuditLog.SYSTEM, repoFor(assignmentId).getFullName());
            if (a.getCandidate().getGithubUsername() != null) {
                events.publishEvent(new CandidateAccessRequested(assignmentId));
            }
        });
    }

    void grantAccess(String assignmentId) {
        record Target(String repoName, String username) {
        }
        Target target = tx.execute(s -> {
            Assignment a = assignments.findByPublicId(assignmentId).orElseThrow();
            if (a.getStatus() != AssignmentStatus.AWAITING_GITHUB_USERNAME) {
                return null;
            }
            return new Target(repoFor(assignmentId).getName(), a.getCandidate().getGithubUsername());
        });
        if (target == null || target.username() == null) {
            return;
        }
        // Sends a repository invitation. Access covers this repository only, never the organization.
        github.setCollaborator(target.repoName(), target.username(), "push");
        tx.executeWithoutResult(s -> {
            Assignment a = assignments.findByPublicId(assignmentId).orElseThrow();
            repoFor(assignmentId).setCandidateAccess(GithubRepo.Access.WRITE);
            a.markAssigned(Instant.now());
            audit.record(a, "CANDIDATE_ACCESS_GRANTED", AuditLog.SYSTEM,
                    "github=%s due=%s".formatted(target.username(), a.getDueAt()));
        });
    }

    private Plan preparePlan(String assignmentId) {
        Assignment assignment = assignments.findByPublicId(assignmentId).orElseThrow();
        Blueprint blueprint = blueprints.approved(assignment);
        ProjectConfiguration config = json.read(assignment.getProjectConfigJson(), ProjectConfiguration.class);
        Project project = projects.findByAssignment(assignment).orElse(null);
        String projectId = project != null ? project.getPublicId() : Ids.next(Ids.PROJECT);

        GeneratedProject generated = generator.generate(new GenerationInput(assignmentId, projectId,
                blueprint.getPublicId(), config, blueprints.content(blueprint)));
        if (project == null) {
            project = projects.save(new Project(projectId, assignment, blueprint,
                    blueprints.content(blueprint).projectName(), generated.templateKey(),
                    json.write(generated.files().stream().map(FileToCommit::path).toList())));
        }

        GithubRepo repo = repos.findByAssignment(assignment).orElse(null);
        if (repo == null) {
            repo = repos.save(new GithubRepo(Ids.next(Ids.REPOSITORY), assignment, project,
                    github.organization(), repositoryName(assignment)));
        }
        return new Plan(repo.getName(), repo.getStatus(), generated.files(), assignmentId, project.getName());
    }

    private void applyRulesets(String repoName) {
        try {
            github.applyRulesets(repoName);
        } catch (ExternalServiceException e) {
            if (props.github().enforceRulesets()) {
                throw e;
            }
            log.warn("Rulesets not applied to {}; submissions rely on tags and read-only access only", repoName, e);
        }
    }

    private GithubRepo repoFor(String assignmentId) {
        Assignment a = assignments.findByPublicId(assignmentId).orElseThrow();
        return repos.findByAssignment(a).orElseThrow();
    }

    private void updateRepo(String assignmentId, java.util.function.Consumer<GithubRepo> change) {
        tx.executeWithoutResult(s -> change.accept(repoFor(assignmentId)));
    }

    /** e.g. assessment-assign-7k2m9qx4tb-cand-7821 */
    static String repositoryName(Assignment assignment) {
        String candidate = assignment.getCandidate().getExternalId().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-");
        String name = "assessment-%s-%s".formatted(assignment.getPublicId().toLowerCase(Locale.ROOT), candidate);
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
