package com.geekyants.assessment.admin;

import com.geekyants.assessment.admin.AdminViews.AssignmentDetail;
import com.geekyants.assessment.admin.AdminViews.AssignmentSummary;
import com.geekyants.assessment.admin.AdminViews.AuditView;
import com.geekyants.assessment.admin.AdminViews.BlueprintView;
import com.geekyants.assessment.admin.AdminViews.RepositoryView;
import com.geekyants.assessment.admin.AdminViews.SubmissionView;
import com.geekyants.assessment.analysis.RequirementAnalysisService;
import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentService;
import com.geekyants.assessment.audit.AuditEventRepository;
import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.BlueprintService;
import com.geekyants.assessment.blueprint.ProjectConfiguration;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.config.CurrentUser;
import com.geekyants.assessment.github.GithubRepoRepository;
import com.geekyants.assessment.report.ReportContent;
import com.geekyants.assessment.report.ReportService;
import com.geekyants.assessment.submission.SubmissionRepository;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/admin/assignments")
class AdminAssignmentController {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentService assignments;
    private final RequirementAnalysisService analysis;
    private final BlueprintService blueprints;
    private final PipelineRetryService retries;
    private final ReportService reports;
    private final GithubRepoRepository repos;
    private final SubmissionRepository submissions;
    private final AuditEventRepository auditEvents;
    private final CurrentUser currentUser;
    private final Json json;

    AdminAssignmentController(AssignmentRepository assignmentRepository, AssignmentService assignments,
                              RequirementAnalysisService analysis, BlueprintService blueprints,
                              PipelineRetryService retries, ReportService reports, GithubRepoRepository repos,
                              SubmissionRepository submissions, AuditEventRepository auditEvents,
                              CurrentUser currentUser, Json json) {
        this.assignmentRepository = assignmentRepository;
        this.assignments = assignments;
        this.analysis = analysis;
        this.blueprints = blueprints;
        this.retries = retries;
        this.reports = reports;
        this.repos = repos;
        this.submissions = submissions;
        this.auditEvents = auditEvents;
        this.currentUser = currentUser;
        this.json = json;
    }

    @GetMapping
    @Transactional(readOnly = true)
    Page<AssignmentSummary> list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "25") int size) {
        return assignmentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100)))
                .map(AssignmentSummary::of);
    }

    @GetMapping("/{id}")
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

    @GetMapping("/{id}/audit")
    @Transactional(readOnly = true)
    List<AuditView> audit(@PathVariable String id) {
        return auditEvents.findByAssignmentOrderByCreatedAtAsc(assignments.get(id)).stream()
                .map(AuditView::of).toList();
    }

    @PostMapping("/{id}/analysis/rerun")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void rerunAnalysis(@PathVariable String id) {
        analysis.rerun(id, currentUser.subject());
    }

    @PutMapping("/{id}/configuration")
    AssignmentSummary configure(@PathVariable String id, @Valid @RequestBody ProjectConfiguration config) {
        return AssignmentSummary.of(blueprints.configure(id, config, currentUser.subject()));
    }

    record GenerateRequest(String instructions) {
    }

    @PostMapping("/{id}/blueprints/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AssignmentSummary generate(@PathVariable String id, @RequestBody(required = false) GenerateRequest request) {
        return AssignmentSummary.of(blueprints.requestGeneration(id,
                request == null ? null : request.instructions(), currentUser.subject()));
    }

    @GetMapping("/{id}/blueprints")
    @Transactional(readOnly = true)
    List<BlueprintView> blueprints(@PathVariable String id) {
        return blueprints.history(id).stream()
                .map(b -> AdminViews.blueprint(b, json.read(b.getContentJson(), JsonNode.class))).toList();
    }

    /** Saves an edited draft as a new version. The body is the full blueprint content. */
    @PutMapping("/{id}/blueprints/draft")
    BlueprintView edit(@PathVariable String id, @Valid @RequestBody BlueprintContent content) {
        var saved = blueprints.edit(id, content, currentUser.subject());
        return AdminViews.blueprint(saved, json.read(saved.getContentJson(), JsonNode.class));
    }

    @PostMapping("/{id}/blueprints/{blueprintId}/approve")
    BlueprintView approve(@PathVariable String id, @PathVariable String blueprintId) {
        var approved = blueprints.approve(id, blueprintId, currentUser.subject());
        return AdminViews.blueprint(approved, json.read(approved.getContentJson(), JsonNode.class));
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AssignmentSummary retry(@PathVariable String id) {
        return AssignmentSummary.of(retries.retry(id, currentUser.subject()));
    }

    @GetMapping("/{id}/report")
    ReportContent report(@PathVariable String id) {
        return reports.get(id);
    }
}
