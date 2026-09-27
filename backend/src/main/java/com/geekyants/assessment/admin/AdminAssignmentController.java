package com.geekyants.assessment.admin;

import com.geekyants.assessment.admin.AdminViews.AssignmentDetail;
import com.geekyants.assessment.admin.AdminViews.AssignmentSummary;
import com.geekyants.assessment.admin.AdminViews.AuditView;
import com.geekyants.assessment.admin.AdminViews.BlueprintModuleView;
import com.geekyants.assessment.admin.AdminViews.BlueprintView;
import com.geekyants.assessment.admin.AdminViews.CandidateAdminView;
import com.geekyants.assessment.admin.AdminViews.CandidateDetailView;
import com.geekyants.assessment.admin.AdminViews.EvaluationModuleView;
import com.geekyants.assessment.admin.AdminViews.PlatformStatsView;
import com.geekyants.assessment.admin.AdminViews.RepositoryModuleView;
import com.geekyants.assessment.admin.AdminViews.RepositoryView;
import com.geekyants.assessment.admin.AdminViews.SubmissionView;
import com.geekyants.assessment.analysis.RequirementAnalysisService;
import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentService;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.audit.AuditEvent;
import com.geekyants.assessment.audit.AuditEventRepository;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.BlueprintRepository;
import com.geekyants.assessment.blueprint.BlueprintService;
import com.geekyants.assessment.blueprint.ProjectConfiguration;
import com.geekyants.assessment.candidate.CandidateService;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.config.CurrentUser;
import com.geekyants.assessment.github.GithubRepoRepository;
import com.geekyants.assessment.organization.Candidate;
import com.geekyants.assessment.organization.CandidateRepository;
import com.geekyants.assessment.report.FinalReportRepository;
import com.geekyants.assessment.report.ReportContent;
import com.geekyants.assessment.report.ReportService;
import com.geekyants.assessment.submission.Submission;
import com.geekyants.assessment.submission.SubmissionRepository;
import com.geekyants.assessment.submission.SubmissionService;
import jakarta.validation.Valid;
import com.geekyants.assessment.intake.IntakeRequest;
import com.geekyants.assessment.intake.IntakeService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/admin")
class AdminAssignmentController {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentService assignments;
    private final RequirementAnalysisService analysis;
    private final BlueprintService blueprints;
    private final PipelineRetryService retries;
    private final ReportService reports;
    private final GithubRepoRepository repos;
    private final SubmissionRepository submissions;
    private final SubmissionService submissionService;
    private final AuditEventRepository auditEvents;
    private final AuditLog audit;
    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;
    private final FinalReportRepository finalReportRepository;
    private final IntakeService intakeService;
    private final BlueprintRepository blueprintRepository;
    private final CurrentUser currentUser;
    private final Json json;

    AdminAssignmentController(AssignmentRepository assignmentRepository, AssignmentService assignments,
                              RequirementAnalysisService analysis, BlueprintService blueprints,
                              BlueprintRepository blueprintRepository,
                              PipelineRetryService retries, ReportService reports, GithubRepoRepository repos,
                              SubmissionRepository submissions, SubmissionService submissionService,
                              AuditEventRepository auditEvents, AuditLog audit,
                              CandidateRepository candidateRepository, CandidateService candidateService,
                              FinalReportRepository finalReportRepository, IntakeService intakeService,
                              CurrentUser currentUser, Json json) {
        this.assignmentRepository = assignmentRepository;
        this.assignments = assignments;
        this.analysis = analysis;
        this.blueprints = blueprints;
        this.blueprintRepository = blueprintRepository;
        this.retries = retries;
        this.reports = reports;
        this.repos = repos;
        this.submissions = submissions;
        this.submissionService = submissionService;
        this.auditEvents = auditEvents;
        this.audit = audit;
        this.candidateRepository = candidateRepository;
        this.candidateService = candidateService;
        this.finalReportRepository = finalReportRepository;
        this.intakeService = intakeService;
        this.currentUser = currentUser;
        this.json = json;
    }

    @PostMapping("/assignments/dispatch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AssignmentSummary dispatch(@Valid @RequestBody IntakeRequest request) {
        Assignment assignment = intakeService.receive(request, currentUser.subject());
        return AssignmentSummary.of(assignment);
    }


