package com.geekyants.assessment.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.geekyants.assessment.Fixtures;
import com.geekyants.assessment.analysis.RequirementAnalysis;
import com.geekyants.assessment.github.GitHubClient;
import com.geekyants.assessment.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    LlmClient llm;

    @MockitoBean
    GitHubClient github;

    @BeforeEach
    void setUp() {
        when(llm.generate(any(), eq(RequirementAnalysis.class))).thenReturn(Fixtures.analysis());
        when(github.userExists(any())).thenReturn(true);
    }

    @Test
    void adminEndpointsAndModuleTelemetry() throws Exception {
        // 1. Candidate cannot access admin endpoints
        mvc.perform(get("/api/v1/admin/stats").with(candidate("CAND-TEST")))
                .andExpect(status().isForbidden());

        // 2. Admin retrieves platform stats
        mvc.perform(get("/api/v1/admin/stats").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssignments").isNumber())
                .andExpect(jsonPath("$.totalCandidates").isNumber());

        // 3. Admin dispatches new assessment intake directly
        String dispatchPayload = """
                {
                  "company": {
                    "companyId": "ICICI-TECH",
                    "name": "ICICI Bank",
                    "domain": "Banking"
                  },
                  "job": {
                    "jobId": "JOB-ICICI-1",
                    "title": "Senior Backend Architect",
                    "requiredSkills": ["Java", "Spring Boot", "Kafka"]
                  },
                  "candidate": {
                    "candidateId": "CAND-ICICI-99",
                    "fullName": "Rohan Sharma",
                    "email": "rohan.sharma@example.com",
                    "skills": ["Java", "Spring Boot", "Docker"]
                  },
                  "durationDays": 7
                }
                """;

        JsonNode created = json(mvc.perform(post("/api/v1/admin/assignments/dispatch").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.assignmentId").exists())
                .andExpect(jsonPath("$.candidateName").value("Rohan Sharma")));

        String assignmentId = created.get("assignmentId").asText();

        // 4. Admin lists assignments and finds the newly dispatched pipeline
        mvc.perform(get("/api/v1/admin/assignments").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        // 5. Admin retrieves assignment detail
        mvc.perform(get("/api/v1/admin/assignments/{id}", assignmentId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.summary.candidateName").value("Rohan Sharma"));

        // 6. Admin lists candidate talent pool
        mvc.perform(get("/api/v1/admin/candidates").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 7. Admin retrieves candidate detail
        mvc.perform(get("/api/v1/admin/candidates/{candidateId}", "CAND-ICICI-99").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value("CAND-ICICI-99"))
                .andExpect(jsonPath("$.fullName").value("Rohan Sharma"))
                .andExpect(jsonPath("$.assignments").isArray());

        // 8. Admin links candidate GitHub username
        mvc.perform(put("/api/v1/admin/candidates/{candidateId}/github-username", "CAND-ICICI-99")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"rohan-sharma-dev\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").value("rohan-sharma-dev"));

        // 9. Admin queries system telemetry / audit stream
        mvc.perform(get("/api/v1/admin/audit/recent").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 10. Admin queries modular endpoints: blueprints, repositories, evaluations
        mvc.perform(get("/api/v1/admin/modules/blueprints").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/v1/admin/modules/repositories").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/v1/admin/modules/evaluations").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 11. Admin can cancel an assignment
        mvc.perform(post("/api/v1/admin/assignments/{id}/cancel", assignmentId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin@assessment.internal")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor candidate(String candidateId) {
        return jwt().jwt(j -> j.subject("user-" + candidateId).claim("candidate_id", candidateId))
                .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"));
    }
}
