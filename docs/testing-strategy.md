# Testing Strategy — Gotham News & Media Browser

**Audience:** AI coding agents and humans  
**Related:** [`implementation-plan.md`](./implementation-plan.md) · [`implementation-state.md`](./implementation-state.md) · [`synthetic-data-generation.md`](./synthetic-data-generation.md)

---

## 1. Mandate (locked)

| Layer | Requirement |
|-------|-------------|
| **Backend** (`gotham-common`, services, repositories, clients, datagen) | **Unit tests** for every new/changed production class with logic |
| **Frontend** (Thymeleaf controllers, form binding, view models, error handling) | **Unit / web-slice tests** (MockMvc) for every new/changed controller and critical view wiring |
| **Integration** | Dedicated suites for **Elasticsearch**, **ImageBind**, and **datagen helpers** (Ollama · ComfyUI · Kokoro) |
| **CI default** | `mvn test` (unit + slice) must pass; integration suites via Failsafe / profiles (may need live or Docker deps) |

Agents **must not** mark a coding task `done` without adding or updating tests for the code that task introduced, and running them.

---

## 2. Tooling (prototype defaults)

| Concern | Choice |
|---------|--------|
| Unit / slice | JUnit **5** · Maven Surefire · Mockito · Spring Boot test (`@WebMvcTest`, `@JsonTest`, `@ExtendWith(MockitoExtension.class)`) |
| Web UI (server-rendered) | **MockMvc** (+ optional HtmlUnit) asserting status, model attributes, and key HTML fragments |
| Integration | Maven **Failsafe** (`*IT.java`) · Spring `@SpringBootTest` where needed · Docker Compose services |
| Elasticsearch | Live **Elastic Cloud** (lab properties) **or** Testcontainers Elasticsearch when viable; otherwise Failsafe profile `it-es` skipped cleanly if endpoint unset |
| ImageBind | Live Compose `imagebind-service` **or** stub mode for unit; Failsafe `it-imagebind` against `:8081` |
| Datagen helpers | Failsafe `it-datagen-helpers` against Compose profile `datagen` (`:11434`, `:8188`, `:8880`) |
| GCS | Unit: mocked storage client; optional live IT gated on secret file presence |

Commands (from package root):

```bash
# Unit + web-slice (required on every coding task)
mvn -q test

# Integration (profiles; run through `verify` so the reactor builds gotham-common first;
# each IT skips gracefully via JUnit assumptions / @EnabledIf when its deps are unavailable)
ES_ENDPOINT=… ES_API_KEY=…            mvn -q -Pit-es verify
IMAGEBIND_BASE_URL=http://127.0.0.1:8081 ES_ENDPOINT=… ES_API_KEY=… mvn -q -Pit-imagebind verify
mvn -q -Pit-datagen-helpers verify    # Ollama / ComfyUI / Kokoro / small orchestrator
```

`it-es` runs every `@Tag("integration")` IT except the `@Tag("imagebind")` ones; `it-imagebind` runs
only the `@Tag("imagebind")` ITs (which also need Elasticsearch for the write-path check). Plain
`mvn test` (Surefire) never runs `*IT`.

---

## 3. Unit test expectations by area

| Area | What to cover |
|------|----------------|
| Domain / projections | Enums, `full_name`, projection builders, validation rules |
| Repositories (mocked ES client) | Query construction, mapping of responses, cascade-strip helpers |
| GCS / ImageBind Java clients | Request shaping, error mapping, stub mode |
| Controllers | Happy path + validation + error-page mapping (MockMvc) |
| Search services | Field remap, filter assembly, pagination `from`/`size`, vector-mode rejection |
| `gotham-datagen` | CLI arg/property parsing, skip-flag policy, HTTP client request bodies (mocked servers) |

**Frontend note:** There is no SPA. “Frontend unit tests” means **controller + Thymeleaf model/view** tests, not Jest/React. Visual mockups under `ui-mockups/` remain the UI reference; MockMvc asserts behavior parity.

**Assertion note:** `containsString` matches the whole document, so a label assertion can silently re-anchor onto another element after a rename and keep passing. Pin controls by something unique to them (an `id`, a `name`, an `aria-label`), never by visible text that also appears in a heading. See [`engineering-notes.md`](./engineering-notes.md).

---

## 4. Integration test suites

**Failsafe profiles `it-es`, `it-imagebind`, and `it-datagen-helpers` exist.** All `*IT.java` self-skip via JUnit
`assumeTrue` / `@EnabledIf` when their deps are missing, so a profile run stays green without them.
Default Surefire does **not** include `*IT` (`mvn test` stays unit + `*Test`). Run a suite with
`mvn -Pit-es verify` / `mvn -Pit-imagebind verify` (through `verify` so the reactor builds first), or a
single class with `mvn -pl gotham-common test -Dtest=ArticleFullTextServiceIT` (add `-am` for a
`gotham-web` class). Split by JUnit tag: **`it-es`** = `integration` − `imagebind`; **`it-imagebind`**
= `imagebind`; **`it-datagen-helpers`** = `datagen` (`it-es` also excludes `datagen` so helper ITs
do not run on the ES profile).

