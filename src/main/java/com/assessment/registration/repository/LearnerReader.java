package com.assessment.registration.repository;

import com.assessment.registration.model.Learner;

import java.util.Optional;

/**
 * Read-only lookup boundary for learner academic records (Interface Segregation Principle).
 *
 * <p>Services that only verify learner existence and eligibility depend on {@code LearnerReader}
 * rather than {@link LearnerRepository}, preventing unintended write coupling.
 */
public interface LearnerReader {

    /**
     * Finds a learner by identifier (after trimming surrounding whitespace).
     *
     * @return the matching {@link Learner}, or {@link Optional#empty()} if null, blank, or unknown
     */
    Optional<Learner> findById(String learnerId);

    /**
     * Returns {@code true} if a learner with the normalized identifier exists.
     */
    boolean existsById(String learnerId);
}
