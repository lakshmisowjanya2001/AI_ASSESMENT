package com.assessment.registration.model;

import java.util.Set;

/**
 * Immutable definition of a single bounded course offering.
 *
 * @param offeringId            unique, non-blank offering identifier (normalized)
 * @param title                 human-readable course title (normalized)
 * @param capacity              maximum simultaneous enrolled seats; must be {@code >= 1}
 * @param requiredPrerequisites immutable set of prerequisite course IDs required for eligibility
 */
public record CourseOffering(
        String offeringId,
        String title,
        int capacity,
        Set<String> requiredPrerequisites
) {

    public CourseOffering {
        offeringId = IdentifierValidator.normalizeRequiredId(offeringId, "offeringId");
        title = IdentifierValidator.normalizeRequiredId(title, "title");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than 0, got: " + capacity);
        }
        requiredPrerequisites = IdentifierValidator.normalizeCourseIdSet(
                requiredPrerequisites, "requiredPrerequisites");
        if (requiredPrerequisites.contains(offeringId)) {
            throw new IllegalArgumentException(
                    "Course offering '" + offeringId + "' cannot list itself as a prerequisite");
        }
    }

    /**
     * Convenience constructor using {@code offeringId} as the default title.
     */
    public CourseOffering(String offeringId, int capacity, Set<String> requiredPrerequisites) {
        this(offeringId, offeringId, capacity, requiredPrerequisites);
    }
}
