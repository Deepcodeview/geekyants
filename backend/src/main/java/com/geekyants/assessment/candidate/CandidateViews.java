package com.geekyants.assessment.candidate;

import java.time.Instant;
import java.util.List;

/** What the candidate may see. Internal analysis, scores, prompts and answer keys are never included. */
final class CandidateViews {

    private CandidateViews() {
    }

    record AssignmentItem(String assignmentId, String jobTitle, String companyName, String status, Instant dueAt) {
    }

    record Dashboard(
            String assignmentId,
            String status,
            String jobTitle,
            String companyName,
            Project project,
            Repository repository,
            List<CiRunView> ciRuns,
            List<PushView> recentPushes,
            SubmissionView submission,
            AssessmentInfo assessment) {
    }

    record Project(String name, String domain, List<String> technologies, int durationDays, Instant assignedAt,
                   Instant dueAt, String businessProblem, String instructions, List<RequirementView> requirements) {
    }

    record RequirementView(String code, String title, String description, boolean mandatory,
                           List<String> acceptanceCriteria) {
    }

    record Repository(String fullName, String url, String cloneUrl, String access, String githubUsername) {
    }

    record CiRunView(String commitSha, String workflow, String status, String conclusion, String url,
                     Instant updatedAt) {
    }

    record PushView(String commitSha, String message, int commits, Instant at) {
    }

    record SubmissionView(String submissionId, String commitSha, Instant submittedAt, String trigger) {
    }

    record AssessmentInfo(String status, int questionCount, int timeLimitMinutes, Instant deadlineAt) {
    }

    record QuestionView(String code, int position, String category, String stem, List<String> options,
                        Integer selectedOption) {
    }

    record AssessmentAttempt(String assessmentId, String status, Instant deadlineAt, List<QuestionView> questions) {
    }
}
