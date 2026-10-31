package com.assessment.registration.model;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Structured, immutable outcome of an {@code enroll(learnerId)} operation.
 *
 * @param status               categorical outcome of the enrollment attempt
 * @param learnerId            normalized learner ID (or raw value if invalid)
 * @param waitlistPosition     1-based waitlist position assigned when {@code status == WAITLISTED};
 *                             empty otherwise to keep duplicate requests strictly O(1)
 * @param missingPrerequisites missing prerequisite course IDs when {@code status == INELIGIBLE}
 * @param message              clear diagnostic explanation of the outcome
 */
public record EnrollmentResult(
        EnrollmentStatus status,
        String learnerId,
        OptionalInt waitlistPosition,
        Set<String> missingPrerequisites,
        String message
) {

    public EnrollmentResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(waitlistPosition, "waitlistPosition must not be null");
        missingPrerequisites = missingPrerequisites == null ? Set.of() : Set.copyOf(missingPrerequisites);
        Objects.requireNonNull(message, "message must not be null");
    }

    public static EnrollmentResult enrolled(String learnerId) {
        return new EnrollmentResult(
                EnrollmentStatus.ENROLLED,
                learnerId,
                OptionalInt.empty(),
                Set.of(),
                "Learner '" + learnerId + "' enrolled successfully.");
    }

    public static EnrollmentResult waitlisted(String learnerId, int position) {
        if (position <= 0) {
            throw new IllegalArgumentException("waitlist position must be >= 1, got: " + position);
        }
        return new EnrollmentResult(
                EnrollmentStatus.WAITLISTED,
                learnerId,
                OptionalInt.of(position),
                Set.of(),
                "Offering is full; learner '" + learnerId + "' joined waitlist at position " + position + ".");
    }

    public static EnrollmentResult alreadyEnrolled(String learnerId) {
        return new EnrollmentResult(
                EnrollmentStatus.ALREADY_ENROLLED,
                learnerId,
                OptionalInt.empty(),
                Set.of(),
                "Learner '" + learnerId + "' is already enrolled.");
    }

    public static EnrollmentResult alreadyWaitlisted(String learnerId) {
        return new EnrollmentResult(
                EnrollmentStatus.ALREADY_WAITLISTED,
                learnerId,
                OptionalInt.empty(),
                Set.of(),
                "Learner '" + learnerId + "' is already on the waitlist.");
    }

    public static EnrollmentResult ineligible(String learnerId, Set<String> missingPrerequisites) {
        return new EnrollmentResult(
                EnrollmentStatus.INELIGIBLE,
                learnerId,
                OptionalInt.empty(),
                missingPrerequisites,
                "Learner '" + learnerId + "' is ineligible; missing prerequisites: " + missingPrerequisites + ".");
    }

    public static EnrollmentResult invalidRequest(String rawLearnerId, String reason) {
        return new EnrollmentResult(
                EnrollmentStatus.INVALID_REQUEST,
                rawLearnerId,
                OptionalInt.empty(),
                Set.of(),
                reason);
    }

    /**
     * Returns {@code true} if this request newly enrolled or newly waitlisted the learner.
     */
    public boolean isAccepted() {
        return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.WAITLISTED;
    }
}
