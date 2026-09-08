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

# Integration (profiles; skip gracefully when deps unavailable)
mvn -q -Pit-es failsafe:integration-test failsafe:verify
mvn -q -Pit-imagebind failsafe:integration-test failsafe:verify
mvn -q -Pit-datagen-helpers failsafe:integration-test failsafe:verify
```

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

---

## 4. Integration test suites

**Already in tree (env-gated, not Failsafe profiles):** `*IT.java` in `gotham-common` use JUnit `assumeTrue` so they skip when secrets/deps are missing. Default Surefire does **not** include `*IT` (`mvn test` stays unit + `*Test`). Run a live class with e.g. `mvn -pl gotham-common test -Dtest=ArticleFullTextServiceIT`. Failsafe profiles `it-es` / `it-imagebind` / `it-datagen-helpers` remain **P9 / P10**.

| Class | Gate | Covers |
|-------|------|--------|
| `JournalistRepositoryIT` | `ES_ENDPOINT` + `ES_API_KEY` | Live journalist CRUD |
| `ArticleRepositoryIT` | same | Live article CRUD / nest |
| `ArticleFullTextServiceIT` | same | Cookbook §4 FTS |
| `MultimediaFullTextServiceIT` | same | Cookbook §7 nested FTS + inner_hits |
| `HttpImageBindClientIT` | `IMAGEBIND_BASE_URL` | Live 1024-d embed |
| `GcsStorageServiceIT` | GCS secret / env | Live public object put |

### 4.1 Elasticsearch (`it-es`) — task **P9-T03** (and earlier repo ITs as built)

| Case | Intent |
|------|--------|
| Index bootstrap | Idempotent create of `gotham-journalists` + `gotham-media-browser` |
| Journalist CRUD | Index/get/update/delete against live ES |
| Article CRUD + nest | Bylines + projections persisted |
| Cascade-strip | Delete journalist removes nested byline from articles |
| FTS / filters | Article + multimedia queries from cookbook smoke set |
| kNN / hybrid (when embeddings present) | Semantic/hybrid smoke with 1024-d vectors |

### 4.2 ImageBind (`it-imagebind`) — task **P9-T03** / **P6** verification

| Case | Intent |
|------|--------|
| Health | `GET /health` → 200 |
| Embed text | Response length **1024** |
| Embed image/audio/video | Fixture bytes → length **1024** |
| Write path | Article save populates `article_embedding` + `asset_vector` when service up |

### 4.3 Datagen helpers (`it-datagen-helpers`) — task **P10-T06**

| Case | Intent |
|------|--------|
| Ollama | Health + chat completion with `qwen2.5:7b-instruct` (or documented test model) |
| ComfyUI | Health + minimal T2I (SDXL-Turbo) and short T2V (Wan) or skip flags documented |
| Kokoro | Health + TTS bytes returned |
| Orchestrator dry/small run | Console app posts ≥1 journalist + ≥1 article via HTTP when helpers + `gotham-web` are up (may use reduced counts for IT) |

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

- [ ] Every production module ships Surefire unit (and MockMvc) coverage for shipped features  
- [ ] `mvn test` passes on a clean checkout with placeholders  
- [ ] Failsafe profiles exist for **ES**, **ImageBind**, and **datagen helpers**  
- [ ] P9-T03 and P10-T06 documented and green on the lab (or assumptions documented with operator runbook)  
- [ ] Agents record test commands in task `notes` when closing tasks  

---

## 7. Mapping to implementation tasks

| Requirement | Where enforced |
|-------------|----------------|
| Test harness in parent POM | **P0-T01** |
| Unit tests with each feature | Agent rule + each coding task **Verification** |
| ES + ImageBind + web IT suite | **P9-T03** |
| Helper + datagen IT suite | **P10-T06** |
