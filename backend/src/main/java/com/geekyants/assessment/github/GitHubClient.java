package com.geekyants.assessment.github;

import com.geekyants.assessment.common.ExternalServiceException;
import com.geekyants.assessment.config.AppProperties;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** Minimal GitHub REST client covering what the platform needs. All repos live in one organization. */
@Component
public class GitHubClient {

    private final RestClient http;
    private final String org;

    public GitHubClient(RestClient.Builder builder, AppProperties props) {
        AppProperties.Github config = props.github();
        this.org = config.organization();
        this.http = builder
                .baseUrl(config.apiUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.token())
                .build();
    }

    public String organization() {
        return org;
    }

    public record RepoInfo(long id, String name, String htmlUrl, String defaultBranch) {
    }

    public record FileToCommit(String path, String content) {
    }

    public record TreeEntry(String path, String sha, long size) {
    }

    public RepoInfo createPrivateRepository(String name, String description) {
        JsonNode body = call(() -> http.post().uri("/orgs/{org}/repos", org)
                .body(Map.of(
                        "name", name,
                        "description", description,
                        "private", true,
                        "auto_init", true,
                        "has_issues", false,
                        "has_wiki", false,
                        "has_projects", false))
                .retrieve().body(JsonNode.class), "create repository " + name);
        return toRepoInfo(body);
    }

    public Optional<RepoInfo> findRepository(String name) {
        try {
            return Optional.of(toRepoInfo(http.get().uri("/repos/{org}/{repo}", org, name)
                    .retrieve().body(JsonNode.class)));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException e) {
            throw failure("get repository " + name, e);
        }
    }

    /**
     * Replaces the branch content with {@code files} in a single commit on top of the current head.
     * Uses the Git Data API so the whole skeleton lands atomically.
     */
    public String commitFiles(String repo, String branch, List<FileToCommit> files, String message) {
        String headSha = branchHead(repo, branch);
        List<Map<String, String>> tree = new ArrayList<>();
        for (FileToCommit file : files) {
            tree.add(Map.of("path", file.path(), "mode", "100644", "type", "blob", "content", file.content()));
        }
        JsonNode treeNode = call(() -> http.post().uri("/repos/{org}/{repo}/git/trees", org, repo)
                .body(Map.of("tree", tree)).retrieve().body(JsonNode.class), "create tree");
        JsonNode commit = call(() -> http.post().uri("/repos/{org}/{repo}/git/commits", org, repo)
                .body(Map.of("message", message, "tree", treeNode.get("sha").asString(), "parents", List.of(headSha)))
                .retrieve().body(JsonNode.class), "create commit");
        String commitSha = commit.get("sha").asString();
        call(() -> http.patch().uri("/repos/{org}/{repo}/git/refs/heads/{branch}", org, repo, branch)
                .body(Map.of("sha", commitSha, "force", false)).retrieve().toBodilessEntity(), "update ref");
        return commitSha;
    }

    public String branchHead(String repo, String branch) {
        JsonNode ref = call(() -> http.get().uri("/repos/{org}/{repo}/git/ref/heads/{branch}", org, repo, branch)
                .retrieve().body(JsonNode.class), "get branch head");
        return ref.get("object").get("sha").asString();
    }

    public long createWebhook(String repo, String url, String secret) {
        JsonNode hook = call(() -> http.post().uri("/repos/{org}/{repo}/hooks", org, repo)
                .body(Map.of(
                        "name", "web",
                        "active", true,
                        "events", List.of("push", "workflow_run"),
                        "config", Map.of("url", url, "content_type", "json", "secret", secret,
                                "insecure_ssl", "0")))
                .retrieve().body(JsonNode.class), "create webhook");
        return hook.get("id").asLong();
    }

