package com.assessment.registration.eligibility;

import com.assessment.registration.model.CourseOffering;
import com.assessment.registration.model.EligibilityEvaluation;
import com.assessment.registration.model.Learner;

/**
 * Abstraction for evaluating whether a {@link Learner} meets eligibility requirements
 * for a {@link CourseOffering} (SRP, OCP, DIP).
 */
@FunctionalInterface
public interface EligibilityChecker {

    /**
     * Evaluates whether {@code learner} is currently eligible to enroll in or be promoted
     * into {@code offering}.
     *
     * @param learner  non-null learner snapshot
     * @param offering non-null course offering configuration
     * @return non-null {@link EligibilityEvaluation}
     */
    EligibilityEvaluation evaluate(Learner learner, CourseOffering offering);
}
