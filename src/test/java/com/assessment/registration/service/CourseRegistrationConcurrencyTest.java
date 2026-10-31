package com.assessment.registration.service;

import com.assessment.registration.model.CourseOffering;
import com.assessment.registration.model.DropResult;
import com.assessment.registration.model.DropStatus;
import com.assessment.registration.model.EnrollmentResult;
import com.assessment.registration.model.EnrollmentStatus;
import com.assessment.registration.model.Learner;
import com.assessment.registration.model.OfferingSnapshot;
import com.assessment.registration.repository.InMemoryLearnerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CourseRegistrationService Concurrency & Thread-Safety Tests")
class CourseRegistrationConcurrencyTest {

    @Test
    @DisplayName("Concurrent enrollment burst: 100 distinct learners racing for 10 seats never exceed capacity or duplicate")
    void concurrentDistinctEnrollmentBurst() throws Exception {
        int capacity = 10;
        int totalLearners = 100;
        InMemoryLearnerRepository repo = new InMemoryLearnerRepository();
        for (int i = 0; i < totalLearners; i++) {
            repo.save(new Learner("learner-" + i, Set.of("CS-101")));
        }
        CourseOffering offering = new CourseOffering("CS-201", capacity, Set.of("CS-101"));
        RegistrationService service = CourseRegistrationFactory.createInMemory(offering, repo);

        CountDownLatch ready = new CountDownLatch(totalLearners);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<EnrollmentResult>> futures = new ArrayList<>(totalLearners);
            for (int i = 0; i < totalLearners; i++) {
                final String learnerId = "learner-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.enroll(learnerId);
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int enrolledResults = 0;
            int waitlistedResults = 0;
            Set<Integer> assignedPositions = new HashSet<>();

            for (Future<EnrollmentResult> f : futures) {
                EnrollmentResult res = f.get(5, TimeUnit.SECONDS);
                if (res.status() == EnrollmentStatus.ENROLLED) {
                    enrolledResults++;
                } else if (res.status() == EnrollmentStatus.WAITLISTED) {
                    waitlistedResults++;
                    assignedPositions.add(res.waitlistPosition().orElseThrow());
                }
            }

            assertEquals(capacity, enrolledResults);
            assertEquals(totalLearners - capacity, waitlistedResults);
            assertEquals(totalLearners - capacity, assignedPositions.size(),
                    "Each waitlisted learner gets a unique 1..90 position");

            OfferingSnapshot snap = service.getSnapshot();
            assertEquals(capacity, snap.enrolledCount());
            assertEquals(0, snap.availableSeats());
            assertEquals(totalLearners - capacity, snap.waitlistedLearnerIds().size());
            assertSnapshotInvariants(snap);
        }
    }

    @Test
    @DisplayName("Concurrent duplicate enroll storm for the same learner enrolls or waitlists exactly once")
    void concurrentSameLearnerDuplicateEnrollStorm() throws Exception {
        int threads = 50;
        InMemoryLearnerRepository repo = new InMemoryLearnerRepository(List.of(
                new Learner("seed", Set.of("CS-101")),
                new Learner("alice", Set.of("CS-101")),
                new Learner("bob", Set.of("CS-101"))
        ));
        CourseOffering offering = new CourseOffering("CS-201", 1, Set.of("CS-101"));
        RegistrationService service = CourseRegistrationFactory.createInMemory(offering, repo);

        List<EnrollmentResult> phase1 = runConcurrently(threads, () -> service.enroll("alice"));
        long enrolledCount = phase1.stream().filter(r -> r.status() == EnrollmentStatus.ENROLLED).count();
        long alreadyEnrolledCount = phase1.stream().filter(r -> r.status() == EnrollmentStatus.ALREADY_ENROLLED).count();

        assertEquals(1, enrolledCount);
        assertEquals(threads - 1, alreadyEnrolledCount);
        assertEquals(List.of("alice"), service.getSnapshot().enrolledLearnerIds());
        assertTrue(service.getSnapshot().waitlistedLearnerIds().isEmpty());

        List<EnrollmentResult> phase2 = runConcurrently(threads, () -> service.enroll("bob"));
        long waitlistedCount = phase2.stream().filter(r -> r.status() == EnrollmentStatus.WAITLISTED).count();
        long alreadyWaitlistedCount = phase2.stream().filter(r -> r.status() == EnrollmentStatus.ALREADY_WAITLISTED).count();

        assertEquals(1, waitlistedCount);
        assertEquals(threads - 1, alreadyWaitlistedCount);
        assertEquals(List.of("bob"), service.getSnapshot().waitlistedLearnerIds());
        assertSnapshotInvariants(service.getSnapshot());
    }

