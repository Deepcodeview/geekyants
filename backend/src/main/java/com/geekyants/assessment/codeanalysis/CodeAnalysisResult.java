package com.geekyants.assessment.codeanalysis;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Structured output of the code analysis step. */
public record CodeAnalysisResult(
        String overallSummary,
        @JsonPropertyDescription("One entry per blueprint requirement, in requirement order")
        List<RequirementCoverage> requirementCoverage,
        List<AreaAssessment> areas,
        List<Finding> findings,
        @JsonPropertyDescription("Specific design or implementation choices the candidate made that they should be able to explain")
        List<ImplementationDecision> implementationDecisions) {

    public enum CoverageStatus { IMPLEMENTED, PARTIALLY_IMPLEMENTED, NOT_IMPLEMENTED, NOT_ASSESSED }

    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

    public record RequirementCoverage(
            String requirementCode,
            CoverageStatus status,
            @JsonPropertyDescription("File paths and symbols implementing it, e.g. src/main/java/.../TransactionService.java#processTransaction")
            List<String> implementationRefs,
            @JsonPropertyDescription("Test classes or methods that exercise it")
            List<String> testRefs,
            String notes) {
    }

    public record AreaAssessment(
            @JsonPropertyDescription("e.g. Architecture, REST APIs, Persistence, Messaging, Security, Testing, Error handling, Code quality")
            String area,
            @JsonPropertyDescription("0 to 10")
            int score,
            List<String> strengths,
            List<String> issues) {
    }

    public record Finding(
            Severity severity,
            String area,
            String file,
            String symbol,
            String description,
            String recommendation) {
    }

    public record ImplementationDecision(
            String file,
            String symbol,
            String description,
            @JsonPropertyDescription("Short verbatim excerpt of the relevant code, at most 25 lines")
            String excerpt,
            String requirementCode) {
    }
}
