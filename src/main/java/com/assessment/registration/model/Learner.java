package com.assessment.registration.model;

import java.util.Set;

/**
 * Immutable domain record representing a learner and their completed course history (SRP).
 *
 * <p>Prerequisite evaluation against a {@link CourseOffering} is separated into
 * {@link com.assessment.registration.eligibility.EligibilityChecker} so {@code Learner}
 * has a single responsibility: modeling canonical learner state.
 *
 * @param learnerId          unique, non-blank learner identifier (normalized)
 * @param completedCourseIds immutable set of completed course IDs (normalized)
 */
public record Learner(
        String learnerId,
        Set<String> completedCourseIds
) {

    public Learner {
        learnerId = IdentifierValidator.normalizeRequiredId(learnerId, "learnerId");
        completedCourseIds = IdentifierValidator.normalizeCourseIdSet(
                completedCourseIds, "completedCourseIds");
    }

    /**
     * Convenience constructor for a learner with no prior completed courses.
     */
    public Learner(String learnerId) {
        this(learnerId, Set.of());
    }
}
