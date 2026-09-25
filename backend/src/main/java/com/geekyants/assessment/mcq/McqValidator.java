package com.geekyants.assessment.mcq;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class McqValidator {

    public static final int OPTIONS = 4;

    private McqValidator() {
    }

    public static List<String> validate(GeneratedMcqs mcqs, int min, int max) {
        List<String> errors = new ArrayList<>();
        List<GeneratedMcqs.Question> questions = mcqs.questions() == null ? List.of() : mcqs.questions();
        if (questions.size() < min || questions.size() > max) {
            errors.add("Expected %d-%d questions, got %d".formatted(min, max, questions.size()));
        }
        Set<McqCategory> categories = new HashSet<>();
        int codeSpecific = 0;
        for (int i = 0; i < questions.size(); i++) {
            GeneratedMcqs.Question q = questions.get(i);
            String label = "Question " + (i + 1);
            if (q.category() == null) {
                errors.add(label + " has no category");
            } else {
                categories.add(q.category());
            }
            if (q.stem() == null || q.stem().isBlank()) {
                errors.add(label + " has no stem");
            }
            if (q.options() == null || q.options().size() != OPTIONS) {
                errors.add(label + " must have exactly " + OPTIONS + " options");
                continue;
            }
            Set<String> distinct = new HashSet<>();
            for (String option : q.options()) {
                if (option == null || option.isBlank() || !distinct.add(option.trim().toLowerCase(Locale.ROOT))) {
                    errors.add(label + " has blank or duplicate options");
                    break;
                }
            }
            if (q.correctOptionIndex() < 0 || q.correctOptionIndex() >= OPTIONS) {
                errors.add(label + " has an out-of-range correct option");
            }
            if (q.sourceRef() != null && !q.sourceRef().isBlank()) {
                codeSpecific++;
            }
        }
        if (!questions.isEmpty() && categories.size() < 4) {
            errors.add("Questions cover only %d categories; need at least 4".formatted(categories.size()));
        }
        if (codeSpecific * 2 < questions.size()) {
            errors.add("At least half of the questions must reference the candidate's code (sourceRef)");
        }
        return errors;
    }
}
