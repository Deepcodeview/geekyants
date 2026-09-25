package com.geekyants.assessment.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Payload the evaluation runner posts back when the pipeline finishes. */
public record EvaluationResult(
        @NotBlank String evaluationId,
        @NotBlank String commitSha,
        Long runId,
        String runUrl,
        @NotNull List<@Valid Stage> stages,
        TestSummary visibleTests,
        TestSummary hiddenTests,
        List<String> failedTests) {

    public enum Outcome { PASSED, FAILED, SKIPPED, ERROR }

    public record Stage(@NotBlank String name, @NotNull Outcome outcome, String summary) {
    }

    public record TestSummary(int total, int passed, int failed, int skipped) {
    }
}
