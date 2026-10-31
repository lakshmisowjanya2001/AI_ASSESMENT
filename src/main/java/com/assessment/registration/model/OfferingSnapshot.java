package com.assessment.registration.model;

import java.util.List;
import java.util.Objects;

/**
 * Point-in-time, deeply immutable snapshot of a course offering's roster and waitlist.
 *
 * @param offeringId           offering identifier
 * @param capacity             configured seat capacity
 * @param enrolledCount        number of currently occupied seats
 * @param availableSeats       remaining open seats ({@code capacity - enrolledCount})
 * @param enrolledLearnerIds   enrolled learner IDs in chronological enrollment/promotion order
 * @param waitlistedLearnerIds waitlisted learner IDs in strict FIFO queue order
 */
public record OfferingSnapshot(
        String offeringId,
        int capacity,
        int enrolledCount,
        int availableSeats,
        List<String> enrolledLearnerIds,
        List<String> waitlistedLearnerIds
) {
    public OfferingSnapshot {
        Objects.requireNonNull(offeringId, "offeringId must not be null");
        enrolledLearnerIds = List.copyOf(enrolledLearnerIds);
        waitlistedLearnerIds = List.copyOf(waitlistedLearnerIds);
    }
}
