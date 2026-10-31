# AI Prompt and Use Record

This document records how AI assistance was used during the engineering of the bounded Course Waitlist feature, how each suggestion was independently reviewed (accepted, edited, or rejected), the files/decisions informed, and concrete verification evidence.

---

## Entry 1 — Problem Decomposition, Policy Selection & Low-Level Design (LLD)

### Prompt Used
> "Analyse the probelm statement document, Write an optimized java solution, including all the corder cases, junits. follow LLD and use java 21"

### AI Response Summary
- Extracted the bounded scope from `Course-Registration-AI-Assisted-Assessment.pdf`: single-offering enrollment, dropping, and FIFO waitlist promotion backed by an in-memory store and CLI prototype, explicitly excluding course catalogs, frontends, external DBs, and configurable policy engines.
- Proposed separating immutable domain records (`CourseOffering`, `Learner`, `EnrollmentResult`, `DropResult`, `OfferingSnapshot`), repository interfaces (`LearnerRepository`, `OfferingRosterStore`), and a lock-guarded `CourseRegistrationService`.
- Evaluated two candidate waitlist promotion policies when a waitlisted learner loses prerequisite eligibility before reaching the front of the queue:
  1. **Retain-in-Queue (Bypass & Retain)**
  2. **Skip & Evict**

### Engineering Review Decision: **Accepted with Critical Edits & Explicit Rejections**

| Suggestion / Alternative | Disposition | Engineering Rationale & Files Informed |
|---|---|---|
| Configurable `PromotionPolicy` Strategy interface supporting both Skip and Retain | **Rejected** | Directly violates Scope Boundary: *"State the prerequisite and waitlist assumptions; do not build a configurable policy engine."* Implemented one concrete, documented rule directly in `CourseRegistrationService.java`. |
| Dual-collection waitlist (`ArrayDeque<String>` + `HashSet<String>`) | **Rejected** | Dropping a waitlisted learner from the middle of an `ArrayDeque` degrades to $O(W)$ linear scan and risks state drift between two structures. Replaced with a single Java 21 `SequencedMap<String, WaitlistEntry>` (`LinkedHashMap`) in `InMemoryOfferingRosterStore.java`, achieving $O(1)$ tail insert, $O(1)$ `pollFirstEntry()`, and $O(1)$ arbitrary node removal. |
| **Skip & Evict** policy over **Retain-in-Queue** for ineligible waitlisted learners | **Accepted** | Retain-in-Queue breaks the work-conserving invariant (`enrolledCount < capacity => waitlist.isEmpty()`) and forces $O(W)$ repeated scans across retained ineligible heads on every subsequent drop. Skip & Evict guarantees strict $O(1)$ amortized promotion and consistent invariants (`CourseRegistrationService.java`, `DESIGN_SPEC.md`). |
| Checking `LearnerRepository` eligibility *before* acquiring the offering write lock | **Edited** | Moved `learnerRepository.findById(learnerId)` *inside* `rwLock.writeLock()` in `CourseRegistrationService.enroll()` to prevent a Time-Of-Check-to-Time-Of-Use (TOCTOU) race if a learner's prerequisite credit is revoked concurrently mid-enrollment. |
| Computing live 1-based waitlist index on duplicate `ALREADY_WAITLISTED` responses | **Edited** | Omitted queue scan on `ALREADY_WAITLISTED` in `EnrollmentResult.java` so duplicate-request storms remain strictly $O(1)$; exposed `getLearnerStatus(learnerId)` for explicit live position queries. |
| Base-case invariant validation on injected `OfferingRosterStore` | **Edited / Added** | Added constructor validation in `CourseRegistrationService.java` rejecting pre-populated stores where `enrolledCount > capacity` or `enrolledCount < capacity && waitlistSize > 0`, ensuring inductive invariant safety. |

---

## Entry 2 — Corner-Case Matrix & Concurrency Test Verification

