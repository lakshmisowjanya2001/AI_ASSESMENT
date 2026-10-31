package com.assessment.registration.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable roster entry representing an actively enrolled learner.
 *
 * @param learnerId            canonical learner identifier
 * @param enrolledAt           timestamp when the learner occupied the seat
 * @param promotedFromWaitlist {@code true} if enrolled via FIFO waitlist promotion, {@code false} if direct
 */
public record EnrollmentRecord(
        String learnerId,
        Instant enrolledAt,
        boolean promotedFromWaitlist
) {
    public EnrollmentRecord {
        learnerId = IdentifierValidator.normalizeRequiredId(learnerId, "learnerId");
        Objects.requireNonNull(enrolledAt, "enrolledAt must not be null");
    }
}