    @Test
    @DisplayName("Concurrent duplicate drop storm promotes at most ONE waitlisted learner")
    void concurrentSameLearnerDuplicateDropStorm() throws Exception {
        int threads = 40;
        InMemoryLearnerRepository repo = new InMemoryLearnerRepository(List.of(
                new Learner("e1", Set.of("CS-101")),
                new Learner("e2", Set.of("CS-101")),
                new Learner("w1", Set.of("CS-101")),
                new Learner("w2", Set.of("CS-101")),
                new Learner("w3", Set.of("CS-101"))
        ));
        CourseOffering offering = new CourseOffering("CS-201", 2, Set.of("CS-101"));
        RegistrationService service = CourseRegistrationFactory.createInMemory(offering, repo);

        service.enroll("e1");
        service.enroll("e2");
        service.enroll("w1");
        service.enroll("w2");
        service.enroll("w3");

        List<DropResult> results = runConcurrently(threads, () -> service.drop("e1"));

        List<DropResult> actualDrops = results.stream()
                .filter(r -> r.status() == DropStatus.DROPPED_FROM_ENROLLED)
                .toList();
        long repeatedDrops = results.stream()
                .filter(r -> r.status() == DropStatus.NOT_REGISTERED)
                .count();

        assertEquals(1, actualDrops.size(), "Exactly one thread may drop e1");
        assertEquals(Optional.of("w1"), actualDrops.getFirst().promotedLearnerId());
        assertEquals(threads - 1, repeatedDrops);

        OfferingSnapshot snap = service.getSnapshot();
        assertEquals(List.of("e2", "w1"), snap.enrolledLearnerIds());
        assertEquals(List.of("w2", "w3"), snap.waitlistedLearnerIds());
        assertSnapshotInvariants(snap);
    }

    @Test
    @DisplayName("Mixed contention stress test: interleaved enrolls, drops, eligibility updates, and snapshot reads preserve all invariants")
    void mixedWorkloadPreservesInvariantsUnderContention() throws Exception {
        int capacity = 5;
        int learnerPoolSize = 40;
        InMemoryLearnerRepository repo = new InMemoryLearnerRepository();
        for (int i = 0; i < learnerPoolSize; i++) {
            repo.save(new Learner("u-" + i, Set.of("CS-101")));
        }

        CourseOffering offering = new CourseOffering("CS-201", capacity, Set.of("CS-101"));
        RegistrationService service = CourseRegistrationFactory.createInMemory(offering, repo);

        int workerCount = 24;
        int operationsPerWorker = 100;
        List<OfferingSnapshot> observedSnapshots = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger counter = new AtomicInteger();

        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(workerCount)) {
            List<Future<?>> futures = new ArrayList<>(workerCount);
            for (int w = 0; w < workerCount; w++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int op = 0; op < operationsPerWorker; op++) {
                        int step = counter.incrementAndGet();
                        String id = "u-" + (step % learnerPoolSize);
                        switch (step % 5) {
                            case 0, 1 -> service.enroll(id);
                            case 2 -> service.drop(id);
                            case 3 -> {
                                Set<String> courses = (step % 2 == 0) ? Set.of("CS-101") : Set.of();
                                repo.save(new Learner(id, courses));
                            }
                            default -> observedSnapshots.add(service.getSnapshot());
                        }
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        }

        assertFalse(observedSnapshots.isEmpty());
        for (OfferingSnapshot snap : observedSnapshots) {
            assertSnapshotInvariants(snap);
        }
        assertSnapshotInvariants(service.getSnapshot());
    }

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return task.call();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<T> results = new ArrayList<>(threads);
            for (Future<T> f : futures) {
                results.add(f.get(5, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private static void assertSnapshotInvariants(OfferingSnapshot snap) {
        assertTrue(snap.enrolledCount() >= 0 && snap.enrolledCount() <= snap.capacity());
        assertEquals(snap.capacity() - snap.enrolledCount(), snap.availableSeats());

        Set<String> enrolledSet = new HashSet<>(snap.enrolledLearnerIds());
        Set<String> waitlistSet = new HashSet<>(snap.waitlistedLearnerIds());
        assertEquals(snap.enrolledLearnerIds().size(), enrolledSet.size());
        assertEquals(snap.waitlistedLearnerIds().size(), waitlistSet.size());

        for (String w : waitlistSet) {
            assertFalse(enrolledSet.contains(w));
        }
        if (snap.enrolledCount() < snap.capacity()) {
            assertTrue(snap.waitlistedLearnerIds().isEmpty());
        }
    }
}
