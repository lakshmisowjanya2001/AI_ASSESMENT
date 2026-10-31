package com.assessment.registration.repository;

import com.assessment.registration.model.EnrollmentRecord;
import com.assessment.registration.model.WaitlistEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SequencedMap;

/**
 * Optimized in-memory implementation of {@link OfferingRosterStore} using Java 21
 * {@link SequencedMap} ({@link LinkedHashMap}).
 *
 * <h2>Complexity &amp; LSP Guarantees</h2>
 * <ul>
 *   <li>All lookups, insertions, arbitrary waitlist removals, and head polls run in O(1).</li>
 *   <li>Pre-sized via {@link LinkedHashMap#newLinkedHashMap(int)} so {@code enrolled} never rehashes.</li>
 *   <li>Enforces non-null canonical ID preconditions and capacity ceiling so behavior matches
 *       concurrent/persistent store substitutes (Liskov Substitution Principle).</li>
 * </ul>
 */
public final class InMemoryOfferingRosterStore implements OfferingRosterStore {

    private final int offeringCapacity;
    private final SequencedMap<String, EnrollmentRecord> enrolled;
    private final SequencedMap<String, WaitlistEntry> waitlist;

    public InMemoryOfferingRosterStore(int offeringCapacity) {
        if (offeringCapacity <= 0) {
            throw new IllegalArgumentException("offeringCapacity must be > 0, got: " + offeringCapacity);
        }
        this.offeringCapacity = offeringCapacity;
        this.enrolled = LinkedHashMap.newLinkedHashMap(offeringCapacity);
        this.waitlist = new LinkedHashMap<>();
    }

    @Override
    public boolean isEnrolled(String learnerId) {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        return enrolled.containsKey(learnerId);
    }

    @Override
    public boolean isWaitlisted(String learnerId) {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        return waitlist.containsKey(learnerId);
    }

    @Override
    public int enrolledCount() {
        return enrolled.size();
    }

    @Override
    public int waitlistSize() {
        return waitlist.size();
    }

    @Override
    public void addEnrolled(EnrollmentRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        String id = record.learnerId();
        if (enrolled.containsKey(id) || waitlist.containsKey(id)) {
            throw new IllegalStateException("Learner '" + id + "' is already registered in roster or waitlist");
        }
        if (enrolled.size() >= offeringCapacity) {
            throw new IllegalStateException("Cannot enroll '" + id + "': offering is already at capacity " + offeringCapacity);
        }
        enrolled.putLast(id, record);
    }

    @Override
    public boolean removeEnrolled(String learnerId) {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        return enrolled.remove(learnerId) != null;
    }

    @Override
    public int addWaitlisted(WaitlistEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        String id = entry.learnerId();
        if (enrolled.containsKey(id) || waitlist.containsKey(id)) {
            throw new IllegalStateException("Learner '" + id + "' is already registered in roster or waitlist");
        }
        waitlist.putLast(id, entry);
        return waitlist.size();
    }

    @Override
    public boolean removeWaitlisted(String learnerId) {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        return waitlist.remove(learnerId) != null;
    }

    @Override
    public Optional<WaitlistEntry> pollEarliestWaitlisted() {
        Map.Entry<String, WaitlistEntry> first = waitlist.pollFirstEntry();
        return first == null ? Optional.empty() : Optional.of(first.getValue());
    }

    @Override
    public Optional<EnrollmentRecord> findEnrollmentRecord(String learnerId) {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        return Optional.ofNullable(enrolled.get(learnerId));
    }

    @Override
    public OptionalInt findWaitlistPosition(String learnerId) {
        Objects.requireNonNull(learnerId, "learnerId must not be null");
        if (!waitlist.containsKey(learnerId)) {
            return OptionalInt.empty();
        }
        int position = 1;
        for (String queuedId : waitlist.sequencedKeySet()) {
            if (queuedId.equals(learnerId)) {
                return OptionalInt.of(position);
            }
            position++;
        }
        return OptionalInt.empty();
    }

    @Override
    public List<String> enrolledLearnerIds() {
        return List.copyOf(enrolled.sequencedKeySet());
    }

    @Override
    public List<String> waitlistedLearnerIds() {
        return List.copyOf(waitlist.sequencedKeySet());
    }
}
