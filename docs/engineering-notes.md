# Engineering notes — non-obvious learnings

Hard-won, non-obvious findings from building this prototype, kept in one place so they are not
buried in the [`implementation-state.md`](./implementation-state.md) task log. Each entry is
**symptom → cause → fix → where**. The DSL cookbook is
[`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md).

## Elasticsearch (Serverless, `elasticsearch-java` 9.4.5)

### Nested vector kNN leaves `inner_hits` empty
- **Symptom:** a top-level `knn` over the nested `multimedia.asset_vector` returns the parent article
  but `inner_hits.matched_media` comes back empty (`innerCount=0`), so the matched asset can't render.
- **Cause:** on Serverless, a top-level `knn` search option does not populate nested `inner_hits`.
- **Fix:** run kNN as a `knn` **query** (`co.elastic.clients...KnnQuery`) wrapped in a `nested` query
  that carries `inner_hits` (cookbook §8's "explicit nested knn context"). Article `article_embedding`
  is a top-level (non-nested) vector, so it correctly uses the top-level `knn` option (§5).
- **Where:** `MultimediaSemanticSearchService.nestedKnnQuery`; found live in P8-T01.

### RRF rejects two legs that share an `inner_hits` name
- **Symptom:** hybrid multimedia search fails with
  `illegal_argument_exception: [inner_hits] already contains an entry for key [matched_media]`.
- **Cause:** `retriever.rrf` merges every leg's `inner_hits` into one map; two nested legs using the
  same name collide.
- **Fix:** the kNN leg uses a distinct name (`matched_media_knn`); `MultimediaHitMapper.cards(…, names)`
  reads both blocks and de-duplicates assets by id.
- **Where:** `MultimediaHybridSearchService`; found live in P8-T02.

### RRF retriever legs cannot carry filters in this client
- **Symptom:** `KnnRetriever` / `StandardRetriever` expose no `filter` method, but shared filters must
  apply to both RRF legs.
- **Fix:** build both legs as `standard` retrievers whose queries are `bool{ must: …, filter: shared }`
  (the kNN leg uses a `knn` query inside that bool). Pagination: top-level `from`/`size` with
  `rank_window_size = max(50, from + size)` works on Serverless (no app-side window slicing needed).
- **Where:** `ArticleHybridSearchService` / `MultimediaHybridSearchService`; cookbook §6/§9.

### `dense_vector` is excluded from `_source` by default
- **Symptom:** reading a document back returns `null` for `article_embedding` / `asset_vector`, and an
  edit re-save would wipe a stored asset vector.
- **Cause:** ES 9 Serverless excludes indexed `dense_vector` from `_source` retrieval by default.
- **Fix:** request it explicitly — `sourceIncludes("*")` on the get/read path. `article_embedding` is
  recomputed on every write, so it needs no round-trip; per-asset `asset_vector` is preserved via the
  `*` include on `findById`.
- **Where:** `ArticleRepository.findById`, `ArticleEmbeddingImageBindIT`; found in P6-T03.

### `copy_to` must sit on the parent keyword, not a `.text` sub-field
- **Symptom:** index create rejected — `copy_to` inside a multi-field is forbidden.
- **Fix:** move `copy_to` to the parent `keyword` field (the `.text` sub-field is analysis-only).
- **Where:** `elasticsearch/gotham-media-browser.mapping.json`; fixed in P2-T02.

### Serverless rejects shard/replica settings
- Serverless ignores/refuses `number_of_shards` / `number_of_replicas`; the bootstrap strips them
  before create while the mapping files keep them for portability. `IndexBootstrapper.serverlessSafe()`.

## ImageBind service (FastAPI / uvicorn)

### `java.net.http` defaults to HTTP/2 and drops the POST body against uvicorn
- **Symptom:** FastAPI returns `422` with an empty body; embeds fail.
- **Cause:** the JDK HttpClient negotiates HTTP/2; uvicorn is HTTP/1.1-only and drops the body.
- **Fix:** force `HttpClient.Version.HTTP_1_1` in `HttpImageBindClient`. Found in P6-T02.

### Real model memory
- The real `imagebind_huge` load needs peak memory above the ~4.5 GB on-disk weights; the container is
  given `mem_limit 12g` and a weights volume. Use `IMAGEBIND_BACKEND=stub` for CI / laptop demos
  (deterministic offline 1024-d vectors, no download).

## Google Cloud Storage

- The bucket uses **uniform bucket-level access**, so per-object ACLs are impossible. Public reads come
  from the bucket IAM policy (`allUsers:objectViewer`, provisioned once); uploads set **no** ACL. See
  [`../secrets/README.md`](../secrets/README.md). The GCS client bean is lazy — no network at boot.

## Build & test

### Run integration profiles through the `verify` phase
- **Symptom:** `mvn -Pit-es failsafe:integration-test failsafe:verify` fails with
  `ClassNotFoundException: …MultimediaSearchHit` in the `gotham-web` fork.
- **Cause:** invoking the Failsafe goals directly does not build the reactor dependency
  (`gotham-common`) onto the `gotham-web` test classpath.
- **Fix:** run through the lifecycle — `mvn -Pit-es verify` / `mvn -Pit-imagebind verify` /
  `mvn -Pit-datagen-helpers verify`. Profiles split ITs by JUnit tag (`it-es` = `integration` −
  `imagebind` − `datagen`; `it-imagebind` = `imagebind`; `it-datagen-helpers` = `datagen`); every
  IT self-skips via `assumeTrue` / `@EnabledIf` when its deps are absent. Found in P9-T03 / P10-T06.

### Datagen helper ITs skip real Wan unless opted in
- **Symptom:** a live ComfyUI T2V IT would sit for minutes (or timeout) on M4 CPU.
- **Fix:** `ComfyuiClientIT` runs T2V only against the in-repo stub (`system_stats` contains `stub`)
  or when `DATAGEN_IT_VIDEO=true`. The small orchestrator IT always `--skip-video`.

### Spring Boot 4 moved the test auto-configuration packages
- `@WebMvcTest` → `org.springframework.boot.webmvc.test.autoconfigure` (needs
  `spring-boot-starter-webmvc-test` + `thymeleaf-test`); `@AutoConfigureMockMvc` →
  `org.springframework.boot.webmvc.test.autoconfigure`. `@MockitoBean` replaces `@MockBean`.

### Shell scripts stay LF
- `.gitattributes` pins `*.sh` / `mvnw` to `eol=lf` so the seed/smoke scripts run on the macOS lab
  regardless of the contributor's OS.
