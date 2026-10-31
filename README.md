# Course Registration Waitlist Slice (Java 21)

Bounded course-offering registration service implementing **enrollment**, **dropping**, and **FIFO waitlist promotion** with $O(1)$ amortized operations, fair read-write concurrency control, comprehensive JUnit 5 coverage, and a minimal CLI prototype.

## Deliverables Index

- **Design Specification**: [`DESIGN_SPEC.md`](./DESIGN_SPEC.md) — service boundary, API & data structures, invariants, prerequisite & skip-and-evict promotion policy, concurrency model, and tradeoffs.
- **AI Prompt & Use Record**: [`AI_USAGE_RECORD.md`](./AI_USAGE_RECORD.md) — prompts used, accepted/edited/rejected AI suggestions, defect caught during verification, and test evidence.
- **Source Code**: `src/main/java/com/assessment/registration/`
- **Automated JUnit 5 Tests**: `src/test/java/com/assessment/registration/`

---

## Prerequisites & Setup

- **JDK**: OpenJDK **21** (`/usr/lib/jvm/java-21-openjdk-amd64`)
- **Build Tool**: Apache Maven 3.9+

Export `JAVA_HOME` pointing to Java 21 before running Maven commands:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## Build, Test & Run Instructions

### 1. Run All Automated JUnit 5 Tests
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn clean test
```

### 2. Package & Run the CLI Prototype
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn package -DskipTests
/usr/lib/jvm/java-21-openjdk-amd64/bin/java -jar target/course-registration-waitlist-1.0.0.jar
```

---

## Concise Summary of Limitations & Follow-Up Risks (Within Scope)

1. **Volatile In-Memory State**: Roster, waitlist, and learner records reside in JVM memory (`LinkedHashMap` / `ConcurrentHashMap`) and are lost on process restart. Migrating to a relational store would require translating the in-process `ReentrantReadWriteLock` critical section into a `SELECT ... FOR UPDATE` row lock on the offering record (or serializable transaction with unique constraints on `(offering_id, learner_id)`).
2. **Post-Enrollment Prerequisite Revocation**: Consistent with standard university registrars and the stated scope, prerequisite revocation is enforced at enrollment and waitlist promotion boundaries; learners *already enrolled* in a seat are not retroactively de-enrolled if a prerequisite record changes later, unless an administrative drop is invoked.
3. **Passive Notification on Promotion / Eviction**: `DropResult` synchronously returns `promotedLearnerId` and `skippedIneligibleLearnerIds` to the caller of `drop()`, but there is no asynchronous outbox or notification channel to alert promoted or skipped waitlisted learners out-of-band.
4. **Unbounded Waitlist Capacity**: The FIFO waitlist currently grows up to available heap memory; high-demand production offerings may warrant an explicit `maxWaitlistCapacity` cap to bound memory usage and set realistic learner expectations.
5. **Single-JVM Lock Granularity**: `ReentrantReadWriteLock(true)` serializes writes per `CourseRegistrationService` instance within one JVM. Horizontal multi-node scaling would require distributed coordination or database-backed optimistic/pessimistic locking.
