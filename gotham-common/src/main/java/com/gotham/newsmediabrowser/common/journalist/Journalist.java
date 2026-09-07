package com.gotham.newsmediabrowser.common.journalist;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A journalist (master data), stored as one document in the {@code gotham-journalists} index.
 *
 * <p>Immutable. The {@code id} is the Elasticsearch {@code _id} (auto-generated on create, so it is
 * {@code null} for a journalist that has not been persisted yet). {@code fullName} is not stored as
 * state here — it is always {@link #fullName() derived} from the first and last name so the two can
 * never disagree.
 *
 * @param id        Elasticsearch {@code _id}; {@code null} before the journalist is created
 * @param firstName given name
 * @param lastName  family name
 * @param email     contact email (optional; unique when present)
 * @param bio       short biography (optional)
 * @param createdAt when the master record was first created (set by the repository)
 * @param updatedAt when the master record was last updated (set by the repository)
 */
public record Journalist(
        String id,
        String firstName,
        String lastName,
        String email,
        String bio,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Creates a journalist that has not been persisted yet: no id and no timestamps (the repository
     * assigns them on create).
     */
    public static Journalist newJournalist(String firstName, String lastName, String email, String bio) {
        return new Journalist(null, firstName, lastName, email, bio, null, null);
    }

    /** The display name, derived by joining the non-blank name parts with a single space. */
    public String fullName() {
        return Stream.of(firstName, lastName)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    /** Returns a copy with the given Elasticsearch id. */
    public Journalist withId(String newId) {
        return new Journalist(newId, firstName, lastName, email, bio, createdAt, updatedAt);
    }

    /** Returns a copy with the given creation/update timestamps. */
    public Journalist withTimestamps(Instant created, Instant updated) {
        return new Journalist(id, firstName, lastName, email, bio, created, updated);
    }
}
