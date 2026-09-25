package com.geekyants.assessment.analysis;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Structured output of the Requirement Intelligence step. */
public record RequirementAnalysis(
        @JsonPropertyDescription("Two or three sentences on the role and how well the candidate matches it")
        String summary,
        @JsonPropertyDescription("Business domain the project should be set in, e.g. Banking")
        String domain,
        List<SkillAssessment> skills,
        @JsonPropertyDescription("Skills the job needs where the candidate is below the required level")
        List<String> skillGaps,
        @JsonPropertyDescription("Technologies the project should use, mandatory ones first")
        List<String> recommendedTechnologies,
        Complexity recommendedComplexity,
        @JsonPropertyDescription("Capabilities the project must exercise, e.g. 'event publishing with Kafka'")
        List<String> requiredCapabilities,
        List<ProjectIdea> projectIdeas) {

    public enum Level { NONE, BEGINNER, INTERMEDIATE, STRONG, EXPERT }

    public enum Complexity { BASIC, INTERMEDIATE, ADVANCED }

    public record SkillAssessment(
            String skill,
            @JsonPropertyDescription("True if the job description requires this skill")
            boolean requiredByJob,
            Level requiredLevel,
            Level candidateLevel,
            @JsonPropertyDescription("Where in the candidate profile the level comes from; 'not mentioned' if absent")
            String evidence) {
    }

    public record ProjectIdea(String title, String rationale) {
    }
}
