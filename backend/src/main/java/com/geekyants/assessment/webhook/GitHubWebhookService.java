package com.geekyants.assessment.webhook;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.github.GithubRepo;
import com.geekyants.assessment.github.GithubRepoRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);

    private final GithubRepoRepository repos;
    private final PushEventRepository pushes;
    private final CiRunRepository ciRuns;
    private final AuditLog audit;

    public GitHubWebhookService(GithubRepoRepository repos, PushEventRepository pushes, CiRunRepository ciRuns,
                                AuditLog audit) {
        this.repos = repos;
        this.pushes = pushes;
        this.ciRuns = ciRuns;
        this.audit = audit;
    }

    @Transactional
    public void handle(String eventType, String deliveryId, JsonNode payload) {
        Optional<GithubRepo> repo = findRepo(payload);
        if (repo.isEmpty()) {
            log.debug("Ignoring {} for unknown repository", eventType);
            return;
        }
        switch (eventType) {
            case "push" -> onPush(repo.get(), deliveryId, payload);
            case "workflow_run" -> onWorkflowRun(repo.get(), payload);
            default -> log.debug("Ignoring event {}", eventType);
        }
    }

    private void onPush(GithubRepo repo, String deliveryId, JsonNode payload) {
        // GitHub redelivers on timeouts; the delivery ID makes handling idempotent.
        if (pushes.existsByDeliveryId(deliveryId)) {
            return;
        }
        String ref = payload.path("ref").asString();
        JsonNode head = payload.path("head_commit");
        PushEvent push = pushes.save(new PushEvent(repo, deliveryId, ref,
                payload.path("before").asString(null), payload.path("after").asString(),
                payload.path("pusher").path("name").asString(null),
                payload.path("forced").asBoolean(false),
                payload.path("commits").size(),
                head.isMissingNode() || head.isNull() ? null : head.path("message").asString(null)));

        Assignment assignment = repo.getAssignment();
        if (push.isForced()) {
            audit.record(assignment, "FORCE_PUSH_DETECTED", push.getPusherLogin(), ref);
        }
        if (assignment.getStatus() == AssignmentStatus.ASSIGNED
                && ref.equals("refs/heads/" + repo.getDefaultBranch())) {
            assignment.transitionTo(AssignmentStatus.IN_PROGRESS);
            audit.record(assignment, "DEVELOPMENT_STARTED", push.getPusherLogin(), push.getAfterSha());
        }
    }

    private void onWorkflowRun(GithubRepo repo, JsonNode payload) {
        JsonNode run = payload.path("workflow_run");
        long runId = run.path("id").asLong();
        CiRun ciRun = ciRuns.findByGithubRunId(runId).orElseGet(() -> new CiRun(repo, runId));
        ciRun.update(run.path("head_sha").asString(), run.path("name").asString(null),
                run.path("status").asString("unknown"), run.path("conclusion").asString(null),
                run.path("html_url").asString(null));
        ciRuns.save(ciRun);
    }

    private Optional<GithubRepo> findRepo(JsonNode payload) {
        JsonNode repository = payload.path("repository");
        String owner = repository.path("owner").path("login").asString(null);
        String name = repository.path("name").asString(null);
        if (owner == null || name == null) {
            return Optional.empty();
        }
        return repos.findByOwnerIgnoreCaseAndNameIgnoreCase(owner, name);
    }
}
