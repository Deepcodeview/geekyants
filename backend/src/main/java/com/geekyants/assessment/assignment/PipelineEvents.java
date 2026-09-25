package com.geekyants.assessment.assignment;

/**
 * Events that advance an assignment through the pipeline. They are published inside a transaction and
 * handled asynchronously after commit, so slow work (LLM, GitHub, CI) never holds a database transaction.
 */
public final class PipelineEvents {

    private PipelineEvents() {
    }

    public record RequirementAnalysisRequested(String assignmentId) {
    }

    public record BlueprintRequested(String assignmentId, String adminInstructions) {
    }

    public record ProvisioningRequested(String assignmentId) {
    }

    public record CandidateAccessRequested(String assignmentId) {
    }

    public record SubmissionFrozen(String assignmentId, String submissionId) {
    }

    public record EvaluationCompleted(String assignmentId, String submissionId) {
    }

    public record CodeAnalysisCompleted(String assignmentId, String submissionId) {
    }

    public record McqCompleted(String assignmentId, String assessmentId) {
    }
}
