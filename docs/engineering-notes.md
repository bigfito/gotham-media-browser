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
- **Fix:** request it explicitly — `sourceIncludes("*")` on the get/read path, so the edit round-trip
  (`findById` → form → save) carries each `asset_vector` back into the write instead of dropping it.
- **Where:** `ArticleRepository.findById`, `ArticleEmbeddingImageBindIT`; found in P6-T03.

### A full reindex is the wrong write for a journalist cascade
- **Symptom:** renaming (or cascade-deleting) a journalist silently emptied `article_embedding` on every
  article they byline. A first fix made the sweep read vectors back with `sourceIncludes("*")`, which
  rescued `multimedia.asset_vector` but not the article vector.
- **Cause:** the cascade was reindexing whole documents through `ArticleRepository.update`, and
  `toDocument` **recomputes** `article_embedding` from ImageBind on every write. With the embedder down
  that recompute returns `null` and the field is simply omitted — so a rename with ImageBind unavailable
  wiped the semantic vector of every affected article. Reading vectors back cannot fix that half: the
  article vector is never read, it is always regenerated.
- **Fix:** don't reindex at all. `ArticleRepository.updateJournalistBylines` issues a **partial** ES
  `update` carrying only `journalists`, `journalist_names`, `journalist_bios` and `updated_at`. Fields
  it does not name are untouched, so both vector fields survive regardless of ImageBind, and the sweep
  goes back to excluding vectors (nothing needs them). Verified live with ImageBind pointed at a dead
  port: rename propagated, `article_embedding` and all six `asset_vector`s intact.
- **Where:** `ArticleRepository.updateJournalistBylines` / `bylinePatch`, `JournalistService`; the
  reindex hole was found and closed 2026-09-09.
- **Rule of thumb:** any write whose purpose is to change field X must not travel through a code path
  that regenerates field Y from a service that can be down.

### `copy_to` must sit on the parent keyword, not a `.text` sub-field
- **Symptom:** index create rejected — `copy_to` inside a multi-field is forbidden.
- **Fix:** move `copy_to` to the parent `keyword` field (the `.text` sub-field is analysis-only).
- **Where:** `elasticsearch/gotham-media-browser.mapping.json`; fixed in P2-T02.

### Serverless rejects shard/replica settings
- Serverless ignores/refuses `number_of_shards` / `number_of_replicas`; the bootstrap strips them
  before create while the mapping files keep them for portability. `IndexBootstrapper.serverlessSafe()`.

### Chrome ImageBind legend vs model warmup
- **Symptom:** header shows ImageBind Available while embeds return 503.
- **Cause:** `GET /health` is 200 as soon as uvicorn is up; `model_loaded` stays false until the
  background load finishes.
- **Fix:** `ImageBindHealthChecker` requires 2xx and `model_loaded` not false. The check is an anchored
  regex on the quoted JSON key, not a parse: `gotham-web` carries no Jackson (Spring Boot 4 makes JSON
  opt-in) and adding databind for one flag would switch on JSON message conversion app-wide.
- **Where:** `ImageBindHealthChecker`; `imagebind-service` `/health`.

### `docker build` dies with `./mvnw: not found` on a Windows checkout
- **Symptom:** the image build fails at `RUN chmod +x mvnw && ./mvnw ...` with exit 127, even though
  `mvnw` is right there in the build context.
- **Cause:** CRLF. `git ls-files --eol mvnw` showed `i/lf w/crlf` — the repo stores LF and
  `.gitattributes` pins `mvnw text eol=lf`, but the working tree was checked out **before** that rule
  existed, so `core.autocrlf` left CRLF on disk. Docker builds from the working tree, and `/bin/sh`
  cannot find the interpreter `"/bin/sh"`.
- **Fix (local working copy, not a repo change):** `rm mvnw && git checkout -- mvnw`, then confirm
  `git ls-files --eol mvnw` reads `w/lf`. `git add --renormalize .` fixes a whole stale checkout.
- **Watch for:** any file `.gitattributes` marks `eol=lf` that a container executes — check with
  `git ls-files --eol | grep "eol=lf" | grep "w/crlf"` before blaming the Dockerfile.
- **Where:** `mvnw`, `.gitattributes`, `gotham-web/Dockerfile`; hit 2026-09-09 on Windows.

### Upload duration limits can only be best-effort
- **Symptom:** every MP3 upload was refused with a branded 413 — "duration could not be determined".
- **Cause:** `MediaDurationProbe` reads WAV/AIFF/AU (Java Sound SPI) and the MP4/MOV `mvhd` box only,
  but `MediaType.fromContentType` accepts any `audio/*` / `video/*` and the form offers the same. Making
  an unknown duration a hard rejection therefore banned MP3, OGG, FLAC and WebM.
- **Fix, part 1:** `GcsStorageService` enforces the duration cap when a duration was parsed and
  otherwise stores the object on its size cap alone, logging a WARN. The size cap (20 MB audio /
  50 MB video) already bounds the file; a demo-only time cap is not worth refusing formats the UI
  invites.
- **Fix, part 2 — real measurement:** `MediaDurationProbe` is now a two-tier Spring bean. Tier 1 is an
  **`ffprobe` subprocess** (`-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1`),
  which reads MP3/OGG/FLAC/WebM/MOV/MKV; tier 2 is the old pure-Java `ContainerDurationParser` for
  hosts with no ffmpeg. Configured under `gotham.media.probe.*`; the `gotham-web` image installs
  `ffmpeg` so the containerized path always has it. Unknown stays *accepted* — ffprobe makes unknown
  rare, it does not make the binary a hard dependency.
- **Subprocess hygiene (the part that bites):** build the command as an argument **list**, never a
  shell string; close stdin so the child cannot block waiting on input; send stderr to
  `Redirect.DISCARD` so a file that provokes pages of diagnostics cannot fill an undrained pipe and
  wedge the writer; drain stdout *before* `waitFor`; use the timeout overload of `waitFor` and
  `destroyForcibly` in a `finally`. Skip any one of these and a bad upload hangs a request thread.
- **Where:** `GcsStorageService.enforceLimits`, `MediaDurationProbe`, `ContainerDurationParser`,
  `MediaProbeProperties`, `gotham-web/Dockerfile`; relaxed then properly measured 2026-09-09.


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
