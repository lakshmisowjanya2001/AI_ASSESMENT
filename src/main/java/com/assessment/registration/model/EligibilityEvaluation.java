package com.assessment.registration.model;

import java.util.Set;

/**
 * Immutable value object representing the result of checking a learner's eligibility
 * for a course offering.
 *
 * @param eligible             {@code true} if the learner satisfies all prerequisite rules
 * @param missingPrerequisites sorted, unmodifiable set of missing prerequisite course IDs
 *                             (empty when {@code eligible == true})
 */
public record EligibilityEvaluation(
        boolean eligible,
        Set<String> missingPrerequisites
) {
    private static final EligibilityEvaluation ELIGIBLE_INSTANCE =
            new EligibilityEvaluation(true, Set.of());

    public EligibilityEvaluation {
        missingPrerequisites = missingPrerequisites == null
                ? Set.of()
                : Set.copyOf(missingPrerequisites);
        if (eligible && !missingPrerequisites.isEmpty()) {
            throw new IllegalArgumentException(
                    "Eligible evaluation cannot contain missing prerequisites: " + missingPrerequisites);
        }
    }

    /**
     * Returns a shared, zero-allocation singleton for eligible evaluations.
     */
    public static EligibilityEvaluation pass() {
        return ELIGIBLE_INSTANCE;
    }

    /**
     * Creates an ineligible evaluation carrying the missing prerequisite course IDs.
     */
    public static EligibilityEvaluation fail(Set<String> missingPrerequisites) {
        if (missingPrerequisites == null || missingPrerequisites.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ineligible evaluation must specify at least one missing prerequisite");
        }
        return new EligibilityEvaluation(false, missingPrerequisites);
    }
}
