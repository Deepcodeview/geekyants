package com.geekyants.assessment.organization;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** Company data as received from the recruitment system. */
public record CompanyProfile(
        @NotBlank String companyId,
        @NotBlank String name,
        String industry,
        String domain,
        List<String> engineeringStandards,
        List<String> preferredTechnologies,
        List<String> securityStandards,
        List<String> codingStandards,
        List<String> testingStandards,
        List<String> deploymentStandards) {
}
