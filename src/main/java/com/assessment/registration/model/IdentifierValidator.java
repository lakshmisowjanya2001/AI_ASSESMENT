package com.assessment.registration.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared identifier validation and normalization utilities.
 *
 * <p>All domain identifiers (offering IDs, course IDs, learner IDs) are normalized
 * by stripping leading and trailing whitespace so inputs such as {@code "alice"} and
 * {@code " alice "} deterministically resolve to the same canonical identifier.
 */
public final class IdentifierValidator {

    private IdentifierValidator() {
    }

    /**
     * Validates that {@code rawId} is non-null and non-blank, returning its stripped form.
     *
     * @throws IllegalArgumentException if {@code rawId} is null or blank
     */
    public static String normalizeRequiredId(String rawId, String fieldName) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return rawId.strip();
    }

    /**
     * Attempts to normalize an external request identifier without throwing.
     *
     * @return the stripped identifier, or {@code null} if {@code rawId} is null or blank
     */
    public static String tryNormalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        return rawId.strip();
    }

    /**
     * Validates a set of course identifiers and returns an unmodifiable normalized copy.
     *
     * @throws IllegalArgumentException if the set itself or any element is null or blank
     */
    public static Set<String> normalizeCourseIdSet(Set<String> rawIds, String fieldName) {
        if (rawIds == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (rawIds.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = HashSet.newHashSet(rawIds.size());
        for (String id : rawIds) {
            normalized.add(normalizeRequiredId(id, fieldName + " element"));
        }
        return Set.copyOf(normalized);
    }
}
