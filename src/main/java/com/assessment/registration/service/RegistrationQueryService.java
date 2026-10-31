package com.assessment.registration.service;

import com.assessment.registration.model.CourseOffering;
import com.assessment.registration.model.EnrollmentRecord;
import com.assessment.registration.model.LearnerStatusView;
import com.assessment.registration.model.OfferingSnapshot;

import java.util.Optional;

/**
 * Segregated read-only query boundary for inspecting a course offering's registration state
 * (Interface Segregation Principle).
 */
public interface RegistrationQueryService {

    /**
     * Returns the immutable {@link CourseOffering} managed by this service.
     */
    CourseOffering getOffering();

    /**
     * Captures a linearizable, point-in-time snapshot of roster, capacity, and waitlist state.
     */
    OfferingSnapshot getSnapshot();

    /**
     * Queries a single learner's current registration state and live 1-based waitlist position.
     *
     * @throws IllegalArgumentException if {@code rawLearnerId} is null or blank
     */
    LearnerStatusView getLearnerStatus(String rawLearnerId);

    /**
     * Looks up enrollment metadata (timestamp and promotion flag) for an actively enrolled learner.
     */
    Optional<EnrollmentRecord> findEnrollmentRecord(String rawLearnerId);
}
