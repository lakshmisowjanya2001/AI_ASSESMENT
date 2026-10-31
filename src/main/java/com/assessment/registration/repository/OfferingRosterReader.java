package com.assessment.registration.repository;

import com.assessment.registration.model.EnrollmentRecord;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Segregated read-only query contract for a single course offering's roster and waitlist
 * (Interface Segregation Principle).
 */
public interface OfferingRosterReader {

    /**
     * Returns {@code true} if {@code learnerId} (non-null canonical ID) is actively enrolled.
     */
    boolean isEnrolled(String learnerId);

    /**
     * Returns {@code true} if {@code learnerId} (non-null canonical ID) is currently waitlisted.
     */
    boolean isWaitlisted(String learnerId);

    /**
     * Returns the current number of enrolled learners in O(1).
     */
    int enrolledCount();

    /**
     * Returns the current number of waitlisted learners in O(1).
     */
    int waitlistSize();

    /**
     * Looks up enrollment metadata for {@code learnerId} (non-null canonical ID) in O(1).
     */
    Optional<EnrollmentRecord> findEnrollmentRecord(String learnerId);

    /**
     * Computes the 1-based FIFO waitlist index for {@code learnerId} (non-null canonical ID),
     * or {@link OptionalInt#empty()} if not waitlisted.
     */
    OptionalInt findWaitlistPosition(String learnerId);

    /**
     * Returns an immutable list of enrolled learner IDs in chronological order.
     */
    List<String> enrolledLearnerIds();

    /**
     * Returns an immutable list of waitlisted learner IDs in strict FIFO order.
     */
    List<String> waitlistedLearnerIds();
}
