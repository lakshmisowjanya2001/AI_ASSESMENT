package com.assessment.registration.eligibility;

import com.assessment.registration.model.CourseOffering;
import com.assessment.registration.model.EligibilityEvaluation;
import com.assessment.registration.model.Learner;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stateless implementation of {@link EligibilityChecker} enforcing the stated prerequisite rule:
 * a learner must have completed <em>all</em> courses in {@link CourseOffering#requiredPrerequisites()}.
 *
 * <p>Performance: Allocation-free O(|P|) check on the eligible path via {@link EligibilityEvaluation#pass()},
 * allocating a sorted missing-course set only when at least one prerequisite is unsatisfied.
 */
public final class PrerequisiteEligibilityChecker implements EligibilityChecker {

    @Override
    public EligibilityEvaluation evaluate(Learner learner, CourseOffering offering) {
        Objects.requireNonNull(learner, "learner must not be null");
        Objects.requireNonNull(offering, "offering must not be null");

        Set<String> required = offering.requiredPrerequisites();
        if (required.isEmpty() || learner.completedCourseIds().containsAll(required)) {
            return EligibilityEvaluation.pass();
        }

        TreeSet<String> missing = new TreeSet<>();
        for (String prereq : required) {
            if (!learner.completedCourseIds().contains(prereq)) {
                missing.add(prereq);
            }
        }
        return EligibilityEvaluation.fail(Collections.unmodifiableSet(missing));
    }
}