| Class | Tag / gate | Covers |
|-------|------------|--------|
| `IndexBootstrapIT` | es · `ES_ENDPOINT`+`ES_API_KEY` | Idempotent index bootstrap (create / no-op) |
| `JournalistRepositoryIT` | es · same | Live journalist CRUD |
| `JournalistServiceIT` | es · same | Cascade-strip delete + article reindex |
| `ArticleRepositoryIT` | es · same | Live article CRUD / nest |
| `ArticleFullTextServiceIT` | es · same | Cookbook §4 FTS |
| `MultimediaFullTextServiceIT` | es · same | Cookbook §7 nested FTS + inner_hits |
| `ArticleSemanticSearchServiceIT` | es · same | Cookbook §5 article kNN |
| `MultimediaSemanticSearchServiceIT` | es · same | Cookbook §8 nested kNN + inner_hits |
| `ArticleHybridSearchServiceIT` | es · same | Cookbook §6 article RRF |
| `MultimediaHybridSearchServiceIT` | es · same | Cookbook §9 nested RRF (distinct inner_hits names) |
| `MultimediaVectorSearchServiceIT` (`gotham-web`) | es · same | Cookbook §10 file→vector nested kNN |
| `WebFlowsIT` (`gotham-web`) | es · `@EnabledIf` `ES_ENDPOINT`+`ES_API_KEY` | `@SpringBootTest` MockMvc: `/`, `/journalist`, `/results`, article-vector 400 |
| `HttpImageBindClientIT` | imagebind · `IMAGEBIND_BASE_URL` | Live 1024-d embed (text + image) |
| `ArticleEmbeddingImageBindIT` | imagebind · `IMAGEBIND_BASE_URL` + ES | Write path: article save → `article_embedding` 1024-d |
| `GcsStorageServiceIT` | es · GCS secret / env | Live public object put |
| `OllamaClientIT` (`gotham-datagen`) | datagen · live `:11434` | Health + JSON chat (`qwen2.5:7b-instruct`) |
| `ComfyuiClientIT` (`gotham-datagen`) | datagen · live `:8188` | Health + T2I; T2V on stub or `DATAGEN_IT_VIDEO=true` |
| `KokoroClientIT` (`gotham-datagen`) | datagen · live `:8880` | Health + TTS WAV bytes |
| `DatagenOrchestratorIT` (`gotham-datagen`) | datagen · web + Ollama | 1 journalist + 1 article via HTTP; media skip if helper down |

### 4.1 Elasticsearch (`it-es`) — **done (P9-T03)**; lab run `mvn -Pit-es verify` green (15 ITs)

| Case | Intent |
|------|--------|
| Index bootstrap | Idempotent create of `gotham-journalists` + `gotham-media-browser` |
| Journalist CRUD | Index/get/update/delete against live ES |
| Article CRUD + nest | Bylines + projections persisted |
| Cascade-strip | Delete journalist removes nested byline from articles **without wiping** `asset_vector` |
| FTS / filters | Article + multimedia queries from cookbook smoke set |
| kNN / hybrid (when embeddings present) | Semantic/hybrid smoke with 1024-d vectors |

### 4.2 ImageBind (`it-imagebind`) — **done (P9-T03)**; lab run `mvn -Pit-imagebind verify` green (3 ITs, stub backend)

| Case | Intent |
|------|--------|
| Health | `GET /health` → 200 |
| Embed text | Response length **1024** |
| Embed image/audio/video | Fixture bytes → length **1024** |
| Write path | Article save populates `article_embedding` + `asset_vector` when service up |

### 4.3 Datagen helpers (`it-datagen-helpers`) — **done (P10-T06)**

Run through the lifecycle: `mvn -Pit-datagen-helpers verify`. Each IT probes the Compose default
port (overridable via `OLLAMA_URL` / `COMFYUI_URL` / `KOKORO_URL` / `GOTHAM_WEB_URL` /
`OLLAMA_MODEL`) and `assumeTrue`-skips when the helper is down. Wan T2V is skipped unless the
in-repo ComfyUI stub is detected or `DATAGEN_IT_VIDEO=true` (CPU clips are minutes each).

| Case | Intent |
|------|--------|
| Ollama | Health + chat completion with `qwen2.5:7b-instruct` (or `OLLAMA_MODEL`) |
| ComfyUI | Health + minimal T2I (SDXL-Turbo); short T2V (Wan) on stub or `DATAGEN_IT_VIDEO=true` |
| Kokoro | Health + TTS WAV bytes (`RIFF` header) |
| Orchestrator small run | 1 journalist + 1 article via HTTP when `gotham-web` + Ollama are up; image/audio skipped if that helper is down; video always skipped; created docs deleted afterwards |

---

## 5. Profile & skip policy

| Situation | Behavior |
|-----------|----------|
| Unit tests | **Never** skip for missing ES/ImageBind/helpers |
| Integration profile deps down | Failsafe tests use JUnit assumptions / `@EnabledIf` so CI stays green; lab run with profile **must** execute when deps are up |
| Secrets missing | GCS live ITs disabled; unit mocks still required |
| M4 CPU slow helpers | Helper ITs may use tiny prompts / single asset; full 15/25/5+5+5 remains a manual/P10 runbook concern |

---

## 6. Definition of done (testing)

- [x] Every production module ships Surefire unit (and MockMvc) coverage for shipped features  
- [x] `mvn test` passes on a clean checkout with placeholders  
- [x] Failsafe profiles exist for **ES**, **ImageBind**, and **datagen helpers** (`it-es` / `it-imagebind` / `it-datagen-helpers`)  
- [x] P9-T03 documented and green on the lab (`it-es` 15 ITs, `it-imagebind` 3 ITs); P10-T06 profile wired (ITs skip when helpers are down)  
- [x] Agents record test commands in task `notes` when closing tasks  

---

## 7. Mapping to implementation tasks

| Requirement | Where enforced |
|-------------|----------------|
| Test harness in parent POM | **P0-T01** |
| Unit tests with each feature | Agent rule + each coding task **Verification** |
| ES + ImageBind + web IT suite | **P9-T03** |
| Helper + datagen IT suite | **P10-T06** |
