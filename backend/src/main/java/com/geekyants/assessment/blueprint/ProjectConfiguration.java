package com.geekyants.assessment.blueprint;

import com.geekyants.assessment.analysis.RequirementAnalysis.Complexity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Choices the admin makes before the blueprint is generated. */
public record ProjectConfiguration(
        @NotBlank String domain,
        @NotBlank String projectType,
        @NotNull Complexity complexity,
        @Min(1) @Max(30) int durationDays,
        @NotEmpty List<@NotBlank String> technologies,
        List<@NotBlank String> mandatoryRequirements,
        List<@NotBlank String> optionalRequirements,
        String notes) {
}
