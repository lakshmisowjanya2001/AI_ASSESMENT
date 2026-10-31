package com.assessment.registration.service;

import com.assessment.registration.eligibility.EligibilityChecker;
import com.assessment.registration.model.CourseOffering;
import com.assessment.registration.model.DropResult;
import com.assessment.registration.model.EligibilityEvaluation;
import com.assessment.registration.model.EnrollmentRecord;
import com.assessment.registration.model.EnrollmentResult;
import com.assessment.registration.model.IdentifierValidator;
import com.assessment.registration.model.Learner;
import com.assessment.registration.model.LearnerRegistrationState;
import com.assessment.registration.model.LearnerStatusView;
import com.assessment.registration.model.OfferingSnapshot;
import com.assessment.registration.model.WaitlistEntry;
import com.assessment.registration.repository.LearnerReader;
import com.assessment.registration.repository.OfferingRosterStore;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe domain orchestrator implementing {@link RegistrationService} for a single
 * bounded {@link CourseOffering}.
 *
 * <h2>SOLID Alignment</h2>
 * <ul>
 *   <li><b>SRP</b>: Coordinates atomic registration/drop/promotion workflow and lock management;
 *       prerequisite evaluation lives in {@link EligibilityChecker} and storage primitives live in
 *       {@link OfferingRosterStore}.</li>
 *   <li><b>OCP</b>: Extensible via {@link RegistrationService} decorators and pluggable
 *       {@link OfferingRosterStore} / {@link LearnerReader} / {@link EligibilityChecker} implementations.</li>
 *   <li><b>ISP</b>: Depends on read-only {@link LearnerReader} rather than write-capable
 *       {@code LearnerRepository}, and exposes segregated {@link RegistrationCommandService} and
 *       {@link RegistrationQueryService} views.</li>
 *   <li><b>DIP</b>: Zero compile-time dependency on concrete {@code InMemory*} classes; wired
 *       cleanly via constructor injection or {@link CourseRegistrationFactory}.</li>
 * </ul>
 */
public final class CourseRegistrationService implements RegistrationService {

