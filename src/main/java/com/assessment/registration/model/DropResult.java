package com.assessment.registration.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured, immutable outcome of a {@code drop(learnerId)} operation.
 *
 * @param status                      categorical outcome of the drop attempt
 * @param droppedLearnerId            normalized learner ID (or raw value if invalid)
 * @param promotedLearnerId           earliest eligible waitlisted learner promoted into the freed seat, if any
 * @param skippedIneligibleLearnerIds FIFO-ordered list of waitlisted learners evicted during promotion
 *                                    because they no longer satisfied prerequisites
 * @param message                     clear diagnostic explanation of the outcome
 */
public record DropResult(
        DropStatus status,
        String droppedLearnerId,
        Optional<String> promotedLearnerId,
        List<String> skippedIneligibleLearnerIds,
        String message
) {

    public DropResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(promotedLearnerId, "promotedLearnerId must not be null");
        skippedIneligibleLearnerIds = skippedIneligibleLearnerIds == null
                ? List.of()
                : List.copyOf(skippedIneligibleLearnerIds);
        Objects.requireNonNull(message, "message must not be null");
    }

    public static DropResult droppedFromEnrolled(
            String droppedLearnerId,
            Optional<String> promotedLearnerId,
            List<String> skippedIneligibleLearnerIds
    ) {
        String base = "Learner '" + droppedLearnerId + "' dropped from enrolled roster.";
        String promotionDetail = promotedLearnerId
                .map(id -> " Promoted waitlisted learner '" + id + "'.")
                .orElse(" Seat is now open.");
        return new DropResult(
                DropStatus.DROPPED_FROM_ENROLLED,
                droppedLearnerId,
                promotedLearnerId,
                skippedIneligibleLearnerIds,
                base + promotionDetail);
    }

    public static DropResult droppedFromWaitlist(String droppedLearnerId) {
        return new DropResult(
                DropStatus.DROPPED_FROM_WAITLIST,
                droppedLearnerId,
                Optional.empty(),
                List.of(),
                "Learner '" + droppedLearnerId + "' removed from waitlist.");
    }

    public static DropResult notRegistered(String learnerId) {
        return new DropResult(
                DropStatus.NOT_REGISTERED,
                learnerId,
                Optional.empty(),
                List.of(),
                "Learner '" + learnerId + "' is not currently enrolled or waitlisted.");
    }

    public static DropResult invalidRequest(String rawLearnerId, String reason) {
        return new DropResult(
                DropStatus.INVALID_REQUEST,
                rawLearnerId,
                Optional.empty(),
                List.of(),
                reason);
    }
}
