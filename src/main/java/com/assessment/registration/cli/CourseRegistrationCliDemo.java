package com.assessment.registration.cli;

import com.assessment.registration.model.CourseOffering;
import com.assessment.registration.model.DropResult;
import com.assessment.registration.model.EnrollmentResult;
import com.assessment.registration.model.Learner;
import com.assessment.registration.model.OfferingSnapshot;
import com.assessment.registration.repository.InMemoryLearnerRepository;
import com.assessment.registration.repository.LearnerRepository;
import com.assessment.registration.service.CourseRegistrationFactory;
import com.assessment.registration.service.RegistrationService;

import java.util.List;
import java.util.Set;

/**
 * Composition root and minimal command-line prototype demonstrating the bounded
 * single-offering enrollment, drop, and FIFO waitlist-promotion workflow.
 */
public final class CourseRegistrationCliDemo {

    private CourseRegistrationCliDemo() {
    }

    public static void main(String[] args) {
        CourseOffering offering = new CourseOffering(
                "CS-201",
                "Data Structures & Algorithms",
                2,
                Set.of("CS-101"));

        LearnerRepository learnerRepo = new InMemoryLearnerRepository(List.of(
                new Learner("alice", Set.of("CS-101")),
                new Learner("bob", Set.of("CS-101")),
                new Learner("charlie", Set.of("CS-101")),
                new Learner("diana", Set.of("CS-101")),
                new Learner("eve", Set.of()) // missing CS-101
        ));

        RegistrationService service = CourseRegistrationFactory.createInMemory(offering, learnerRepo);

        System.out.println("=== Course Registration CLI Prototype (" + offering.offeringId()
                + ", capacity=" + offering.capacity()
                + ", prereqs=" + offering.requiredPrerequisites() + ") ===");

        logEnroll(service.enroll("alice"));
        logEnroll(service.enroll("bob"));
        logEnroll(service.enroll("alice"));   // duplicate enrolled request
        logEnroll(service.enroll("eve"));     // ineligible learner
        logEnroll(service.enroll("charlie")); // joins waitlist #1
        logEnroll(service.enroll("diana"));   // joins waitlist #2
        logEnroll(service.enroll("charlie")); // duplicate waitlisted request
        printSnapshot(service.getSnapshot());

        System.out.println("\n--- Revoking charlie's CS-101 credit before a seat opens ---");
        learnerRepo.save(new Learner("charlie", Set.of()));

        logDrop(service.drop("alice"));        // skips ineligible charlie, promotes diana
        logDrop(service.drop("alice"));        // repeated drop -> NOT_REGISTERED
        logDrop(service.drop("unknown-user")); // invalid drop -> INVALID_REQUEST
        printSnapshot(service.getSnapshot());
    }

    private static void logEnroll(EnrollmentResult result) {
        System.out.printf("[ENROLL] %-18s -> %s%n", result.status(), result.message());
    }

    private static void logDrop(DropResult result) {
        System.out.printf("[DROP]   %-22s -> %s (skipped=%s)%n",
                result.status(), result.message(), result.skippedIneligibleLearnerIds());
    }

    private static void printSnapshot(OfferingSnapshot snapshot) {
        System.out.printf("[STATE]  capacity=%d, enrolled=%d, available=%d, roster=%s, waitlist=%s%n",
                snapshot.capacity(),
                snapshot.enrolledCount(),
                snapshot.availableSeats(),
                snapshot.enrolledLearnerIds(),
                snapshot.waitlistedLearnerIds());
    }
}
