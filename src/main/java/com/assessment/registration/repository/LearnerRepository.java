package com.assessment.registration.repository;

import com.assessment.registration.model.Learner;

/**
 * Full repository boundary extending {@link LearnerReader} with write capability
 * (Interface Segregation Principle).
 */
public interface LearnerRepository extends LearnerReader {

    /**
     * Inserts or replaces a learner's academic record atomically.
     *
     * @param learner non-null immutable learner record
     */
    void save(Learner learner);
}
