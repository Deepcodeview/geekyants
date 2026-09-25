package com.geekyants.assessment.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.geekyants.assessment.Fixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlueprintValidatorTest {

    @Test
    void validBlueprintPasses() {
        assertThat(BlueprintValidator.validate(Fixtures.blueprint())).isEmpty();
    }

    @Test
    void rejectsDuplicateCodesAndDanglingReferences() {
        BlueprintContent bp = Fixtures.blueprint();
        List<BlueprintContent.Requirement> reqs = new ArrayList<>(bp.requirements());
        reqs.set(1, new BlueprintContent.Requirement("REQ-001", "dup", "dup", BlueprintContent.Category.DATA, true,
                List.of("x")));
        reqs.set(2, new BlueprintContent.Requirement("R3", "bad", "bad", BlueprintContent.Category.DATA, true,
                List.of("x")));
        BlueprintContent broken = new BlueprintContent(bp.projectName(), bp.businessProblem(), bp.scope(),
                bp.modules(), reqs, bp.apis(), bp.dataModel(), bp.events(), bp.nonFunctionalRequirements(),
                bp.securityRequirements(), bp.testingRequirements(), bp.evaluationCriteria(),
                bp.candidateInstructions());

        assertThat(BlueprintValidator.validate(broken)).anyMatch(e -> e.contains("Duplicate requirement code REQ-001"))
                .anyMatch(e -> e.contains("'R3'"))
                .anyMatch(e -> e.contains("event TransactionCreated references unknown requirement REQ-003"));
    }
}
