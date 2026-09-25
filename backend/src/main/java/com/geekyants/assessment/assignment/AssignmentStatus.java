package com.geekyants.assessment.assignment;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of an assignment. Every state change goes through {@link #canTransitionTo}
 * so the pipeline cannot skip a step (e.g. provisioning a repository before approval).
 */
public enum AssignmentStatus {
    CREATED,
    ANALYZING_REQUIREMENTS,
    ANALYZED,
    BLUEPRINT_GENERATING,
    BLUEPRINT_DRAFT,
    BLUEPRINT_APPROVED,
    PROVISIONING,
    AWAITING_GITHUB_USERNAME,
    ASSIGNED,
    IN_PROGRESS,
    SUBMITTED,
    EVALUATING,
    ANALYZING_CODE,
    GENERATING_MCQ,
    MCQ_READY,
    MCQ_IN_PROGRESS,
    MCQ_COMPLETED,
    REPORTED,
    /** A platform step failed (GitHub, LLM, runner). Not used for candidate build failures. */
    FAILED;

    private static final Map<AssignmentStatus, Set<AssignmentStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(CREATED, EnumSet.of(ANALYZING_REQUIREMENTS)),
            Map.entry(ANALYZING_REQUIREMENTS, EnumSet.of(ANALYZED, FAILED)),
            Map.entry(ANALYZED, EnumSet.of(ANALYZING_REQUIREMENTS, BLUEPRINT_GENERATING)),
            Map.entry(BLUEPRINT_GENERATING, EnumSet.of(BLUEPRINT_DRAFT, FAILED)),
            Map.entry(BLUEPRINT_DRAFT, EnumSet.of(BLUEPRINT_GENERATING, BLUEPRINT_APPROVED)),
            Map.entry(BLUEPRINT_APPROVED, EnumSet.of(PROVISIONING)),
            Map.entry(PROVISIONING, EnumSet.of(AWAITING_GITHUB_USERNAME, ASSIGNED, FAILED)),
            Map.entry(AWAITING_GITHUB_USERNAME, EnumSet.of(ASSIGNED, FAILED)),
            Map.entry(ASSIGNED, EnumSet.of(IN_PROGRESS, SUBMITTED)),
            Map.entry(IN_PROGRESS, EnumSet.of(SUBMITTED)),
            Map.entry(SUBMITTED, EnumSet.of(EVALUATING, FAILED)),
            Map.entry(EVALUATING, EnumSet.of(ANALYZING_CODE, FAILED)),
            Map.entry(ANALYZING_CODE, EnumSet.of(GENERATING_MCQ, FAILED)),
            Map.entry(GENERATING_MCQ, EnumSet.of(MCQ_READY, FAILED)),
            Map.entry(MCQ_READY, EnumSet.of(MCQ_IN_PROGRESS)),
            Map.entry(MCQ_IN_PROGRESS, EnumSet.of(MCQ_COMPLETED)),
            Map.entry(MCQ_COMPLETED, EnumSet.of(REPORTED, FAILED)),
            Map.entry(REPORTED, EnumSet.of(REPORTED)),
            Map.entry(FAILED, EnumSet.noneOf(AssignmentStatus.class)));

    public boolean canTransitionTo(AssignmentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** States in which a background step is running. */
    public boolean isBackgroundStep() {
        return EnumSet.of(ANALYZING_REQUIREMENTS, BLUEPRINT_GENERATING, PROVISIONING, SUBMITTED, EVALUATING,
                ANALYZING_CODE, GENERATING_MCQ, MCQ_COMPLETED).contains(this);
    }

    /** States in which the candidate may still push and submit. */
    public boolean isDevelopmentOpen() {
        return this == ASSIGNED || this == IN_PROGRESS;
    }
}