    /** Blocks force-push and deletion of the default branch, and any change to submission tags. */
    public void applyRulesets(String repo) {
        call(() -> http.post().uri("/repos/{org}/{repo}/rulesets", org, repo)
                .body(Map.of(
                        "name", "protect-default-branch",
                        "target", "branch",
                        "enforcement", "active",
                        "conditions", Map.of("ref_name", Map.of("include", List.of("~DEFAULT_BRANCH"),
                                "exclude", List.of())),
                        "rules", List.of(Map.of("type", "non_fast_forward"), Map.of("type", "deletion"))))
                .retrieve().toBodilessEntity(), "create branch ruleset");
        call(() -> http.post().uri("/repos/{org}/{repo}/rulesets", org, repo)
                .body(Map.of(
                        "name", "protect-submission-tags",
                        "target", "tag",
                        "enforcement", "active",
                        "conditions", Map.of("ref_name", Map.of("include", List.of("refs/tags/submission-*"),
                                "exclude", List.of())),
                        "rules", List.of(Map.of("type", "update"), Map.of("type", "deletion"),
                                Map.of("type", "non_fast_forward"))))
                .retrieve().toBodilessEntity(), "create tag ruleset");
    }

    public boolean userExists(String username) {
        try {
            http.get().uri("/users/{u}", username).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientResponseException e) {
            throw failure("look up user " + username, e);
        }
    }

    /** {@code permission} is "push" while developing and "pull" once the submission is frozen. */
    public void setCollaborator(String repo, String username, String permission) {
        call(() -> http.put().uri("/repos/{org}/{repo}/collaborators/{u}", org, repo, username)
                .body(Map.of("permission", permission)).retrieve().toBodilessEntity(),
                "set collaborator " + username);
    }

    /** Creates an immutable tag at {@code sha}. Returns the full ref name. */
    public String createTag(String repo, String tagName, String sha) {
        String ref = "refs/tags/" + tagName;
        call(() -> http.post().uri("/repos/{org}/{repo}/git/refs", org, repo)
                .body(Map.of("ref", ref, "sha", sha)).retrieve().toBodilessEntity(), "create tag " + tagName);
        return ref;
    }

    public void dispatchWorkflow(String repo, String workflowFile, String ref, Map<String, String> inputs) {
        call(() -> http.post().uri("/repos/{org}/{repo}/actions/workflows/{wf}/dispatches", org, repo, workflowFile)
                .body(Map.of("ref", ref, "inputs", inputs)).retrieve().toBodilessEntity(), "dispatch workflow");
    }

    public List<TreeEntry> listFiles(String repo, String commitSha) {
        JsonNode tree = call(() -> http.get().uri("/repos/{org}/{repo}/git/trees/{sha}?recursive=1", org, repo, commitSha)
                .retrieve().body(JsonNode.class), "list files");
        if (tree.path("truncated").asBoolean(false)) {
            throw new ExternalServiceException("Repository tree at %s is too large to list".formatted(commitSha));
        }
        List<TreeEntry> entries = new ArrayList<>();
        for (JsonNode node : tree.get("tree")) {
            if ("blob".equals(node.get("type").asString())) {
                entries.add(new TreeEntry(node.get("path").asString(), node.get("sha").asString(),
                        node.path("size").asLong(0)));
            }
        }
        return entries;
    }

    public byte[] readBlob(String repo, String blobSha) {
        JsonNode blob = call(() -> http.get().uri("/repos/{org}/{repo}/git/blobs/{sha}", org, repo, blobSha)
                .retrieve().body(JsonNode.class), "read blob");
        return Base64.getMimeDecoder().decode(blob.get("content").asString());
    }

    private static RepoInfo toRepoInfo(JsonNode node) {
        return new RepoInfo(node.get("id").asLong(), node.get("name").asString(), node.get("html_url").asString(),
                node.get("default_branch").asString());
    }

    private static <T> T call(java.util.function.Supplier<T> request, String action) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw failure(action, e);
        }
    }

    private static ExternalServiceException failure(String action, RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        HttpStatusCode status = e.getStatusCode();
        return new ExternalServiceException("GitHub %s failed (%s): %s".formatted(action, status,
                body.length() > 500 ? body.substring(0, 500) : body), e);
    }
}
