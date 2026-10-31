package com.assessment.registration.model;

import com.assessment.registration.eligibility.EligibilityChecker;
import com.assessment.registration.eligibility.PrerequisiteEligibilityChecker;
import com.assessment.registration.repository.InMemoryLearnerRepository;
import com.assessment.registration.repository.InMemoryOfferingRosterStore;
import com.assessment.registration.repository.LearnerReader;
import com.assessment.registration.service.CourseRegistrationFactory;
import com.assessment.registration.service.CourseRegistrationService;
import com.assessment.registration.service.RegistrationQueryService;
import com.assessment.registration.service.RegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Domain Model, Eligibility & SOLID Contract Validation Tests")
class DomainModelValidationTest {

    private final EligibilityChecker eligibilityChecker = new PrerequisiteEligibilityChecker();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t\n"})
    @DisplayName("CourseOffering rejects null or blank offeringId and title")
    void courseOfferingRejectsInvalidIds(String badValue) {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering(badValue, "Algorithms", 5, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS-201", badValue, 5, Set.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, Integer.MIN_VALUE})
    @DisplayName("CourseOffering rejects non-positive capacity")
    void courseOfferingRejectsNonPositiveCapacity(int badCapacity) {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS-201", "Algorithms", badCapacity, Set.of()));
    }

    @Test
    @DisplayName("CourseOffering rejects null prerequisites, blank prerequisite IDs, and self-prerequisite")
    void courseOfferingRejectsInvalidPrerequisites() {
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS-201", 5, null));

        Set<String> setWithNull = new HashSet<>();
        setWithNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS-201", 5, setWithNull));

        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS-201", 5, Set.of("   ")));

        assertThrows(IllegalArgumentException.class,
                () -> new CourseOffering("CS-201", 5, Set.of("CS-101", "  CS-201  ")));
    }

    @Test
    @DisplayName("CourseOffering normalizes identifiers and enforces defensive immutability")
    void courseOfferingNormalizesAndProtectsPrerequisites() {
        Set<String> mutableInput = new HashSet<>(List.of(" CS-101 ", "MATH-100\t"));
        CourseOffering offering = new CourseOffering("  CS-201 ", "  Data Structures  ", 3, mutableInput);

        mutableInput.add("HACKED-999");

        assertEquals("CS-201", offering.offeringId());
        assertEquals("Data Structures", offering.title());
        assertEquals(3, offering.capacity());
        assertEquals(Set.of("CS-101", "MATH-100"), offering.requiredPrerequisites());
        assertThrows(UnsupportedOperationException.class,
                () -> offering.requiredPrerequisites().add("CS-300"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Learner rejects null or blank learnerId")
    void learnerRejectsInvalidId(String badId) {
        assertThrows(IllegalArgumentException.class, () -> new Learner(badId));
    }

    @Test
    @DisplayName("Learner enforces immutability and PrerequisiteEligibilityChecker computes sorted missing courses")
    void learnerAndPrerequisiteEligibilityEvaluation() {
        Set<String> mutableCompleted = new HashSet<>(List.of(" CS-101 ", "STAT-100"));
        Learner learner = new Learner("  alice  ", mutableCompleted);
        mutableCompleted.add("MATH-200");

        assertEquals("alice", learner.learnerId());
        assertThrows(UnsupportedOperationException.class,
                () -> learner.completedCourseIds().add("EXTRA"));

        CourseOffering openOffering = new CourseOffering("GEN-100", 5, Set.of());
        CourseOffering cs201 = new CourseOffering("CS-201", 5, Set.of("CS-101"));
        CourseOffering cs301 = new CourseOffering("CS-301", 5, Set.of("CS-101", "PHYS-101", "MATH-200"));

        assertSame(EligibilityEvaluation.pass(), eligibilityChecker.evaluate(learner, openOffering));
        assertSame(EligibilityEvaluation.pass(), eligibilityChecker.evaluate(learner, cs201));

        EligibilityEvaluation failed = eligibilityChecker.evaluate(learner, cs301);
        assertFalse(failed.eligible());
        assertEquals(List.of("MATH-200", "PHYS-101"), List.copyOf(failed.missingPrerequisites()));

        // EligibilityEvaluation invariant guards
        assertThrows(IllegalArgumentException.class,
                () -> new EligibilityEvaluation(true, Set.of("CS-101")));
        assertThrows(IllegalArgumentException.class,
                () -> EligibilityEvaluation.fail(Set.of()));
    }

    @Test
    @DisplayName("InMemoryLearnerRepository handles save, update, whitespace lookup, and null/blank queries safely")
    void learnerRepositoryOperations() {
        InMemoryLearnerRepository repo = new InMemoryLearnerRepository();
        assertFalse(repo.existsById(null));
        assertFalse(repo.existsById("   "));
        assertEquals(Optional.empty(), repo.findById(null));

        Learner v1 = new Learner("alice", Set.of("CS-101"));
        repo.save(v1);
        assertTrue(repo.existsById("  alice "));
        assertEquals(Optional.of(v1), repo.findById("alice"));

        Learner v2 = new Learner("alice", Set.of("CS-101", "CS-102"));
        repo.save(v2);
        assertEquals(Optional.of(v2), repo.findById("alice"));
    }

    @Test
    @DisplayName("InMemoryOfferingRosterStore enforces capacity, duplicate-registration, and LSP non-null guards")
    void rosterStoreGuards() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryOfferingRosterStore(0));

        InMemoryOfferingRosterStore store = new InMemoryOfferingRosterStore(1);
        EnrollmentRecord rec = new EnrollmentRecord("alice", Instant.EPOCH, false);
        store.addEnrolled(rec);

        // Duplicate registration guard
        assertThrows(IllegalStateException.class, () -> store.addEnrolled(rec));
        assertThrows(IllegalStateException.class,
                () -> store.addWaitlisted(new WaitlistEntry("alice", Instant.EPOCH)));

        // Capacity ceiling guard
        assertThrows(IllegalStateException.class,
                () -> store.addEnrolled(new EnrollmentRecord("bob", Instant.EPOCH, false)));

        // Non-null LSP precondition guards
        assertThrows(NullPointerException.class, () -> store.isEnrolled(null));
        assertThrows(NullPointerException.class, () -> store.isWaitlisted(null));
        assertThrows(NullPointerException.class, () -> store.removeEnrolled(null));
        assertThrows(NullPointerException.class, () -> store.removeWaitlisted(null));
    }

    @Test
    @DisplayName("CourseRegistrationService enforces DIP/ISP contracts and validates broken initial store invariants")
    void serviceConstructorAndIspValidation() {
        Set<String> rawPrereqs = new HashSet<>(List.of("CS-101", " CS-101 "));
        CourseOffering offering = new CourseOffering("CS-201", 1, rawPrereqs);
        assertEquals(Set.of("CS-101"), offering.requiredPrerequisites());

        InMemoryLearnerRepository repo = new InMemoryLearnerRepository();

        assertThrows(NullPointerException.class, () -> CourseRegistrationFactory.createInMemory(null, repo));
        assertThrows(NullPointerException.class, () -> CourseRegistrationFactory.createInMemory(offering, null));
        assertThrows(IllegalArgumentException.class, () -> EnrollmentResult.waitlisted("alice", 0));

        // Pre-populated store exceeding offering capacity=1
        InMemoryOfferingRosterStore overCapStore = new InMemoryOfferingRosterStore(2);
        overCapStore.addEnrolled(new EnrollmentRecord("a", Instant.EPOCH, false));
        overCapStore.addEnrolled(new EnrollmentRecord("b", Instant.EPOCH, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseRegistrationService(
                        offering, repo, overCapStore, eligibilityChecker, Clock.systemUTC()));

        // Pre-populated store with open capacity (0/1) and non-empty waitlist
        InMemoryOfferingRosterStore brokenQueueStore = new InMemoryOfferingRosterStore(1);
        brokenQueueStore.addWaitlisted(new WaitlistEntry("w1", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseRegistrationService(
                        offering, repo, brokenQueueStore, eligibilityChecker, Clock.systemUTC()));

        // ISP verification: Service works with a pure read-only LearnerReader without write methods
        LearnerReader readOnlyReader = new LearnerReader() {
            @Override
            public Optional<Learner> findById(String learnerId) {
                return "alice".equals(learnerId)
                        ? Optional.of(new Learner("alice", Set.of("CS-101")))
                        : Optional.empty();
            }

            @Override
            public boolean existsById(String learnerId) {
                return "alice".equals(learnerId);
            }
        };
        RegistrationService svc = CourseRegistrationFactory.createInMemory(offering, readOnlyReader);
        assertEquals(EnrollmentStatus.ENROLLED, svc.enroll("alice").status());

        // Segregated read-only query view
        RegistrationQueryService queryOnly = svc;
        assertEquals(1, queryOnly.getSnapshot().enrolledCount());
    }
}
