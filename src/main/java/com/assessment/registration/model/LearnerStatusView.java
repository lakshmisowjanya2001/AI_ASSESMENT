package com.assessment.registration.model;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Read-only view of a single learner's live registration status.
 *
 * @param learnerId        normalized learner identifier
 * @param state            current registration state ({@code ENROLLED}, {@code WAITLISTED}, {@code NOT_REGISTERED})
 * @param waitlistPosition current 1-based position in the FIFO waitlist if {@code state == WAITLISTED}
 */
public record LearnerStatusView(
        String learnerId,
        LearnerRegistrationState state,
        OptionalInt waitlistPosition
) {
    public LearnerStatusView {
        learnerId = IdentifierValidator.normalizeRequiredId(learnerId, "learnerId");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(waitlistPosition, "waitlistPosition must not be null");
    }
}
