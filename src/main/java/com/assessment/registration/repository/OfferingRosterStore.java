package com.assessment.registration.repository;

import com.assessment.registration.model.EnrollmentRecord;
import com.assessment.registration.model.WaitlistEntry;

import java.util.Optional;

/**
 * Full storage boundary extending {@link OfferingRosterReader} with roster and FIFO waitlist
 * mutation operations (Interface Segregation Principle &amp; Liskov Substitution Principle).
 *
 * <p>Contract: All {@code learnerId} parameters must be non-null canonical identifiers.
 * Multi-step transitions must be externally synchronized by the owning service.
 */
public interface OfferingRosterStore extends OfferingRosterReader {

    /**
     * Adds an enrolled record to the active roster.
     *
     * @throws IllegalStateException if the learner is already enrolled or waitlisted,
     *                               or if roster capacity is already full
     */
    void addEnrolled(EnrollmentRecord record);

    /**
     * Removes {@code learnerId} from the active roster if present.
     *
     * @return {@code true} if the learner was enrolled and removed; {@code false} otherwise
     */
    boolean removeEnrolled(String learnerId);

    /**
     * Appends {@code entry} to the tail of the FIFO waitlist.
     *
     * @return the newly assigned 1-based waitlist position
     * @throws IllegalStateException if the learner is already enrolled or waitlisted
     */
    int addWaitlisted(WaitlistEntry entry);

    /**
     * Unlinks and removes {@code learnerId} from any position in the FIFO waitlist.
     *
     * @return {@code true} if the learner was waitlisted and removed; {@code false} otherwise
     */
    boolean removeWaitlisted(String learnerId);

    /**
     * Removes and returns the earliest (head) entry from the FIFO waitlist, or
     * {@link Optional#empty()} if the waitlist is empty.
     */
    Optional<WaitlistEntry> pollEarliestWaitlisted();
}
