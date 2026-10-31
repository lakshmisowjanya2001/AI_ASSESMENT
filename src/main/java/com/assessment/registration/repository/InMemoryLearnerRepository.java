package com.assessment.registration.repository;

import com.assessment.registration.model.IdentifierValidator;
import com.assessment.registration.model.Learner;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link LearnerRepository}.
 *
 * <p>Stores immutable {@link Learner} snapshots in a {@link ConcurrentHashMap},
 * enabling O(1) concurrent lookups and atomic profile updates (such as prerequisite
 * completion or credit revocation).
 */
public final class InMemoryLearnerRepository implements LearnerRepository {

    private final ConcurrentHashMap<String, Learner> learners = new ConcurrentHashMap<>();

    public InMemoryLearnerRepository() {
    }

    public InMemoryLearnerRepository(Collection<Learner> initialLearners) {
        Objects.requireNonNull(initialLearners, "initialLearners must not be null");
        for (Learner learner : initialLearners) {
            save(learner);
        }
    }

    @Override
    public void save(Learner learner) {
        Objects.requireNonNull(learner, "learner must not be null");
        learners.put(learner.learnerId(), learner);
    }

    @Override
    public Optional<Learner> findById(String learnerId) {
        String normalized = IdentifierValidator.tryNormalizeId(learnerId);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(learners.get(normalized));
    }

    @Override
    public boolean existsById(String learnerId) {
        String normalized = IdentifierValidator.tryNormalizeId(learnerId);
        return normalized != null && learners.containsKey(normalized);
    }
}
