package com.gotham.newsmediabrowser.common.journalist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live CRUD + search round-trip against a real {@code gotham-journalists} index (P3-T01
 * verification). Skipped unless {@code ES_ENDPOINT} / {@code ES_API_KEY} are set and the cluster is
 * reachable, so it never breaks a no-infrastructure {@code mvn test}. Wired into the {@code it-es}
 * Failsafe profile in P9-T03; runnable now with:
 *
 * <pre>mvn -pl gotham-common test -Dtest=JournalistRepositoryIT -DfailIfNoTests=false</pre>
 */
@Tag("integration")
class JournalistRepositoryIT {

    private static ElasticsearchClient client;

    @BeforeAll
    static void connect() {
        String endpoint = System.getenv("ES_ENDPOINT");
        String apiKey = System.getenv("ES_API_KEY");
        assumeTrue(endpoint != null && !endpoint.isBlank(), "ES_ENDPOINT not set — skipping live ES test");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "ES_API_KEY not set — skipping live ES test");

        ElasticsearchClient candidate = ElasticsearchClient.of(builder -> builder.host(endpoint).apiKey(apiKey));
        try {
            candidate.info(); // Serverless-safe reachability probe
        } catch (Exception e) {
            assumeTrue(false, "Elasticsearch not reachable — skipping: " + e.getMessage());
        }
        client = candidate;
    }

    @Test
    void createReadUpdateSearchDelete() {
        JournalistRepository repository = new JournalistRepository(client);
        String marker = "IT-" + System.nanoTime();
        Journalist created = null;
        try {
            // create
            created = repository.create(Journalist.newJournalist("Vicki", marker, "vicki@gazette.gotham", "Reporter"));
            assertThat(created.id()).isNotBlank();
            assertThat(created.createdAt()).isNotNull();
            assertThat(created.fullName()).isEqualTo("Vicki " + marker);

            // read
            Optional<Journalist> fetched = repository.findById(created.id());
            assertThat(fetched).isPresent();
            assertThat(fetched.get().lastName()).isEqualTo(marker);

            // update
            Journalist edited = repository.update(new Journalist(created.id(), "Victoria", marker,
                    "victoria@gazette.gotham", "Senior reporter", created.createdAt(), created.updatedAt()));
            assertThat(edited.updatedAt()).isAfterOrEqualTo(created.createdAt());
            assertThat(repository.findById(created.id())).get()
                    .extracting(Journalist::firstName).isEqualTo("Victoria");

            // search (newest first, includes our doc)
            String createdId = created.id();
            JournalistPage page = repository.findAll(0, 100);
            assertThat(page.total()).isGreaterThanOrEqualTo(1);
            assertThat(page.items()).anyMatch(j -> createdId.equals(j.id()));
        } finally {
            if (created != null && created.id() != null) {
                boolean deleted = repository.deleteById(created.id());
                assertThat(deleted).isTrue();
                assertThat(repository.findById(created.id())).isEmpty();
            }
        }
    }
}
