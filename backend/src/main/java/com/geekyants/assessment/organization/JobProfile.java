package com.geekyants.assessment.organization;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** Job profile and job description as received from the recruitment system. */
public record JobProfile(
        @NotBlank String jobId,
        @NotBlank String title,
        String department,
        String seniority,
        String requiredExperience,
        List<String> requiredSkills,
        List<String> optionalSkills,
        List<String> responsibilities,
        List<String> technicalRequirements,
        List<String> functionalRequirements,
        List<String> nonFunctionalRequirements,
        List<String> mandatoryTechnologies,
        List<String> preferredTechnologies,
        List<String> domainRequirements) {
}
