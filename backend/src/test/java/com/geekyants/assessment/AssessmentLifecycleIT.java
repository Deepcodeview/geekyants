package com.geekyants.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.geekyants.assessment.analysis.RequirementAnalysis;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult;
import com.geekyants.assessment.common.HmacSignatures;
import com.geekyants.assessment.github.GitHubClient;
import com.geekyants.assessment.github.GitHubClient.RepoInfo;
import com.geekyants.assessment.github.GitHubClient.TreeEntry;
import com.geekyants.assessment.llm.LlmClient;
import com.geekyants.assessment.mcq.GeneratedMcqs;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives one assignment from intake to final report with GitHub and the LLM mocked.
 * Verifies the state machine, ownership checks, signatures and the submission freeze order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssessmentLifecycleIT {

    private static final String SHA = "a81f92c7a81f92c7a81f92c7a81f92c7a81f92c7";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    AssignmentRepository assignments;

    @MockitoBean
    LlmClient llm;

    @MockitoBean
    GitHubClient github;

    @Test
    void fullLifecycle() throws Exception {
        stubLlm();
        stubGithub();

        // 1. Recruitment system sends JD + candidate + company.
        String intake = """
                {"company": {"companyId": "ABC-BANK", "name": "ABC Bank", "domain": "Banking"},
                 "job": {"jobId": "JOB-1", "title": "Java Backend Developer", "seniority": "Senior",
                         "requiredSkills": ["Java", "Spring Boot", "Kafka"]},
                 "candidate": {"candidateId": "CAND-7821", "fullName": "Test Candidate",
                               "skills": ["Java", "Spring Boot"]}}
                """;
        JsonNode created = json(mvc.perform(post("/api/v1/intake/assignments").with(integration())
                        .contentType(MediaType.APPLICATION_JSON).content(intake))
                .andExpect(status().isAccepted()));
        String id = created.get("assignmentId").asString();
        awaitStatus(id, AssignmentStatus.ANALYZED);

        // Duplicate intake for the same job and candidate is refused.
        mvc.perform(post("/api/v1/intake/assignments").with(integration())
                .contentType(MediaType.APPLICATION_JSON).content(intake)).andExpect(status().isConflict());

        // 2. Admin configures, generates and approves the blueprint.
        mvc.perform(put("/api/v1/admin/assignments/{id}/configuration", id).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Fixtures.config())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/assignments/{id}/blueprints/generate", id).with(admin()))
                .andExpect(status().isAccepted());
        awaitStatus(id, AssignmentStatus.BLUEPRINT_DRAFT);

        JsonNode blueprints = json(mvc.perform(get("/api/v1/admin/assignments/{id}/blueprints", id).with(admin()))
                .andExpect(status().isOk()));
        String blueprintId = blueprints.get(0).get("blueprintId").asString();

        // Candidates cannot see the draft or reach admin endpoints.
        mvc.perform(get("/api/v1/admin/assignments/{id}", id).with(candidate("CAND-7821")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/candidate/assignments/{id}", id).with(candidate("CAND-7821")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.project").doesNotExist());

        mvc.perform(post("/api/v1/admin/assignments/{id}/blueprints/{bp}/approve", id, blueprintId).with(admin()))
                .andExpect(status().isOk());

        // 3. Provisioning waits for the candidate's GitHub account, then invites it.
        awaitStatus(id, AssignmentStatus.AWAITING_GITHUB_USERNAME);
        mvc.perform(put("/api/v1/candidate/github-username").with(candidate("CAND-7821"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"octocat\"}"))
                .andExpect(status().isNoContent());
        awaitStatus(id, AssignmentStatus.ASSIGNED);

        // Another candidate cannot see this assignment.
        mvc.perform(get("/api/v1/candidate/assignments/{id}", id).with(candidate("CAND-OTHER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/candidate/assignments/{id}", id).with(candidate("CAND-7821")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DEVELOPMENT"))
                .andExpect(jsonPath("$.repository.url").value("https://github.com/assessment-platform/repo"))
                .andExpect(jsonPath("$.project.requirements.length()").value(4));

        // 4. Candidate pushes; unsigned webhooks are rejected.
        String repoName = assignments.findByPublicId(id).map(a -> "assessment-" + id.toLowerCase() + "-cand-7821")
                .orElseThrow();
        byte[] push = """
                {"ref": "refs/heads/main", "before": "0000", "after": "%s", "forced": false,
                 "pusher": {"name": "octocat"}, "commits": [{}], "head_commit": {"message": "Implement accounts"},
                 "repository": {"name": "%s", "owner": {"login": "assessment-platform"}}}
                """.formatted(SHA, repoName).getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/api/v1/webhooks/github").header("X-GitHub-Event", "push")
                        .header("X-GitHub-Delivery", "d-1").header("X-Hub-Signature-256", "sha256=forged")
                        .contentType(MediaType.APPLICATION_JSON).content(push))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/webhooks/github").header("X-GitHub-Event", "push")
                        .header("X-GitHub-Delivery", "d-1")
                        .header("X-Hub-Signature-256", HmacSignatures.sign("webhook-secret", push))
                        .contentType(MediaType.APPLICATION_JSON).content(push))
                .andExpect(status().isAccepted());
        assertThat(assignments.findByPublicId(id).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.IN_PROGRESS);

        // 5. Submit freezes the exact commit: revoke write first, then read head, then tag.
        JsonNode submission = json(mvc.perform(post("/api/v1/candidate/assignments/{id}/submit", id)
                .with(candidate("CAND-7821"))).andExpect(status().isOk()));
        assertThat(submission.get("commitSha").asString()).isEqualTo(SHA);
        InOrder order = inOrder(github);
        order.verify(github).setCollaborator(repoName, "octocat", "pull");
        order.verify(github).branchHead(repoName, "main");
        order.verify(github).createTag(eq(repoName), anyString(), eq(SHA));
        mvc.perform(post("/api/v1/candidate/assignments/{id}/submit", id).with(candidate("CAND-7821")))
                .andExpect(status().isConflict());

        awaitStatus(id, AssignmentStatus.EVALUATING);
        String evaluationId = audit(id, "EVALUATION_DISPATCHED");

        // 6. Evaluation runner reports back. Results for another commit are refused.
        String wrongCommit = evaluationPayload(evaluationId, "ffff");
        mvc.perform(post("/api/v1/callbacks/evaluations/{e}", evaluationId)
                        .header("X-Signature-256", HmacSignatures.sign("callback-secret", wrongCommit.getBytes()))
                        .contentType(MediaType.APPLICATION_JSON).content(wrongCommit))
                .andExpect(status().isBadRequest());
        String result = evaluationPayload(evaluationId, SHA);
        mvc.perform(post("/api/v1/callbacks/evaluations/{e}", evaluationId)
                        .header("X-Signature-256", HmacSignatures.sign("callback-secret", result.getBytes()))
                        .contentType(MediaType.APPLICATION_JSON).content(result))
                .andExpect(status().isAccepted());

        // 7. Code analysis and MCQ generation run; candidate takes the assessment.
        awaitStatus(id, AssignmentStatus.MCQ_READY);
        mvc.perform(get("/api/v1/candidate/assignments/{id}/assessment", id).with(candidate("CAND-7821")))
                .andExpect(jsonPath("$.questions.length()").value(0));
        JsonNode attempt = json(mvc.perform(post("/api/v1/candidate/assignments/{id}/assessment/start", id)
                .with(candidate("CAND-7821"))).andExpect(status().isOk()));
        assertThat(attempt.get("questions")).hasSize(12);
        assertThat(attempt.toString()).doesNotContain("correctOption", "explanation");
        for (JsonNode q : attempt.get("questions")) {
            mvc.perform(put("/api/v1/candidate/assignments/{id}/assessment/answers/{q}", id, q.get("code").asString())
                    .with(candidate("CAND-7821")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"selectedOption\": 0}")).andExpect(status().isNoContent());
        }
        mvc.perform(post("/api/v1/candidate/assignments/{id}/assessment/finish", id).with(candidate("CAND-7821")))
                .andExpect(status().isOk());

        // 8. Final report ties everything together.
        awaitStatus(id, AssignmentStatus.REPORTED);
        JsonNode report = json(mvc.perform(get("/api/v1/admin/assignments/{id}/report", id).with(admin()))
                .andExpect(status().isOk()));
        assertThat(report.at("/repository/commitSha").asString()).isEqualTo(SHA);
        assertThat(report.at("/automatedEvaluation/evaluationId").asString()).isEqualTo(evaluationId);
        assertThat(report.get("requirements")).hasSize(4);
        assertThat(report.at("/requirements/0/status").asString()).isEqualTo("IMPLEMENTED");
        assertThat(report.at("/requirements/2/status").asString()).isEqualTo("NOT_ASSESSED");
        assertThat(report.at("/mcq/total").asInt()).isEqualTo(12);
        assertThat(report.at("/scores/overall").isNumber()).isTrue();
    }

    private void stubLlm() {
        when(llm.generate(any(), eq(RequirementAnalysis.class))).thenReturn(Fixtures.analysis());
        when(llm.generate(any(), eq(BlueprintContent.class))).thenReturn(Fixtures.blueprint());
        when(llm.generate(any(), eq(CodeAnalysisResult.class))).thenReturn(Fixtures.codeAnalysis());
        when(llm.generate(any(), eq(GeneratedMcqs.class))).thenReturn(Fixtures.mcqs(12));
    }

    private void stubGithub() {
        when(github.organization()).thenReturn("assessment-platform");
        when(github.findRepository(anyString())).thenReturn(Optional.empty());
        when(github.createPrivateRepository(anyString(), anyString()))
                .thenReturn(new RepoInfo(42L, "repo", "https://github.com/assessment-platform/repo", "main"));
        when(github.createWebhook(anyString(), anyString(), anyString())).thenReturn(7L);
        when(github.userExists("octocat")).thenReturn(true);
        when(github.branchHead(anyString(), eq("main"))).thenReturn(SHA);
        when(github.createTag(anyString(), anyString(), eq(SHA)))
                .thenAnswer(inv -> "refs/tags/" + inv.getArgument(1));
        when(github.listFiles(anyString(), eq(SHA))).thenReturn(List.of(
                new TreeEntry("src/main/java/App.java", "blob1", 20)));
        when(github.readBlob(anyString(), eq("blob1"))).thenReturn("class App {}".getBytes(StandardCharsets.UTF_8));
    }

    private String evaluationPayload(String evaluationId, String sha) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "evaluationId", evaluationId, "commitSha", sha, "runId", 99, "runUrl", "https://runs/99",
                "stages", List.of(Map.of("name", "build", "outcome", "PASSED"),
                        Map.of("name", "smoke_tests", "outcome", "FAILED", "summary", "health 503")),
                "visibleTests", Map.of("total", 10, "passed", 9, "failed", 1, "skipped", 0),
                "hiddenTests", Map.of("total", 5, "passed", 3, "failed", 2, "skipped", 0),
                "failedTests", List.of("DuplicateTransactionIT")));
    }

    private String audit(String id, String type) throws Exception {
        JsonNode events = json(mvc.perform(get("/api/v1/admin/assignments/{id}/audit", id).with(admin())));
        for (JsonNode e : events) {
            if (type.equals(e.get("type").asString())) {
                return e.get("detail").asString();
            }
        }
        throw new AssertionError("No audit event " + type);
    }

    private void awaitStatus(String id, AssignmentStatus expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        AssignmentStatus current = null;
        while (Instant.now().isBefore(deadline)) {
            var a = assignments.findByPublicId(id).orElseThrow();
            current = a.getStatus();
            if (current == expected) {
                return;
            }
            if (current == AssignmentStatus.FAILED) {
                throw new AssertionError("Assignment failed: " + a.getFailureReason());
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Expected " + expected + " but was " + current);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin@example.com")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor integration() {
        return jwt().jwt(j -> j.subject("recruitment-system")).authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION"));
    }

    private static RequestPostProcessor candidate(String candidateId) {
        return jwt().jwt(j -> j.subject("user-" + candidateId).claim("candidate_id", candidateId))
                .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"));
    }
}
