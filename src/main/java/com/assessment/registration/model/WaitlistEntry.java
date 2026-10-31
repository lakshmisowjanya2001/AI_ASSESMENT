package com.assessment.registration.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable waitlist entry representing a learner waiting in FIFO order.
 *
 * @param learnerId canonical learner identifier
 * @param joinedAt  timestamp when the learner joined the waitlist
 */
public record WaitlistEntry(
        String learnerId,
        Instant joinedAt
) {
    public WaitlistEntry {
        learnerId = IdentifierValidator.normalizeRequiredId(learnerId, "learnerId");
        Objects.requireNonNull(joinedAt, "joinedAt must not be null");
    }
}
