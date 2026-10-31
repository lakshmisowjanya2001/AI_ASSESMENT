package com.assessment.registration.model;

/**
 * Outcome categories for an enrollment request.
 */
public enum EnrollmentStatus {
    /** Learner met prerequisites and occupied an available seat. */
    ENROLLED,

    /** Offering was at full capacity; eligible learner joined the FIFO waitlist. */
    WAITLISTED,

    /** Idempotent duplicate request: learner is already enrolled in this offering. */
    ALREADY_ENROLLED,

    /** Idempotent duplicate request: learner is already on the FIFO waitlist. */
    ALREADY_WAITLISTED,

    /** Learner does not satisfy the offering's required prerequisites. */
    INELIGIBLE,

    /** Request had a null/blank learner ID or referenced an unknown learner. */
    INVALID_REQUEST
}
