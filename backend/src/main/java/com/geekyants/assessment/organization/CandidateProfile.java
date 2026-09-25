package com.geekyants.assessment.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** Candidate data as received from the recruitment system. */
public record CandidateProfile(
        @NotBlank String candidateId,
        @NotBlank String fullName,
        @Email String email,
        @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$",
                message = "must be a valid GitHub username") String githubUsername,
        String resumeText,
        Integer yearsOfExperience,
        List<String> skills,
        List<String> technologies,
        List<PreviousProject> previousProjects,
        List<String> certifications) {

    public record PreviousProject(String name, String description, List<String> technologies) {
    }
}