### Prompt / Activity Summary
Generated JUnit 5 functional, boundary, and multi-threaded contention test suites (`DomainModelValidationTest.java`, `CourseRegistrationServiceTest.java`, `CourseRegistrationConcurrencyTest.java`) covering:
- Available capacity, `capacity = 1` boundary, zero vs. multiple prerequisites, superset prerequisites.
- Full capacity FIFO ordering, whitespace-padded duplicate IDs (`" alice "` vs `"alice"`).
- Duplicate `enroll` while seats remain, duplicate `enroll` for an enrolled learner when the course is full (verifying they are **not** added to the waitlist), duplicate `enroll` on waitlist (preserving FIFO position), and duplicate `enroll` after promotion.
- Dropping a **waitlisted** learner from the middle of the queue without opening an enrolled seat or triggering promotion.
- Post-waitlist prerequisite revocation (single, consecutive front, all waitlisted, and lazy evaluation when a non-front learner temporarily loses and restores credit).
- Invalid (`null`, blank, unknown learner) and repeated drops, plus concurrent duplicate `enroll`/`drop` storms.

### Engineering Review Decision: **Edited After Independent Test Execution Caught a Concurrency Test Bug**
- **Issue Discovered During Verification**: In the initial draft of `CourseRegistrationConcurrencyTest.java`, the barrier helper paired `CountDownLatch ready = new CountDownLatch(totalLearners)` (`N = 100`, `50`, `40`) with `Executors.newFixedThreadPool(32)`. When executed against the JUnit Jupiter engine, those 3 tests timed out after 5 seconds (`AssertionFailedError` at `ready.await(5, SECONDS)`) because 32 platform worker threads counted down `ready` and blocked on `start.await()`, starving the remaining `N - 32` queued tasks!
- **Fix Applied**: Replaced `Executors.newFixedThreadPool(32)` with Java 21 `Executors.newVirtualThreadPerTaskExecutor()` in `CourseRegistrationConcurrencyTest.java` so all `N` concurrent callers mount simultaneously at the barrier and contend at the exact same instant.

---

## Entry 3 — Strict SOLID Refactoring & Contract Tightening

### Prompt Used
> "Follow SOLID principles, Explain repository and service classes in detail and workflow"

### Engineering Review & Changes Applied
- **SRP**: Extracted prerequisite evaluation out of `Learner` into `PrerequisiteEligibilityChecker` (`EligibilityChecker` + zero-allocation `EligibilityEvaluation.pass()` singleton), keeping `Learner` a pure state record.
- **ISP**: Split `LearnerReader` from `LearnerRepository`, `OfferingRosterReader` from `OfferingRosterStore`, and `RegistrationCommandService` / `RegistrationQueryService` from `RegistrationService` so read-only callers and `CourseRegistrationService` never depend on unused write methods.
- **LSP**: Enforced `Objects.requireNonNull` canonical ID checks and explicit capacity-ceiling checks inside `InMemoryOfferingRosterStore` so its runtime contract matches `ConcurrentHashMap`/persistent store substitutes.
- **DIP**: Removed all `InMemory*` imports from `CourseRegistrationService` and isolated default wiring in `CourseRegistrationFactory`.

---

## Independent Verification Evidence

1. **Clean Compilation & Packaging (Java 21.0.12.1 / Maven 3.9.12)**:
   - Compiled 27 main classes and 3 test suites targeting `--release 21` (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test-compile package`) with zero warnings or errors.
2. **Automated JUnit Jupiter Execution (51 / 51 Passing)**:
   - Executed all `@Test`, `@Nested`, and `@ParameterizedTest` cases across `DomainModelValidationTest`, `CourseRegistrationServiceTest`, and `CourseRegistrationConcurrencyTest`: **51 passed, 0 failed**, with `assertInvariants(...)` verified after every state transition and across 2,400+ concurrent operations in the mixed contention stress test.
3. **End-to-End CLI Prototype Verification**:
   - Executed `java -jar target/course-registration-waitlist-1.0.0.jar`, independently confirming enrollment, full-capacity waitlisting, duplicate protection, ineligible rejection, post-waitlist prerequisite revocation skip/eviction (`charlie` skipped, `diana` promoted), repeated drop idempotency (`NOT_REGISTERED`), and unknown-learner rejection (`INVALID_REQUEST`).
