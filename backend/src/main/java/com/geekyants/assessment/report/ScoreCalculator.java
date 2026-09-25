package com.geekyants.assessment.report;

import com.geekyants.assessment.codeanalysis.CodeAnalysisResult;
import com.geekyants.assessment.evaluation.EvaluationResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic scoring, so the same evidence always yields the same numbers. */
public final class ScoreCalculator {

    static final Map<String, Integer> WEIGHTS = Map.of(
            "requirements", 35, "automatedEvaluation", 25, "codeQuality", 15, "mcq", 25);

    private ScoreCalculator() {
    }

    /** Mandatory requirements count double; partial counts half. NOT_ASSESSED is left out. */
    static Integer requirements(List<ReportContent.RequirementStatus> requirements) {
        double earned = 0;
        double possible = 0;
        for (ReportContent.RequirementStatus r : requirements) {
            if (r.status() == CodeAnalysisResult.CoverageStatus.NOT_ASSESSED) {
                continue;
            }
            double weight = r.mandatory() ? 2 : 1;
            possible += weight;
            earned += switch (r.status()) {
                case IMPLEMENTED -> weight;
                case PARTIALLY_IMPLEMENTED -> weight / 2;
                default -> 0;
            };
        }
        return possible == 0 ? null : (int) Math.round(100 * earned / possible);
    }

    /** Half from stages passed, half from tests passed (visible and hidden together). */
    static Integer automatedEvaluation(EvaluationResult result) {
        if (result == null) {
            return null;
        }
        List<EvaluationResult.Stage> counted = result.stages().stream()
                .filter(s -> s.outcome() != EvaluationResult.Outcome.SKIPPED).toList();
        double stages = counted.isEmpty() ? 0 : (double) counted.stream()
                .filter(s -> s.outcome() == EvaluationResult.Outcome.PASSED).count() / counted.size();
        int total = 0;
        int passed = 0;
        for (EvaluationResult.TestSummary t : new EvaluationResult.TestSummary[]{result.visibleTests(), result.hiddenTests()}) {
            if (t != null) {
                total += t.total() - t.skipped();
                passed += t.passed();
            }
        }
        double tests = total == 0 ? 0 : (double) passed / total;
        return (int) Math.round(100 * (stages + tests) / 2);
    }

    static Integer codeQuality(List<CodeAnalysisResult.AreaAssessment> areas) {
        if (areas == null || areas.isEmpty()) {
            return null;
        }
        double avg = areas.stream().mapToInt(a -> Math.max(0, Math.min(10, a.score()))).average().orElse(0);
        return (int) Math.round(avg * 10);
    }

    static Integer mcq(int correct, int total) {
        return total == 0 ? null : (int) Math.round(100.0 * correct / total);
    }

    /** Weighted over the components that were measured. */
    static Integer overall(Map<String, Integer> components) {
        double sum = 0;
        int weights = 0;
        for (Map.Entry<String, Integer> c : components.entrySet()) {
            if (c.getValue() != null) {
                sum += c.getValue() * WEIGHTS.get(c.getKey());
                weights += WEIGHTS.get(c.getKey());
            }
        }
        return weights == 0 ? null : (int) Math.round(sum / weights);
    }

    static ReportContent.Scores scores(Integer requirements, Integer evaluation, Integer quality, Integer mcq) {
        Map<String, Integer> components = new LinkedHashMap<>();
        components.put("requirements", requirements);
        components.put("automatedEvaluation", evaluation);
        components.put("codeQuality", quality);
        components.put("mcq", mcq);
        return new ReportContent.Scores(requirements, evaluation, quality, mcq, overall(components), WEIGHTS);
    }
}
