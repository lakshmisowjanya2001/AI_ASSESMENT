package com.assessment.registration.service;

import com.assessment.registration.model.CourseOffering;
import com.assessment.registration.model.DropResult;
import com.assessment.registration.model.DropStatus;
import com.assessment.registration.model.EnrollmentRecord;
import com.assessment.registration.model.EnrollmentResult;
import com.assessment.registration.model.EnrollmentStatus;
import com.assessment.registration.model.Learner;
import com.assessment.registration.model.LearnerRegistrationState;
import com.assessment.registration.model.LearnerStatusView;
import com.assessment.registration.model.OfferingSnapshot;
import com.assessment.registration.repository.InMemoryLearnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CourseRegistrationService Functional & Corner-Case Tests")
class CourseRegistrationServiceTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-10-31T05:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private InMemoryLearnerRepository learnerRepository;
    private CourseOffering defaultOffering;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        learnerRepository = new InMemoryLearnerRepository(List.of(
                new Learner("l1", Set.of("CS-101", "MATH-100")),
                new Learner("l2", Set.of("CS-101", "MATH-100")),
                new Learner("l3", Set.of("CS-101", "MATH-100")),
                new Learner("l4", Set.of("CS-101", "MATH-100")),
                new Learner("l5", Set.of("CS-101", "MATH-100")),
                new Learner("partial", Set.of("CS-101")), // missing MATH-100
                new Learner("novice", Set.of())           // missing CS-101 and MATH-100
        ));
        defaultOffering = new CourseOffering("CS-201", "Algorithms", 2, Set.of("CS-101", "MATH-100"));
        service = CourseRegistrationFactory.createInMemory(defaultOffering, learnerRepository, FIXED_CLOCK);
    }

    /**
     * Asserts all core domain invariants on the offering state:
     * 1. 0 <= enrolledCount <= capacity
     * 2. availableSeats == capacity - enrolledCount
     * 3. No duplicates in enrolled or waitlist
     * 4. enrolled ∩ waitlist = ∅
     * 5. Strict work-conserving queue: enrolledCount < capacity => waitlist.isEmpty()
     */
    private static void assertInvariants(RegistrationQueryService svc) {
        OfferingSnapshot snap = svc.getSnapshot();
        assertTrue(snap.enrolledCount() >= 0, "enrolledCount must be >= 0");
        assertTrue(snap.enrolledCount() <= snap.capacity(), "enrolledCount must not exceed capacity");
        assertEquals(snap.capacity() - snap.enrolledCount(), snap.availableSeats());
        assertEquals(snap.enrolledCount(), snap.enrolledLearnerIds().size());

        Set<String> uniqueEnrolled = new HashSet<>(snap.enrolledLearnerIds());
        assertEquals(snap.enrolledLearnerIds().size(), uniqueEnrolled.size(), "Duplicate enrolled learners found");

        Set<String> uniqueWaitlisted = new HashSet<>(snap.waitlistedLearnerIds());
        assertEquals(snap.waitlistedLearnerIds().size(), uniqueWaitlisted.size(), "Duplicate waitlisted learners found");

        for (String waitlisted : uniqueWaitlisted) {
            assertFalse(uniqueEnrolled.contains(waitlisted),
                    "Learner '" + waitlisted + "' cannot be both enrolled and waitlisted");
        }

        if (snap.enrolledCount() < snap.capacity()) {
            assertTrue(snap.waitlistedLearnerIds().isEmpty(),
                    "Waitlist must be empty when open capacity remains");
        }
    }

    @Nested
    @DisplayName("1. Available Capacity Enrollment (AC #1)")
    class AvailableCapacityEnrollmentTests {

        @Test
        @DisplayName("Enrolls eligible learners up to exact capacity and records metadata")
        void enrollsUpToCapacity() {
            EnrollmentResult r1 = service.enroll("l1");
            assertEquals(EnrollmentStatus.ENROLLED, r1.status());
            assertTrue(r1.isAccepted());
            assertEquals(OptionalInt.empty(), r1.waitlistPosition());
            assertInvariants(service);

            EnrollmentResult r2 = service.enroll("l2");
            assertEquals(EnrollmentStatus.ENROLLED, r2.status());
            assertInvariants(service);

            OfferingSnapshot snap = service.getSnapshot();
            assertEquals(2, snap.enrolledCount());
            assertEquals(0, snap.availableSeats());
            assertEquals(List.of("l1", "l2"), snap.enrolledLearnerIds());
            assertEquals(List.of(), snap.waitlistedLearnerIds());

            EnrollmentRecord rec1 = service.findEnrollmentRecord("l1").orElseThrow();
            assertEquals(FIXED_TIME, rec1.enrolledAt());
            assertFalse(rec1.promotedFromWaitlist());
        }

        @Test
        @DisplayName("Enrolls learner with zero completed courses when offering has no prerequisites")
        void enrollsWhenNoPrerequisitesRequired() {
            CourseOffering openOffering = new CourseOffering("GEN-100", 1, Set.of());
            RegistrationService openService = CourseRegistrationFactory.createInMemory(openOffering, learnerRepository);

            EnrollmentResult res = openService.enroll("novice");
            assertEquals(EnrollmentStatus.ENROLLED, res.status());
            assertInvariants(openService);
        }

        @Test
        @DisplayName("Enrolls learner who completed a strict superset of required prerequisites")
        void enrollsWithSupersetOfPrerequisites() {
            learnerRepository.save(new Learner("advanced", Set.of("CS-101", "MATH-100", "CS-301", "STAT-200")));

            EnrollmentResult res = service.enroll("advanced");
            assertEquals(EnrollmentStatus.ENROLLED, res.status());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Handles single-seat capacity=1 boundary cleanly")
        void singleSeatCapacityBoundary() {
            CourseOffering oneSeat = new CourseOffering("SEM-400", 1, Set.of("CS-101", "MATH-100"));
            RegistrationService oneSeatService = CourseRegistrationFactory.createInMemory(oneSeat, learnerRepository);

            assertEquals(EnrollmentStatus.ENROLLED, oneSeatService.enroll("l1").status());
            EnrollmentResult w1 = oneSeatService.enroll("l2");
            assertEquals(EnrollmentStatus.WAITLISTED, w1.status());
            assertEquals(OptionalInt.of(1), w1.waitlistPosition());
            assertInvariants(oneSeatService);
        }
    }

    @Nested
    @DisplayName("2. Full Capacity & FIFO Waitlist (AC #2)")
    class FullCapacityAndWaitlistTests {

        @Test
        @DisplayName("Eligible learners join waitlist in strict FIFO order with 1-based positions")
        void joinsWaitlistInFifoOrder() {
            service.enroll("l1");
            service.enroll("l2");

            EnrollmentResult w1 = service.enroll("l3");
            EnrollmentResult w2 = service.enroll("l4");
            EnrollmentResult w3 = service.enroll("l5");

            assertEquals(EnrollmentStatus.WAITLISTED, w1.status());
            assertEquals(OptionalInt.of(1), w1.waitlistPosition());

            assertEquals(EnrollmentStatus.WAITLISTED, w2.status());
            assertEquals(OptionalInt.of(2), w2.waitlistPosition());

            assertEquals(EnrollmentStatus.WAITLISTED, w3.status());
            assertEquals(OptionalInt.of(3), w3.waitlistPosition());

            OfferingSnapshot snap = service.getSnapshot();
            assertEquals(List.of("l1", "l2"), snap.enrolledLearnerIds());
            assertEquals(List.of("l3", "l4", "l5"), snap.waitlistedLearnerIds());
            assertInvariants(service);

            assertEquals(
                    new LearnerStatusView("l1", LearnerRegistrationState.ENROLLED, OptionalInt.empty()),
                    service.getLearnerStatus("l1"));
            assertEquals(
                    new LearnerStatusView("l4", LearnerRegistrationState.WAITLISTED, OptionalInt.of(2)),
                    service.getLearnerStatus("l4"));
            assertEquals(
                    new LearnerStatusView("novice", LearnerRegistrationState.NOT_REGISTERED, OptionalInt.empty()),
                    service.getLearnerStatus("novice"));
        }
    }

    @Nested
    @DisplayName("3. Duplicate Enrollment Requests (AC #2 & AC #5)")
    class DuplicateEnrollmentTests {

        @Test
        @DisplayName("Duplicate enroll for enrolled learner with open seats remaining does not double-enroll")
        void duplicateEnrollWhileSeatsRemain() {
            assertEquals(EnrollmentStatus.ENROLLED, service.enroll("l1").status());
            EnrollmentResult dup = service.enroll("l1");

            assertEquals(EnrollmentStatus.ALREADY_ENROLLED, dup.status());
            assertFalse(dup.isAccepted());
            assertEquals(1, service.getSnapshot().enrolledCount());
            assertEquals(1, service.getSnapshot().availableSeats());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Duplicate enroll for enrolled learner when offering is FULL does NOT add them to waitlist")
        void duplicateEnrollForEnrolledLearnerWhenFull() {
            service.enroll("l1");
            service.enroll("l2"); // now full

            EnrollmentResult dup = service.enroll("  l1  "); // also tests whitespace normalization
            assertEquals(EnrollmentStatus.ALREADY_ENROLLED, dup.status());
            assertTrue(service.getSnapshot().waitlistedLearnerIds().isEmpty());
            assertEquals(List.of("l1", "l2"), service.getSnapshot().enrolledLearnerIds());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Duplicate enroll for waitlisted learner preserves original FIFO position without duplicating")
        void duplicateEnrollForWaitlistedLearnerPreservesOrder() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // waitlist #1
            service.enroll("l4"); // waitlist #2

            EnrollmentResult dup = service.enroll("l3");
            assertEquals(EnrollmentStatus.ALREADY_WAITLISTED, dup.status());
            assertFalse(dup.isAccepted());

            assertEquals(List.of("l3", "l4"), service.getSnapshot().waitlistedLearnerIds());
            assertEquals(OptionalInt.of(1), service.getLearnerStatus("l3").waitlistPosition());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Duplicate enroll for waitlisted learner after prerequisite revocation remains idempotent")
        void duplicateEnrollAfterWaitlistPrerequisiteRevocation() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // waitlisted while eligible

            learnerRepository.save(new Learner("l3", Set.of())); // loses eligibility

            EnrollmentResult dup = service.enroll("l3");
            assertEquals(EnrollmentStatus.ALREADY_WAITLISTED, dup.status());
            assertEquals(List.of("l3"), service.getSnapshot().waitlistedLearnerIds());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Duplicate enroll after learner was promoted from waitlist returns ALREADY_ENROLLED")
        void duplicateEnrollAfterPromotion() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3");
            service.drop("l1"); // promotes l3

            EnrollmentResult dup = service.enroll("l3");
            assertEquals(EnrollmentStatus.ALREADY_ENROLLED, dup.status());
            assertTrue(service.getSnapshot().waitlistedLearnerIds().isEmpty());
            assertInvariants(service);
        }
    }

    @Nested
    @DisplayName("4. Prerequisite Rules & Ineligible Promotion Policy (AC #1, AC #3, AC #4)")
    class PrerequisiteAndEligibilityPolicyTests {

        @Test
        @DisplayName("Ineligible learner is rejected both when seats are open and when offering is full")
        void rejectsIneligibleLearnerInAllCapacityStates() {
            EnrollmentResult res1 = service.enroll("novice");
            assertEquals(EnrollmentStatus.INELIGIBLE, res1.status());
            assertEquals(Set.of("CS-101", "MATH-100"), res1.missingPrerequisites());
            assertEquals(0, service.getSnapshot().enrolledCount());
            assertInvariants(service);

            EnrollmentResult res2 = service.enroll("partial");
            assertEquals(EnrollmentStatus.INELIGIBLE, res2.status());
            assertEquals(Set.of("MATH-100"), res2.missingPrerequisites());
            assertInvariants(service);

            service.enroll("l1");
            service.enroll("l2");
            EnrollmentResult resFull = service.enroll("partial");
            assertEquals(EnrollmentStatus.INELIGIBLE, resFull.status());
            assertTrue(service.getSnapshot().waitlistedLearnerIds().isEmpty());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Previously ineligible learner succeeds once missing prerequisite is completed")
        void enrollsAfterCompletingMissingPrerequisite() {
            assertEquals(EnrollmentStatus.INELIGIBLE, service.enroll("partial").status());

            learnerRepository.save(new Learner("partial", Set.of("CS-101", "MATH-100")));
            assertEquals(EnrollmentStatus.ENROLLED, service.enroll("partial").status());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Skip & Evict policy: skips multiple consecutive ineligible front waitlisted learners and promotes earliest eligible")
        void skipsConsecutiveIneligibleWaitlistedLearnersDuringPromotion() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // waitlist #1
            service.enroll("l4"); // waitlist #2
            service.enroll("l5"); // waitlist #3

            learnerRepository.save(new Learner("l3", Set.of("CS-101")));
            learnerRepository.save(new Learner("l4", Set.of()));

            DropResult drop = service.drop("l1");
            assertEquals(DropStatus.DROPPED_FROM_ENROLLED, drop.status());
            assertEquals(Optional.of("l5"), drop.promotedLearnerId());
            assertEquals(List.of("l3", "l4"), drop.skippedIneligibleLearnerIds());

            OfferingSnapshot snap = service.getSnapshot();
            assertEquals(List.of("l2", "l5"), snap.enrolledLearnerIds());
            assertEquals(List.of(), snap.waitlistedLearnerIds());
            assertTrue(service.findEnrollmentRecord("l5").orElseThrow().promotedFromWaitlist());
            assertEquals(LearnerRegistrationState.NOT_REGISTERED, service.getLearnerStatus("l3").state());
            assertEquals(LearnerRegistrationState.NOT_REGISTERED, service.getLearnerStatus("l4").state());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Skip & Evict policy: if ALL waitlisted learners became ineligible, none are promoted and seat opens cleanly")
        void skipsAllWaitlistedWhenNoneRemainEligible() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3");
            service.enroll("l4");

            learnerRepository.save(new Learner("l3", Set.of()));
            learnerRepository.save(new Learner("l4", Set.of("MATH-100")));

            DropResult drop = service.drop("l2");
            assertEquals(DropStatus.DROPPED_FROM_ENROLLED, drop.status());
            assertEquals(Optional.empty(), drop.promotedLearnerId());
            assertEquals(List.of("l3", "l4"), drop.skippedIneligibleLearnerIds());

            OfferingSnapshot snap = service.getSnapshot();
            assertEquals(1, snap.enrolledCount());
            assertEquals(1, snap.availableSeats());
            assertEquals(List.of("l1"), snap.enrolledLearnerIds());
            assertTrue(snap.waitlistedLearnerIds().isEmpty());
            assertInvariants(service);

            learnerRepository.save(new Learner("l3", Set.of("CS-101", "MATH-100")));
            assertEquals(EnrollmentStatus.ENROLLED, service.enroll("l3").status());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Lazy evaluation: non-front waitlisted learner who temporarily loses and restores eligibility before reaching front is promoted")
        void nonFrontWaitlistedLearnerEvaluatedOnlyWhenReached() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // waitlist #1 (remains eligible)
            service.enroll("l4"); // waitlist #2 (temporarily loses credit)

            learnerRepository.save(new Learner("l4", Set.of("CS-101")));

            DropResult drop1 = service.drop("l1");
            assertEquals(Optional.of("l3"), drop1.promotedLearnerId());
            assertEquals(List.of(), drop1.skippedIneligibleLearnerIds());
            assertEquals(List.of("l4"), service.getSnapshot().waitlistedLearnerIds());
            assertInvariants(service);

            learnerRepository.save(new Learner("l4", Set.of("CS-101", "MATH-100")));

            DropResult drop2 = service.drop("l2");
            assertEquals(Optional.of("l4"), drop2.promotedLearnerId());
            assertTrue(service.getSnapshot().waitlistedLearnerIds().isEmpty());
            assertInvariants(service);
        }
    }

    @Nested
    @DisplayName("5. Dropping, FIFO Promotion & Re-Enrollment (AC #3)")
    class DropAndFifoPromotionTests {

        @Test
        @DisplayName("Dropping enrolled learner with empty waitlist opens a seat")
        void dropEnrolledWithEmptyWaitlistOpensSeat() {
            service.enroll("l1");
            service.enroll("l2");

            DropResult drop = service.drop("l1");
            assertEquals(DropStatus.DROPPED_FROM_ENROLLED, drop.status());
            assertEquals(Optional.empty(), drop.promotedLearnerId());
            assertEquals(1, service.getSnapshot().availableSeats());
            assertEquals(List.of("l2"), service.getSnapshot().enrolledLearnerIds());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Sequential drops promote waitlisted learners in strict FIFO order, including dropping a promoted learner")
        void sequentialDropsPromoteStrictlyInFifoOrder() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // #1
            service.enroll("l4"); // #2
            service.enroll("l5"); // #3

            DropResult d1 = service.drop("l1");
            assertEquals(Optional.of("l3"), d1.promotedLearnerId());
            assertEquals(List.of("l2", "l3"), service.getSnapshot().enrolledLearnerIds());
            assertEquals(List.of("l4", "l5"), service.getSnapshot().waitlistedLearnerIds());
            assertEquals(OptionalInt.of(1), service.getLearnerStatus("l4").waitlistPosition());
            assertInvariants(service);

            DropResult d2 = service.drop("l3");
            assertEquals(Optional.of("l4"), d2.promotedLearnerId());
            assertEquals(List.of("l2", "l4"), service.getSnapshot().enrolledLearnerIds());
            assertEquals(List.of("l5"), service.getSnapshot().waitlistedLearnerIds());
            assertInvariants(service);

            DropResult d3 = service.drop("l2");
            assertEquals(Optional.of("l5"), d3.promotedLearnerId());
            assertEquals(List.of("l4", "l5"), service.getSnapshot().enrolledLearnerIds());
            assertTrue(service.getSnapshot().waitlistedLearnerIds().isEmpty());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Dropping a waitlisted learner removes them in O(1) without opening a seat or triggering promotion")
        void dropWaitlistedLearnerDoesNotTriggerPromotion() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // #1
            service.enroll("l4"); // #2 (middle)
            service.enroll("l5"); // #3

            DropResult dropMiddle = service.drop("l4");
            assertEquals(DropStatus.DROPPED_FROM_WAITLIST, dropMiddle.status());
            assertEquals(Optional.empty(), dropMiddle.promotedLearnerId());
            assertEquals(List.of("l1", "l2"), service.getSnapshot().enrolledLearnerIds());
            assertEquals(List.of("l3", "l5"), service.getSnapshot().waitlistedLearnerIds());
            assertEquals(OptionalInt.of(2), service.getLearnerStatus("l5").waitlistPosition());
            assertInvariants(service);

            assertEquals(Optional.of("l3"), service.drop("l1").promotedLearnerId());
            assertEquals(Optional.of("l5"), service.drop("l2").promotedLearnerId());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Re-enrolling after dropping places learner at tail of waitlist when full, or enrolls if open")
        void reEnrollmentAfterDrop() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // #1
            service.enroll("l4"); // #2

            assertEquals(DropStatus.DROPPED_FROM_WAITLIST, service.drop("l3").status());
            EnrollmentResult reWait = service.enroll("l3");
            assertEquals(EnrollmentStatus.WAITLISTED, reWait.status());
            assertEquals(OptionalInt.of(2), reWait.waitlistPosition());
            assertEquals(List.of("l4", "l3"), service.getSnapshot().waitlistedLearnerIds());
            assertInvariants(service);

            assertEquals(Optional.of("l4"), service.drop("l1").promotedLearnerId());
            EnrollmentResult l1ReEnroll = service.enroll("l1");
            assertEquals(EnrollmentStatus.WAITLISTED, l1ReEnroll.status());
            assertEquals(List.of("l3", "l1"), service.getSnapshot().waitlistedLearnerIds());
            assertInvariants(service);
        }
    }

    @Nested
    @DisplayName("6. Invalid Requests & Repeated Drops (AC #5 & AC #6)")
    class InvalidAndRepeatedDropTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t\n"})
        @DisplayName("Null or blank learnerId returns INVALID_REQUEST for both enroll and drop without mutating state")
        void nullOrBlankLearnerIdReturnsInvalidRequest(String badId) {
            service.enroll("l1");

            EnrollmentResult enrollRes = service.enroll(badId);
            assertEquals(EnrollmentStatus.INVALID_REQUEST, enrollRes.status());

            DropResult dropRes = service.drop(badId);
            assertEquals(DropStatus.INVALID_REQUEST, dropRes.status());

            assertEquals(List.of("l1"), service.getSnapshot().enrolledLearnerIds());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Unknown learner ID returns INVALID_REQUEST for both enroll and drop without mutating state")
        void unknownLearnerIdReturnsInvalidRequest() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3");

            assertEquals(EnrollmentStatus.INVALID_REQUEST, service.enroll("ghost-999").status());
            assertEquals(DropStatus.INVALID_REQUEST, service.drop("ghost-999").status());

            assertEquals(List.of("l1", "l2"), service.getSnapshot().enrolledLearnerIds());
            assertEquals(List.of("l3"), service.getSnapshot().waitlistedLearnerIds());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Dropping a never-registered learner when offering is full does NOT open a seat or promote waitlisted learners")
        void dropNeverRegisteredLearnerDoesNotCorruptState() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // waitlisted

            DropResult res = service.drop("l4"); // valid learner, never registered
            assertEquals(DropStatus.NOT_REGISTERED, res.status());
            assertEquals(Optional.empty(), res.promotedLearnerId());
            assertEquals(List.of("l1", "l2"), service.getSnapshot().enrolledLearnerIds());
            assertEquals(List.of("l3"), service.getSnapshot().waitlistedLearnerIds());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Repeated drops of an enrolled or waitlisted learner return NOT_REGISTERED without extra promotions")
        void repeatedDropsAreIdempotentAndSafe() {
            service.enroll("l1");
            service.enroll("l2");
            service.enroll("l3"); // waitlist #1
            service.enroll("l4"); // waitlist #2

            DropResult firstDrop = service.drop("l1");
            assertEquals(DropStatus.DROPPED_FROM_ENROLLED, firstDrop.status());
            assertEquals(Optional.of("l3"), firstDrop.promotedLearnerId());

            DropResult secondDrop = service.drop("l1");
            DropResult thirdDrop = service.drop("  l1  ");
            assertEquals(DropStatus.NOT_REGISTERED, secondDrop.status());
            assertEquals(Optional.empty(), secondDrop.promotedLearnerId());
            assertEquals(DropStatus.NOT_REGISTERED, thirdDrop.status());

            assertEquals(List.of("l2", "l3"), service.getSnapshot().enrolledLearnerIds());
            assertEquals(List.of("l4"), service.getSnapshot().waitlistedLearnerIds());
            assertInvariants(service);

            assertEquals(DropStatus.DROPPED_FROM_WAITLIST, service.drop("l4").status());
            assertEquals(DropStatus.NOT_REGISTERED, service.drop("l4").status());
            assertInvariants(service);
        }

        @Test
        @DisplayName("Query helpers handle blank inputs as documented")
        void queryHelperInputHandling() {
            assertThrows(IllegalArgumentException.class, () -> service.getLearnerStatus(null));
            assertThrows(IllegalArgumentException.class, () -> service.getLearnerStatus("   "));
            assertEquals(Optional.empty(), service.findEnrollmentRecord(null));
            assertEquals(Optional.empty(), service.findEnrollmentRecord("   "));
            assertEquals(defaultOffering, service.getOffering());
        }
    }
}
