package com.geekyants.assessment.intake;

import com.geekyants.assessment.organization.CandidateProfile;
import com.geekyants.assessment.organization.CompanyProfile;
import com.geekyants.assessment.organization.JobProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record IntakeRequest(
        @Valid @NotNull CompanyProfile company,
        @Valid @NotNull JobProfile job,
        @Valid @NotNull CandidateProfile candidate,
        @Min(1) @Max(30) Integer durationDays) {
}