    private final CourseOffering offering;
    private final LearnerReader learnerReader;
    private final OfferingRosterStore rosterStore;
    private final EligibilityChecker eligibilityChecker;
    private final Clock clock;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);

    public CourseRegistrationService(
            CourseOffering offering,
            LearnerReader learnerReader,
            OfferingRosterStore rosterStore,
            EligibilityChecker eligibilityChecker,
            Clock clock
    ) {
        this.offering = Objects.requireNonNull(offering, "offering must not be null");
        this.learnerReader = Objects.requireNonNull(learnerReader, "learnerReader must not be null");
        this.rosterStore = Objects.requireNonNull(rosterStore, "rosterStore must not be null");
        this.eligibilityChecker = Objects.requireNonNull(eligibilityChecker, "eligibilityChecker must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");

        if (rosterStore.enrolledCount() > offering.capacity()) {
            throw new IllegalArgumentException(
                    "Initial roster enrolledCount (" + rosterStore.enrolledCount()
                            + ") exceeds offering capacity (" + offering.capacity() + ")");
        }
        if (rosterStore.enrolledCount() < offering.capacity() && rosterStore.waitlistSize() > 0) {
            throw new IllegalArgumentException(
                    "Initial roster violates queue invariant: waitlist cannot be non-empty while open seats remain");
        }
    }

    @Override
    public EnrollmentResult enroll(String rawLearnerId) {
        String learnerId = IdentifierValidator.tryNormalizeId(rawLearnerId);
        if (learnerId == null) {
            return EnrollmentResult.invalidRequest(rawLearnerId, "Learner ID must not be null or blank.");
        }

        Lock writeLock = rwLock.writeLock();
        writeLock.lock();
        try {
            if (rosterStore.isEnrolled(learnerId)) {
                return EnrollmentResult.alreadyEnrolled(learnerId);
            }
            if (rosterStore.isWaitlisted(learnerId)) {
                return EnrollmentResult.alreadyWaitlisted(learnerId);
            }

            Optional<Learner> learnerOpt = learnerReader.findById(learnerId);
            if (learnerOpt.isEmpty()) {
                return EnrollmentResult.invalidRequest(
                        learnerId, "Learner '" + learnerId + "' was not found.");
            }

            EligibilityEvaluation evaluation = eligibilityChecker.evaluate(learnerOpt.get(), offering);
            if (!evaluation.eligible()) {
                return EnrollmentResult.ineligible(learnerId, evaluation.missingPrerequisites());
            }

            Instant now = clock.instant();
            if (rosterStore.enrolledCount() < offering.capacity()) {
                rosterStore.addEnrolled(new EnrollmentRecord(learnerId, now, false));
                return EnrollmentResult.enrolled(learnerId);
            }

            int waitlistPos = rosterStore.addWaitlisted(new WaitlistEntry(learnerId, now));
            return EnrollmentResult.waitlisted(learnerId, waitlistPos);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public DropResult drop(String rawLearnerId) {
        String learnerId = IdentifierValidator.tryNormalizeId(rawLearnerId);
        if (learnerId == null) {
            return DropResult.invalidRequest(rawLearnerId, "Learner ID must not be null or blank.");
        }

        Lock writeLock = rwLock.writeLock();
        writeLock.lock();
        try {
            if (rosterStore.removeEnrolled(learnerId)) {
                return promoteNextEligibleAfterDrop(learnerId);
            }

            if (rosterStore.removeWaitlisted(learnerId)) {
                return DropResult.droppedFromWaitlist(learnerId);
            }

            if (!learnerReader.existsById(learnerId)) {
                return DropResult.invalidRequest(
                        learnerId, "Learner '" + learnerId + "' was not found.");
            }

            return DropResult.notRegistered(learnerId);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public CourseOffering getOffering() {
        return offering;
    }

    @Override
    public OfferingSnapshot getSnapshot() {
        Lock readLock = rwLock.readLock();
        readLock.lock();
        try {
            int enrolledCount = rosterStore.enrolledCount();
            return new OfferingSnapshot(
                    offering.offeringId(),
                    offering.capacity(),
                    enrolledCount,
                    offering.capacity() - enrolledCount,
                    rosterStore.enrolledLearnerIds(),
                    rosterStore.waitlistedLearnerIds());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public LearnerStatusView getLearnerStatus(String rawLearnerId) {
        String learnerId = IdentifierValidator.normalizeRequiredId(rawLearnerId, "learnerId");
        Lock readLock = rwLock.readLock();
        readLock.lock();
        try {
            if (rosterStore.isEnrolled(learnerId)) {
                return new LearnerStatusView(
                        learnerId, LearnerRegistrationState.ENROLLED, OptionalInt.empty());
            }
            OptionalInt position = rosterStore.findWaitlistPosition(learnerId);
            if (position.isPresent()) {
                return new LearnerStatusView(
                        learnerId, LearnerRegistrationState.WAITLISTED, position);
            }
            return new LearnerStatusView(
                    learnerId, LearnerRegistrationState.NOT_REGISTERED, OptionalInt.empty());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Optional<EnrollmentRecord> findEnrollmentRecord(String rawLearnerId) {
        String learnerId = IdentifierValidator.tryNormalizeId(rawLearnerId);
        if (learnerId == null) {
            return Optional.empty();
        }
        Lock readLock = rwLock.readLock();
        readLock.lock();
        try {
            return rosterStore.findEnrollmentRecord(learnerId);
        } finally {
            readLock.unlock();
        }
    }

    private DropResult promoteNextEligibleAfterDrop(String droppedLearnerId) {
        List<String> skippedIneligibleIds = null;
        Optional<String> promotedLearnerId = Optional.empty();

        while (rosterStore.enrolledCount() < offering.capacity()) {
            Optional<WaitlistEntry> nextOpt = rosterStore.pollEarliestWaitlisted();
            if (nextOpt.isEmpty()) {
                break;
            }
            String candidateId = nextOpt.get().learnerId();
            Optional<Learner> candidateLearner = learnerReader.findById(candidateId);
            if (candidateLearner.isPresent()
                    && eligibilityChecker.evaluate(candidateLearner.get(), offering).eligible()) {
                rosterStore.addEnrolled(new EnrollmentRecord(candidateId, clock.instant(), true));
                promotedLearnerId = Optional.of(candidateId);
                break;
            }

            if (skippedIneligibleIds == null) {
                skippedIneligibleIds = new ArrayList<>(2);
            }
            skippedIneligibleIds.add(candidateId);
        }

        return DropResult.droppedFromEnrolled(
                droppedLearnerId,
                promotedLearnerId,
                skippedIneligibleIds == null ? List.of() : skippedIneligibleIds);
    }
}
