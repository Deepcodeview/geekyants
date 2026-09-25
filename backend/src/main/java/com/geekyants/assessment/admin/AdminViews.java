package com.geekyants.assessment.admin;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.audit.AuditEvent;
import com.geekyants.assessment.blueprint.Blueprint;
import com.geekyants.assessment.github.GithubRepo;
import com.geekyants.assessment.submission.Submission;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Response shapes for the admin API. */
final class AdminViews {

    private AdminViews() {
    }

    record AssignmentSummary(String assignmentId, String status, String candidateId, String candidateName,
                             String jobTitle, String companyName, Instant createdAt, Instant dueAt) {

        static AssignmentSummary of(Assignment a) {
            return new AssignmentSummary(a.getPublicId(), a.getStatus().name(), a.getCandidate().getExternalId(),
                    a.getCandidate().getFullName(), a.getJob().getTitle(), a.getCompany().getName(),
                    a.getCreatedAt(), a.getDueAt());
        }
    }

    record RepositoryView(String repositoryId, String fullName, String url, String status, String candidateAccess) {

        static RepositoryView of(GithubRepo r) {
            return new RepositoryView(r.getPublicId(), r.getFullName(), r.getHtmlUrl(), r.getStatus().name(),
                    r.getCandidateAccess().name());
        }
    }

    record SubmissionView(String submissionId, int attempt, String commitSha, String tagRef, String trigger,
                          Instant submittedAt) {

        static SubmissionView of(Submission s) {
            return new SubmissionView(s.getPublicId(), s.getAttemptNo(), s.getCommitSha(), s.getTagRef(),
                    s.getTrigger().name(), s.getSubmittedAt());
        }
    }

    record BlueprintView(String blueprintId, int version, String status, String approvedBy, Instant approvedAt,
                         Instant createdAt, JsonNode content) {
    }

    static BlueprintView blueprint(Blueprint b, JsonNode content) {
        return new BlueprintView(b.getPublicId(), b.getVersionNo(), b.getStatus().name(), b.getApprovedBy(),
                b.getApprovedAt(), b.getCreatedAt(), content);
    }

    record AuditView(String type, String actor, String detail, Instant at) {

        static AuditView of(AuditEvent e) {
            return new AuditView(e.getEventType(), e.getActor(), e.getDetail(), e.getCreatedAt());
        }
    }

    record AssignmentDetail(
            AssignmentSummary summary,
            String failureReason,
            Integer durationDays,
            Instant assignedAt,
            String githubUsername,
            JsonNode requirementAnalysis,
            JsonNode projectConfiguration,
            RepositoryView repository,
            java.util.List<SubmissionView> submissions) {
    }
}
