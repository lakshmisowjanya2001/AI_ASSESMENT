package com.assessment.registration.model;

/**
 * Outcome categories for a drop request.
 */
public enum DropStatus {
    /** Enrolled learner dropped; opened seat triggered FIFO waitlist evaluation. */
    DROPPED_FROM_ENROLLED,

    /** Waitlisted learner removed from the queue; enrolled seats unchanged. */
    DROPPED_FROM_WAITLIST,

    /** Valid learner is neither enrolled nor waitlisted (includes duplicate/repeated drops). */
    NOT_REGISTERED,

    /** Request had a null/blank learner ID or referenced an unknown learner. */
    INVALID_REQUEST
}
