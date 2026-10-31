package com.assessment.registration.service;

import com.assessment.registration.model.DropResult;
import com.assessment.registration.model.EnrollmentResult;

/**
 * Segregated command (mutation) boundary for single-offering registration actions
 * (Interface Segregation Principle).
 */
public interface RegistrationCommandService {

    /**
     * Enrolls {@code rawLearnerId} into an available seat, or appends them to the FIFO waitlist
     * if full and prerequisite-eligible.
     */
    EnrollmentResult enroll(String rawLearnerId);

    /**
     * Drops {@code rawLearnerId} from the enrolled roster or waitlist, promoting the earliest
     * currently eligible waitlisted learner if an enrolled seat opens.
     */
    DropResult drop(String rawLearnerId);
}
