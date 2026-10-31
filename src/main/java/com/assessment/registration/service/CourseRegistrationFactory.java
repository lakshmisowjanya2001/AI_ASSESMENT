package com.assessment.registration.service;

import com.assessment.registration.eligibility.PrerequisiteEligibilityChecker;
import com.assessment.registration.model.CourseOffering;
import com.assessment.registration.repository.InMemoryOfferingRosterStore;
import com.assessment.registration.repository.LearnerReader;

import java.time.Clock;
import java.util.Objects;

/**
 * Composition-root factory for assembling default in-memory {@link RegistrationService} instances.
 *
 * <p>Isolates concrete infrastructure wiring ({@link InMemoryOfferingRosterStore},
 * {@link PrerequisiteEligibilityChecker}, {@link Clock#systemUTC()}) outside
 * {@link CourseRegistrationService}, preserving strict Dependency Inversion (DIP).
 */
public final class CourseRegistrationFactory {

    private CourseRegistrationFactory() {
    }

    public static CourseRegistrationService createInMemory(
            CourseOffering offering,
            LearnerReader learnerReader
    ) {
        return createInMemory(offering, learnerReader, Clock.systemUTC());
    }

    public static CourseRegistrationService createInMemory(
            CourseOffering offering,
            LearnerReader learnerReader,
            Clock clock
    ) {
        Objects.requireNonNull(offering, "offering must not be null");
        return new CourseRegistrationService(
                offering,
                learnerReader,
                new InMemoryOfferingRosterStore(offering.capacity()),
                new PrerequisiteEligibilityChecker(),
                clock);
    }
}
