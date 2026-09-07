package com.gotham.newsmediabrowser.common.journalist;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.gotham.newsmediabrowser.common.error.DependencyException;
import com.gotham.newsmediabrowser.common.index.IndexDefinition;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Reads and writes journalist master documents in the {@code gotham-journalists} index.
 *
 * <p>Elasticsearch assigns the {@code _id}; {@code full_name} and the timestamps are maintained here
 * so callers never have to. Writes refresh immediately ({@link Refresh#True}) so the list view and
 * the automated tests see a change the moment it is made — acceptable at this prototype's low write
 * volume. Any Elasticsearch failure is surfaced as a {@link DependencyException} (HTTP 503) that
 * names the service without leaking connection details.
 */
@Repository
public class JournalistRepository {

    private static final Logger log = LoggerFactory.getLogger(JournalistRepository.class);
    private static final String INDEX = IndexDefinition.JOURNALISTS.indexName();
    private static final String SERVICE = "Elasticsearch";

    private final ElasticsearchClient client;

    public JournalistRepository(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * Creates a new journalist, stamping the creation/update time. Elasticsearch generates the id.
     *
     * @return the stored journalist, now carrying its id and timestamps
     */
    public Journalist create(Journalist journalist) {
        Instant now = Instant.now();
        Journalist toStore = journalist.withId(null).withTimestamps(now, now);
        try {
            String id = client.index(request -> request
                    .index(INDEX)
                    .document(toDocument(toStore))
                    .refresh(Refresh.True)).id();
            log.info("Created journalist '{}' with id {}", toStore.fullName(), id);
            return toStore.withId(id);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /** Finds a journalist by id, or empty if there is no such document. */
    public Optional<Journalist> findById(String id) {
        try {
            var response = client.get(request -> request.index(INDEX).id(id), Map.class);
            if (!response.found() || response.source() == null) {
                return Optional.empty();
            }
            return Optional.of(fromSource(response.id(), asStringKeyedMap(response.source())));
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /**
     * Overwrites an existing journalist, refreshing the update time and preserving the original
     * creation time.
     *
     * @throws IllegalArgumentException if the journalist has no id (nothing to update)
     */
    public Journalist update(Journalist journalist) {
        if (journalist.id() == null || journalist.id().isBlank()) {
            throw new IllegalArgumentException("Cannot update a journalist without an id.");
        }
        Instant createdAt = journalist.createdAt() != null ? journalist.createdAt() : Instant.now();
        Journalist toStore = journalist.withTimestamps(createdAt, Instant.now());
        try {
            client.index(request -> request
                    .index(INDEX)
                    .id(toStore.id())
                    .document(toDocument(toStore))
                    .refresh(Refresh.True));
            log.info("Updated journalist id {}", toStore.id());
            return toStore;
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /**
     * Deletes a journalist by id.
     *
     * @return {@code true} if a document was deleted, {@code false} if none existed
     */
    public boolean deleteById(String id) {
        try {
            var response = client.delete(request -> request.index(INDEX).id(id).refresh(Refresh.True));
            boolean deleted = response.result().jsonValue().equals("deleted");
            log.info("Delete journalist id {} -> {}", id, response.result().jsonValue());
            return deleted;
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    /**
     * Lists journalists, newest first, for the requested window.
     *
     * @param from zero-based offset of the first result
     * @param size page size
     */
    public JournalistPage findAll(int from, int size) {
        try {
            SearchResponse<Map> response = client.search(request -> request
                    .index(INDEX)
                    .from(from)
                    .size(size)
                    .trackTotalHits(track -> track.enabled(true))
                    .sort(sort -> sort.field(field -> field.field("created_at").order(SortOrder.Desc))),
                    Map.class);

            List<Journalist> items = response.hits().hits().stream()
                    .map(hit -> fromSource(hit.id(), asStringKeyedMap(hit.source())))
                    .toList();
            long total = response.hits().total() != null ? response.hits().total().value() : items.size();
            return new JournalistPage(items, total);
        } catch (IOException | ElasticsearchException e) {
            throw new DependencyException(SERVICE, e);
        }
    }

    // --- Journalist <-> Elasticsearch document mapping (package-private for direct unit tests) ---

    /** Builds the Elasticsearch {@code _source} for a journalist (snake_case fields, ISO-8601 dates). */
    Map<String, Object> toDocument(Journalist journalist) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("first_name", journalist.firstName());
        document.put("last_name", journalist.lastName());
        document.put("full_name", journalist.fullName());
        document.put("email", journalist.email());
        document.put("bio", journalist.bio());
        document.put("created_at", toIso(journalist.createdAt()));
        document.put("updated_at", toIso(journalist.updatedAt()));
        return document;
    }

    /** Rebuilds a journalist from its id and Elasticsearch {@code _source}. */
    Journalist fromSource(String id, Map<String, Object> source) {
        return new Journalist(
                id,
                asString(source.get("first_name")),
                asString(source.get("last_name")),
                asString(source.get("email")),
                asString(source.get("bio")),
                parseInstant(source.get("created_at")),
                parseInstant(source.get("updated_at")));
    }

    private String toIso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private Instant parseInstant(Object value) {
        return value != null ? Instant.parse(value.toString()) : null;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asStringKeyedMap(Object source) {
        return (Map<String, Object>) source;
    }
}
