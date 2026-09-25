package com.geekyants.assessment.blueprint;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** The project blueprint. Produced by the LLM, editable by the admin, frozen on approval. */
public record BlueprintContent(
        @NotBlank String projectName,
        @NotBlank String businessProblem,
        @NotBlank String scope,
        @NotNull List<@Valid Module> modules,
        @JsonPropertyDescription("Numbered REQ-001, REQ-002, ... in order. Each must be independently verifiable.")
        @NotEmpty List<@Valid Requirement> requirements,
        @NotNull List<@Valid ApiEndpoint> apis,
        @NotNull List<@Valid DataEntity> dataModel,
        @JsonPropertyDescription("Messaging events; empty if the stack has no message broker")
        @NotNull List<@Valid MessagingEvent> events,
        @NotNull List<String> nonFunctionalRequirements,
        @NotNull List<String> securityRequirements,
        @NotNull List<String> testingRequirements,
        @JsonPropertyDescription("How reviewers will judge the submission, e.g. correctness, test quality")
        @NotNull List<String> evaluationCriteria,
        @JsonPropertyDescription("Instructions shown to the candidate in the README, written in second person")
        @NotBlank String candidateInstructions) {

    public enum Category { FUNCTIONAL, DATA, MESSAGING, SECURITY, TESTING, NON_FUNCTIONAL, DEPLOYMENT }

    public record Module(@NotBlank String name, @NotBlank String description) {
    }

    public record Requirement(
            @NotBlank String code,
            @NotBlank String title,
            @NotBlank String description,
            @NotNull Category category,
            boolean mandatory,
            @NotEmpty List<String> acceptanceCriteria) {
    }

    public record ApiEndpoint(
            @NotBlank String method,
            @NotBlank String path,
            @NotBlank String description,
            List<String> requirementCodes) {
    }

    public record DataEntity(@NotBlank String name, String description, List<String> fields) {
    }

    public record MessagingEvent(
            @NotBlank String name,
            @NotBlank String topic,
            String producer,
            String consumer,
            String payload,
            List<String> requirementCodes) {
    }
}
