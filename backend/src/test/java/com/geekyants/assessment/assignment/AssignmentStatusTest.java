package com.geekyants.assessment.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssignmentStatusTest {

    @Test
    void cannotProvisionBeforeApproval() {
        assertThat(AssignmentStatus.BLUEPRINT_DRAFT.canTransitionTo(AssignmentStatus.PROVISIONING)).isFalse();
        assertThat(AssignmentStatus.BLUEPRINT_APPROVED.canTransitionTo(AssignmentStatus.PROVISIONING)).isTrue();
    }

    @Test
    void cannotSubmitTwice() {
        assertThat(AssignmentStatus.SUBMITTED.isDevelopmentOpen()).isFalse();
        assertThat(AssignmentStatus.SUBMITTED.canTransitionTo(AssignmentStatus.SUBMITTED)).isFalse();
    }

    @Test
    void everyStateHasTransitionRules() {
        for (AssignmentStatus s : AssignmentStatus.values()) {
            // Throws NullPointerException if a state was added without rules.
            s.canTransitionTo(AssignmentStatus.FAILED);
        }
    }
}
