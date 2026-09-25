package com.geekyants.assessment.mcq;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Structured output of MCQ generation. */
public record GeneratedMcqs(List<Question> questions) {

    public record Question(
            McqCategory category,
            String stem,
            @JsonPropertyDescription("Exactly four options")
            List<String> options,
            @JsonPropertyDescription("Zero-based index of the single correct option")
            int correctOptionIndex,
            String explanation,
            @JsonPropertyDescription("File#symbol the question is about, or empty for general questions")
            String sourceRef,
            @JsonPropertyDescription("Related blueprint requirement code, or empty")
            String requirementCode) {
    }
}
