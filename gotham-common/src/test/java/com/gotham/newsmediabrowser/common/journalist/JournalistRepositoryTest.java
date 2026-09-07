package com.gotham.newsmediabrowser.common.journalist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for the journalist document mapping (no Elasticsearch needed). The live CRUD round-trip
 * is covered by {@code JournalistRepositoryIT}.
 */
class JournalistRepositoryTest {

    // The client is never touched by the mapping methods under test.
    private final JournalistRepository repository =
            new JournalistRepository(Mockito.mock(co.elastic.clients.elasticsearch.ElasticsearchClient.class));

    @Test
    void toDocumentUsesSnakeCaseFieldsDerivedFullNameAndIsoDates() {
        Instant created = Instant.parse("2026-01-02T03:04:05Z");
        Instant updated = Instant.parse("2026-02-03T04:05:06Z");
        Journalist journalist = new Journalist("id1", "Vicki", "Vale", "vicki@gazette.gotham", "Reporter",
                created, updated);

        Map<String, Object> document = repository.toDocument(journalist);

        assertThat(document).containsEntry("first_name", "Vicki")
                .containsEntry("last_name", "Vale")
                .containsEntry("full_name", "Vicki Vale")
                .containsEntry("email", "vicki@gazette.gotham")
                .containsEntry("bio", "Reporter")
                .containsEntry("created_at", "2026-01-02T03:04:05Z")
                .containsEntry("updated_at", "2026-02-03T04:05:06Z");
        // The ES _id is never written into _source (the index mapping is strict and has no id field).
        assertThat(document).doesNotContainKey("id");
    }

    @Test
    void fromSourceRebuildsTheJournalistIncludingTheId() {
        Map<String, Object> source = Map.of(
                "first_name", "Clark",
                "last_name", "Kent",
                "full_name", "Clark Kent",
                "email", "clark@planet.metropolis",
                "bio", "Mild-mannered",
                "created_at", "2026-01-02T03:04:05Z",
                "updated_at", "2026-02-03T04:05:06Z");

        Journalist journalist = repository.fromSource("doc42", source);

        assertThat(journalist.id()).isEqualTo("doc42");
        assertThat(journalist.firstName()).isEqualTo("Clark");
        assertThat(journalist.lastName()).isEqualTo("Kent");
        assertThat(journalist.email()).isEqualTo("clark@planet.metropolis");
        assertThat(journalist.bio()).isEqualTo("Mild-mannered");
        assertThat(journalist.createdAt()).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(journalist.updatedAt()).isEqualTo(Instant.parse("2026-02-03T04:05:06Z"));
        // full_name is derived, so it matches what we would recompute.
        assertThat(journalist.fullName()).isEqualTo("Clark Kent");
    }

    @Test
    void documentRoundTripsBackToAnEquivalentJournalist() {
        Journalist original = new Journalist("id9", "Selina", "Kyle", null, null,
                Instant.parse("2026-03-03T03:03:03Z"), Instant.parse("2026-03-04T04:04:04Z"));

        Journalist roundTripped = repository.fromSource("id9", repository.toDocument(original));

        assertThat(roundTripped).isEqualTo(original);
    }
}