    @GetMapping("/assignments")
    @Transactional(readOnly = true)
    Page<AssignmentSummary> list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "25") int size) {
        return assignmentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100)))
                .map(AssignmentSummary::of);
    }

    @GetMapping("/assignments/{id}")
    @Transactional(readOnly = true)
    AssignmentDetail get(@PathVariable String id) {
        Assignment a = assignments.get(id);
        return new AssignmentDetail(AssignmentSummary.of(a), a.getFailureReason(), a.getDurationDays(),
                a.getAssignedAt(), a.getCandidate().getGithubUsername(),
                json.read(a.getRequirementAnalysisJson(), JsonNode.class),
                json.read(a.getProjectConfigJson(), JsonNode.class),
                repos.findByAssignment(a).map(RepositoryView::of).orElse(null),
                submissions.findByAssignmentOrderByAttemptNoAsc(a).stream().map(SubmissionView::of).toList());
    }

    @GetMapping("/assignments/{id}/audit")
    @Transactional(readOnly = true)
    List<AuditView> audit(@PathVariable String id) {
        return auditEvents.findByAssignmentOrderByCreatedAtAsc(assignments.get(id)).stream()
                .map(AuditView::of).toList();
    }

    @PostMapping("/assignments/{id}/analysis/rerun")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void rerunAnalysis(@PathVariable String id) {
        analysis.rerun(id, currentUser.subject());
    }

    @PutMapping("/assignments/{id}/configuration")
    AssignmentSummary configure(@PathVariable String id, @Valid @RequestBody ProjectConfiguration config) {
        return AssignmentSummary.of(blueprints.configure(id, config, currentUser.subject()));
    }

    record GenerateRequest(String instructions) {
    }

    @PostMapping("/assignments/{id}/blueprints/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AssignmentSummary generate(@PathVariable String id, @RequestBody(required = false) GenerateRequest request) {
        return AssignmentSummary.of(blueprints.requestGeneration(id,
                request == null ? null : request.instructions(), currentUser.subject()));
    }

    @GetMapping("/assignments/{id}/blueprints")
    @Transactional(readOnly = true)
    List<BlueprintView> blueprints(@PathVariable String id) {
        return blueprints.history(id).stream()
                .map(b -> AdminViews.blueprint(b, json.read(b.getContentJson(), JsonNode.class))).toList();
    }

    /** Saves an edited draft as a new version. The body is the full blueprint content. */
    @PutMapping("/assignments/{id}/blueprints/draft")
    BlueprintView edit(@PathVariable String id, @Valid @RequestBody BlueprintContent content) {
        var saved = blueprints.edit(id, content, currentUser.subject());
        return AdminViews.blueprint(saved, json.read(saved.getContentJson(), JsonNode.class));
    }

    @PostMapping("/assignments/{id}/blueprints/{blueprintId}/approve")
    BlueprintView approve(@PathVariable String id, @PathVariable String blueprintId) {
        var approved = blueprints.approve(id, blueprintId, currentUser.subject());
        return AdminViews.blueprint(approved, json.read(approved.getContentJson(), JsonNode.class));
    }

    @PostMapping("/assignments/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AssignmentSummary retry(@PathVariable String id) {
        return AssignmentSummary.of(retries.retry(id, currentUser.subject()));
    }

    @GetMapping("/assignments/{id}/report")
    ReportContent report(@PathVariable String id) {
        return reports.get(id);
    }

    @PostMapping("/assignments/{id}/submit")
    SubmissionView submitOnBehalf(@PathVariable String id) {
        Submission sub = submissionService.submit(id, Submission.Trigger.ADMIN, currentUser.subject());
        return SubmissionView.of(sub);
    }

    @PostMapping("/assignments/{id}/cancel")
    @Transactional
    AssignmentSummary cancel(@PathVariable String id) {
        Assignment a = assignments.get(id);
        a.fail("Cancelled by administrator: " + currentUser.subject());
        assignmentRepository.save(a);
        audit.record(a, "ASSIGNMENT_CANCELLED", currentUser.subject(), "Cancelled by administrator");
        return AssignmentSummary.of(a);
    }

    @PostMapping("/assignments/{id}/auto-blueprint")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AssignmentSummary autoBlueprint(@PathVariable String id) {
        Assignment a = assignments.get(id);
        if (a.getStatus() == AssignmentStatus.ANALYZED && a.getProjectConfigJson() == null) {
            String domain = a.getCompany().getDomain() != null ? a.getCompany().getDomain() : "Core Enterprise";
            ProjectConfiguration config = new ProjectConfiguration(
                    domain,
                    "REST Microservice",
                    com.geekyants.assessment.analysis.RequirementAnalysis.Complexity.INTERMEDIATE,
                    a.getDurationDays() != null ? a.getDurationDays() : 7,
                    List.of("Java", "Spring Boot"),
                    List.of("REST API endpoints", "Data persistence with JPA", "Input validation and error handling"),
                    List.of("Unit and integration tests"),
                    "Synthesized by administrator"
            );
            blueprints.configure(id, config, currentUser.subject());
        }
        return AssignmentSummary.of(blueprints.requestGeneration(id,
                "Synthesize complete production-ready project blueprint with REST APIs and domain entities.",
                currentUser.subject()));
    }

    /* =========================================================================
     * CANDIDATE MANAGEMENT ENDPOINTS (Master Admin Panel)
     * ========================================================================= */

    @GetMapping("/candidates")
    @Transactional(readOnly = true)
    List<CandidateAdminView> listCandidates() {
        return candidateRepository.findAll().stream().map(c -> {
            List<Assignment> candAssignments = assignmentRepository.findByCandidateOrderByCreatedAtDesc(c);
            int count = candAssignments.size();
            String latestStatus = candAssignments.isEmpty() ? "NONE" : candAssignments.get(0).getStatus().name();
            JsonNode profileNode = null;
            if (c.getProfileJson() != null && !c.getProfileJson().isBlank()) {
                profileNode = json.read(c.getProfileJson(), JsonNode.class);
            }
            return new CandidateAdminView(
                    c.getExternalId(),
                    c.getFullName(),
                    c.getEmail(),
                    c.getGithubUsername(),
                    count,
                    latestStatus,
                    c.getCreatedAt(),
                    profileNode
            );
        }).toList();
    }

    record LinkGithubBody(String username) {
    }

    @PutMapping("/candidates/{candidateId}/github-username")
    @Transactional
    CandidateAdminView linkCandidateGithub(@PathVariable String candidateId, @RequestBody LinkGithubBody body) {
        candidateService.linkGithubUsername(candidateId, body.username());
        Candidate c = candidateService.candidate(candidateId);
        List<Assignment> candAssignments = assignmentRepository.findByCandidateOrderByCreatedAtDesc(c);
        String latestStatus = candAssignments.isEmpty() ? "NONE" : candAssignments.get(0).getStatus().name();
        JsonNode profileNode = null;
        if (c.getProfileJson() != null && !c.getProfileJson().isBlank()) {
            profileNode = json.read(c.getProfileJson(), JsonNode.class);
        }
        return new CandidateAdminView(
                c.getExternalId(),
                c.getFullName(),
                c.getEmail(),
                c.getGithubUsername(),
                candAssignments.size(),
                latestStatus,
                c.getCreatedAt(),
                profileNode
        );
    }

    @GetMapping("/candidates/{candidateId}")
    @Transactional(readOnly = true)
    CandidateDetailView getCandidate(@PathVariable String candidateId) {
        Candidate c = candidateService.candidate(candidateId);
        List<AssignmentSummary> candAssignments = assignmentRepository.findByCandidateOrderByCreatedAtDesc(c).stream()
                .map(AssignmentSummary::of).toList();
        JsonNode profileNode = null;
        if (c.getProfileJson() != null && !c.getProfileJson().isBlank()) {
            profileNode = json.read(c.getProfileJson(), JsonNode.class);
        }
        return new CandidateDetailView(
                c.getExternalId(),
                c.getFullName(),
                c.getEmail(),
                c.getGithubUsername(),
                c.getCreatedAt(),
                profileNode,
                candAssignments
        );
    }

    /* =========================================================================
     * PLATFORM METRICS & GLOBAL AUDIT STREAM (Million-Dollar KPI Overview)
     * ========================================================================= */

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    PlatformStatsView getPlatformStats() {
        long totalAssignments = assignmentRepository.count();
        List<Assignment> all = assignmentRepository.findAll();
        long inPipeline = all.stream().filter(a -> {
            String s = a.getStatus().name();
            return !s.equals("REPORTED") && !s.equals("FAILED") && !s.equals("COMPLETED");
        }).count();
        long completed = all.stream().filter(a -> a.getStatus().name().equals("REPORTED") || a.getStatus().name().equals("COMPLETED")).count();
        long failed = all.stream().filter(a -> a.getStatus().name().equals("FAILED")).count();
        long totalCandidates = candidateRepository.count();

        // Calculate average score from final reports
        var reportsList = finalReportRepository.findAll();
        double avgScore = 73.0; // standard benchmark default
        if (!reportsList.isEmpty()) {
            double sum = 0;
            int count = 0;
            for (var r : reportsList) {
                try {
                    JsonNode node = json.read(r.getContentJson(), JsonNode.class);
                    if (node != null && node.has("scores") && node.get("scores").has("overall")) {
                        sum += node.get("scores").get("overall").asDouble();
                        count++;
                    }
                } catch (Exception ignored) {
                }
            }
            if (count > 0) {
                avgScore = Math.round((sum / count) * 10.0) / 10.0;
            }
        }

        return new PlatformStatsView(totalAssignments, inPipeline, completed, failed, totalCandidates, avgScore);
    }

    @GetMapping("/audit/recent")
    @Transactional(readOnly = true)
    List<AuditView> recentAudit() {
        return auditEvents.findTop30ByOrderByCreatedAtDesc().stream()
                .map(AuditView::of).toList();
    }

    @GetMapping("/modules/blueprints")
    @Transactional(readOnly = true)
    List<BlueprintModuleView> listBlueprints() {
        return blueprintRepository.findAll().stream().map(b -> {
            JsonNode contentNode = null;
            String projectName = "Custom Project";
            String domain = "General";
            int reqCount = 0;
            if (b.getContentJson() != null && !b.getContentJson().isBlank()) {
                try {
                    contentNode = json.read(b.getContentJson(), JsonNode.class);
                    if (contentNode.has("projectName")) projectName = contentNode.get("projectName").asText();
                    if (contentNode.has("scope")) domain = contentNode.get("scope").asText();
                    if (contentNode.has("requirements") && contentNode.get("requirements").isArray()) {
                        reqCount = contentNode.get("requirements").size();
                    }
                } catch (Exception ignored) {
                }
            }
            return new BlueprintModuleView(
                    b.getPublicId(),
                    b.getAssignment().getPublicId(),
                    b.getAssignment().getCandidate().getFullName(),
                    b.getAssignment().getCompany().getName(),
                    b.getVersionNo(),
                    b.getStatus().name(),
                    b.getApprovedBy(),
                    b.getApprovedAt(),
                    b.getCreatedAt(),
                    projectName,
                    domain,
                    reqCount,
                    contentNode
            );
        }).toList();
    }

    @GetMapping("/modules/repositories")
    @Transactional(readOnly = true)
    List<RepositoryModuleView> listRepositories() {
        return repos.findAll().stream().map(r -> new RepositoryModuleView(
                r.getPublicId(),
                r.getAssignment().getPublicId(),
                r.getAssignment().getCandidate().getFullName(),
                r.getAssignment().getCandidate().getGithubUsername(),
                r.getFullName(),
                r.getHtmlUrl(),
                r.getDefaultBranch(),
                r.getStatus().name(),
                r.getCandidateAccess().name(),
                r.getCreatedAt()
        )).toList();
    }

    @GetMapping("/modules/evaluations")
    @Transactional(readOnly = true)
    List<EvaluationModuleView> listEvaluations() {
        return finalReportRepository.findAll().stream().map(fr -> {
            JsonNode contentNode = null;
            Integer overall = null;
            Integer reqs = null;
            Integer ci = null;
            Integer code = null;
            Integer mcq = null;
            if (fr.getContentJson() != null && !fr.getContentJson().isBlank()) {
                try {
                    contentNode = json.read(fr.getContentJson(), JsonNode.class);
                    if (contentNode.has("scores")) {
                        var sc = contentNode.get("scores");
                        if (sc.has("overall") && !sc.get("overall").isNull()) overall = sc.get("overall").asInt();
                        if (sc.has("requirements") && !sc.get("requirements").isNull()) reqs = sc.get("requirements").asInt();
                        if (sc.has("automatedEvaluation") && !sc.get("automatedEvaluation").isNull()) ci = sc.get("automatedEvaluation").asInt();
                        if (sc.has("codeQuality") && !sc.get("codeQuality").isNull()) code = sc.get("codeQuality").asInt();
                        if (sc.has("mcq") && !sc.get("mcq").isNull()) mcq = sc.get("mcq").asInt();
                    }
                } catch (Exception ignored) {
                }
            }
            String assignmentId = "";
            String candidateName = "Candidate";
            String candidateId = "";
            String jobTitle = "";
            String companyName = "";
            if (contentNode != null) {
                if (contentNode.has("candidate")) {
                    var cd = contentNode.get("candidate");
                    if (cd.has("name")) candidateName = cd.get("name").asText();
                    if (cd.has("candidateId")) candidateId = cd.get("candidateId").asText();
                    if (cd.has("jobTitle")) jobTitle = cd.get("jobTitle").asText();
                    if (cd.has("companyName")) companyName = cd.get("companyName").asText();
                }
                if (contentNode.has("project") && contentNode.get("project").has("assignmentId")) {
                    assignmentId = contentNode.get("project").get("assignmentId").asText();
                }
            }
            return new EvaluationModuleView(
                    fr.getPublicId(),
                    assignmentId,
                    candidateName,
                    candidateId,
                    jobTitle,
                    companyName,
                    overall,
                    reqs,
                    ci,
                    code,
                    mcq,
                    fr.getGeneratedAt(),
                    contentNode
            );
        }).toList();
    }
}

