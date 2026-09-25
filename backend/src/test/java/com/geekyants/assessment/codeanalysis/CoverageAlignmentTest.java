package com.geekyants.assessment.codeanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.geekyants.assessment.Fixtures;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.CoverageStatus;
import org.junit.jupiter.api.Test;

class CoverageAlignmentTest {

    @Test
    void oneEntryPerBlueprintRequirementAndUnknownCodesDropped() {
        CodeAnalysisResult aligned = CodeAnalysisService.alignCoverage(Fixtures.codeAnalysis(), Fixtures.blueprint());

        assertThat(aligned.requirementCoverage()).extracting(CodeAnalysisResult.RequirementCoverage::requirementCode)
                .containsExactly("REQ-001", "REQ-002", "REQ-003", "REQ-004");
        assertThat(aligned.requirementCoverage().get(2).status()).isEqualTo(CoverageStatus.NOT_ASSESSED);
    }
}
