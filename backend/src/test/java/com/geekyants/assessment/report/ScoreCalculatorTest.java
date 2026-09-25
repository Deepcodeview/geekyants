package com.geekyants.assessment.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.CoverageStatus;
import com.geekyants.assessment.evaluation.EvaluationResult;
import com.geekyants.assessment.evaluation.EvaluationResult.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    private static ReportContent.RequirementStatus req(boolean mandatory, CoverageStatus status) {
        return new ReportContent.RequirementStatus("REQ", "t", mandatory, status, List.of(), List.of(), null);
    }

    @Test
    void mandatoryWeighsDoubleAndPartialCountsHalf() {
        // mandatory implemented 2/2, mandatory partial 1/2, optional missing 0/1 -> 3/5
        Integer score = ScoreCalculator.requirements(List.of(
                req(true, CoverageStatus.IMPLEMENTED),
                req(true, CoverageStatus.PARTIALLY_IMPLEMENTED),
                req(false, CoverageStatus.NOT_IMPLEMENTED),
                req(true, CoverageStatus.NOT_ASSESSED)));
        assertThat(score).isEqualTo(60);
    }

    @Test
    void evaluationBlendsStagesAndTests() {
        EvaluationResult result = new EvaluationResult("E", "sha", null, null,
                List.of(new EvaluationResult.Stage("build", Outcome.PASSED, null),
                        new EvaluationResult.Stage("smoke", Outcome.FAILED, null),
                        new EvaluationResult.Stage("security", Outcome.SKIPPED, null)),
                new EvaluationResult.TestSummary(10, 8, 2, 0),
                new EvaluationResult.TestSummary(10, 6, 4, 0), List.of());
        // stages 1/2, tests 14/20 -> (0.5 + 0.7) / 2
        assertThat(ScoreCalculator.automatedEvaluation(result)).isEqualTo(60);
    }

    @Test
    void overallIgnoresUnmeasuredComponents() {
        ReportContent.Scores scores = ScoreCalculator.scores(80, null, null, 40);
        // (80*35 + 40*25) / 60
        assertThat(scores.overall()).isEqualTo(63);
    }
}
