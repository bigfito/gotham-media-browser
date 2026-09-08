# Gotham News & Media Browser — Implementation Plan

**Audience:** AI coding agents (Claude Code, Google Antigravity, Cursor, etc.) and humans  
**Companion state file:** [`implementation-state.md`](./implementation-state.md) — **update after every task**  
**Canonical architecture:** [`architecture-end-to-end.md`](./architecture-end-to-end.md)  
**Package root:** `gotham-news-media-browser/`  
**Decisions locked:** 2026-09-07 (see §12)

---

## 0. Agent operating rules (read first)

1. **Single source of truth for progress** is [`implementation-state.md`](./implementation-state.md).  
2. Before starting work: open the state file → pick the **lowest-ID** task with status `pending` whose dependencies are all `done`.  
3. Claim it: set status to `in_progress`, set `claimed_by` to your agent/session id, set `started_at` (ISO-8601 UTC).  
4. Implement **only that task** unless the task explicitly says otherwise.  
5. When finished: mark `done`, fill `completed_at`, add a one-line `notes` entry (files touched / commands run).  
6. If blocked: set `blocked`, write the blocker in `notes`, do **not** invent live credential values; use placeholders from examples.  
7. Do **not** implement features from later phases early (no “while I’m here”).  
8. Do **not** introduce an RDBMS, `/admin`, Elastic `semantic_text`, or auth.  
9. Prefer matching existing mockups under `ui-mockups/` and specs under `docs/`.  
10. After each task: run the task’s **Verification** commands; leave the tree buildable.  
11. **Commit once per task** (message includes task id, e.g. `P3-T02 Journalist list UI`).  
12. **Never** leave users on Whitelabel/stack-trace pages — unexpected failures must use the global error page with a clear reason ([`ui-design-errors.md`](./ui-design-errors.md)).  
13. **`gotham-datagen` is a Java console app** (`main`) — never Spring Boot.  
14. **Datagen helpers** (Ollama, ComfyUI, Kokoro) run **only** as Docker Compose profile `datagen` containers.  
15. **Tests are mandatory:** every coding task adds/updates **unit tests** (backend + frontend/MockMvc). Do not mark `done` until `mvn test` passes for affected modules. Integration suites for ES / ImageBind / helpers are required per [`testing-strategy.md`](./testing-strategy.md) (tasks **P9-T03**, **P10-T06**).  
16. **Compact context at each phase boundary (token-saving gate):** after the **last** task of a phase is marked `done` (phase count reaches N/N), **pause before starting the next phase and compact the context window** so the next phase starts lean. In Claude Code run **`/compact`**; on other harnesses use the equivalent summarize/compaction step, or start a fresh session that re-reads [`implementation-state.md`](./implementation-state.md). Never compact mid-task (only at a phase boundary, with the state file already updated and committed).

### Stack lock (do not change without human approval)

| Item | Value |
|------|--------|
| Language | Java **25** |
| Framework | Spring Boot **4.1.1** |
| Build | Maven **3.9.x** **multi-module** (IntelliJ IDEA Ultimate–friendly) |
| UI | Thymeleaf (server-rendered) |
| Search | Elasticsearch Java API Client (Boot BOM ~9.4.x) |
| Compose | `gotham-web` + `imagebind-service`; P10 profile **`datagen`** = Ollama + ComfyUI + Kokoro **Docker containers (mandatory)** |
| Embeddings | Meta ImageBind **built in-repo**, sync HTTP, **1024-d** |
| Synthetic data | Maven module **`gotham-datagen`** — Java **console** app, not Spring Boot (phase **P10**, last) — see [`synthetic-data-generation.md`](./synthetic-data-generation.md) |
| Testing | JUnit 5 · Surefire unit/MockMvc · Failsafe ITs for **ES**, **ImageBind**, **datagen helpers** — see [`testing-strategy.md`](./testing-strategy.md) |
| Storage | Elastic Cloud Serverless + **public** GCS |
| Config file | `application.properties` (not YAML for secrets/endpoints) |
| Base package | `com.gotham.newsmediabrowser` |

### Maven multi-module layout (IntelliJ)

Open the **parent** `gotham-news-media-browser/pom.xml` in IntelliJ IDEA Ultimate (Maven import). Modules appear as a multi-module project.

```text
gotham-news-media-browser/
├── pom.xml                      # parent (packaging pom), dependencyManagement, Java 25
├── gotham-common/               # shared domain, props, ES/GCS/ImageBind clients
│   └── pom.xml
├── gotham-web/                  # Spring Boot executable + Thymeleaf
│   └── pom.xml                  # depends on gotham-common; spring-boot-maven-plugin
├── gotham-datagen/              # P10 Java console app (NOT Spring Boot; last phase)
│   └── pom.xml                  # jar + Main-Class; depends on gotham-common; HTTP to web + helpers
├── comfyui-service/             # P10 Compose-built CPU ComfyUI (SDXL-Turbo + Wan; NOT a Maven module)
├── imagebind-service/           # Python/Docker ImageBind (NOT a Maven module)
├── secrets/                     # gitignored real secrets; *.example committed
├── docker-compose.yml
├── docs/ · elasticsearch/ · ui-mockups/ · diagrams/
└── AGENTS.md
```

| Module | Artifact | Role |
|--------|----------|------|
| parent | `gotham-news-media-browser` | BOM alignment, plugin versions, module list |
| `gotham-common` | `gotham-common` | Config properties, ES repositories, GCS, ImageBind client, projections |
| `gotham-web` | `gotham-web` | Controllers, Thymeleaf, `SpringBootApplication`, static assets |
| `gotham-datagen` | `gotham-datagen` | **P10** Java **console** app (`main`): generate text/media via helpers → `POST /journalist` & `/article` |

`imagebind-service/` and `comfyui-service/` stay in the same repo for cohesion but are **Compose-built**, not Maven modules.  
**P0** scaffolds `gotham-common` + `gotham-web` only; **P10** adds `gotham-datagen` (console jar, **not** Spring Boot) to the parent module list.

### Credentials & secrets (locked)

**Secrets policy (decided 2026-09-07):** `application.properties` is **committed with safe placeholders only**. Real values are supplied by an **untracked override** — `application-local.properties` (gitignored, imported via `spring.config.import=optional:...`) or environment variables (Spring relaxed binding). Secrets are **never** committed and **never** logged/echoed in health details.

| Secret / param | How it is provided |
|----------------|-------------------|
| Elasticsearch endpoint | Placeholder in committed `application.properties`; **real value** in untracked `application-local.properties` (or env `GOTHAM_ELASTICSEARCH_ENDPOINT`) |
| Elasticsearch API key | Placeholder in committed `application.properties`; **real value** in untracked `application-local.properties` (or env `GOTHAM_ELASTICSEARCH_API_KEY`) |
| GCS bucket / project ids | Placeholder in committed `application.properties`; **real value** in untracked override (or env) |
| GCS service account JSON key | **Secret file** under `secrets/` (e.g. `secrets/gcp-sa.json`); path referenced from properties; **never commit** the real JSON — commit `secrets/gcp-sa.json.example` + `.gitignore` |

Example committed `application.properties` (placeholders + optional local override import):

```properties
# Committed: placeholders only. Real values go in application-local.properties (gitignored) or env.
spring.config.import=optional:file:./application-local.properties
gotham.elasticsearch.endpoint=https://YOUR-ES-ENDPOINT
gotham.elasticsearch.api-key=YOUR_API_KEY
gotham.gcs.project-id=YOUR_GCP_PROJECT
gotham.gcs.bucket=YOUR_PUBLIC_BUCKET
gotham.gcs.credentials-file=secrets/gcp-sa.json
gotham.imagebind.base-url=http://imagebind-service:8081
```

---

## Phase map

```text
P0  Scaffold & agent harness (multi-module Maven)
P1  Config, health, ES client, **global fault-tolerant error pages**
P2  Index bootstrap (mappings → Elastic)
P3  /journalist — list + create + edit form (gotham-journalists master writes)
P4  /article CRUD + journalist cascade-strip delete (needs article repo P4-T02)
P5  GCS + multimedia nested CRUD + HTML5 playback
P6  imagebind-service (in-repo) + write-time embeddings
P7  Public search: landing + /results full-text
P8  Semantic · Hybrid · Vector search
P9  Static smoke fixtures + demo runbook (no generative helpers)
P10 Synthetic data generation (Java console `gotham-datagen` + Docker helpers)  ← LAST
```

Dependency spine: `P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8 → P9 → P10`

**Total tasks:** **42** (see inventory below + [`implementation-state.md`](./implementation-state.md)).  
**Canonical generative design:** [`synthetic-data-generation.md`](./synthetic-data-generation.md).  
**Canonical testing design:** [`testing-strategy.md`](./testing-strategy.md).

**Progress:** P0–P9 + P10-T01 **done** (37/42). Next task **P10-T02**. Live board: [`implementation-state.md`](./implementation-state.md).

### Task inventory (authoritative IDs)

| Phase | Tasks (count) |
|-------|----------------|
| P0 | P0-T01 … P0-T05 (**5**) |
| P1 | P1-T01 … P1-T04 (**4**) |
| P2 | P2-T01 … P2-T02 (**2**) |
| P3 | P3-T01 … P3-T03 (**3**) |
| P4 | P4-T01 … P4-T05 (**5**) |
| P5 | P5-T01 … P5-T03 (**3**) |
| P6 | P6-T01 … P6-T03 (**3**) |
| P7 | P7-T01 … P7-T04 (**4**) |
| P8 | P8-T01 … P8-T03 (**3**) |
| P9 | P9-T01 … P9-T04 (**4**) |
| P10 | P10-T01 … P10-T06 (**6**) |
| **Sum** | **42** |

### Testing cross-cut (all coding tasks)

See [`testing-strategy.md`](./testing-strategy.md).

- **Backend + frontend (Thymeleaf/MockMvc):** ship **unit tests** with every feature task; `mvn test` must pass before marking `done`.  
- **Integration:** Elasticsearch + ImageBind + web → **P9-T03**; datagen helpers (Ollama/ComfyUI/Kokoro) + console orchestrator → **P10-T06**.  

---

## Phase 0 — Scaffold & agent harness

**Goal:** Buildable multi-module Maven project + Compose stubs + agent docs. Importable in IntelliJ IDEA Ultimate.

### P0-T01 — Parent POM + `gotham-common` + `gotham-web` skeleton
- **Create:** Parent `pom.xml` (`packaging` `pom`, modules `gotham-common`, `gotham-web`); both child modules; Java 25; Spring Boot 4.1.1 parent/BOM; package `com.gotham.newsmediabrowser`.  
- **Do:** `gotham-web` has `@SpringBootApplication`, empty `application.properties` with **placeholder** ES/GCS/ImageBind keys (see credentials table); `gotham-common` empty library jar.  
- **Do:** Wire **Surefire** (unit) + **Failsafe** (integration) plugins in parent; JUnit 5 on test classpath for both modules; placeholder `*Test` / empty `src/test/java` layout so `mvn test` succeeds.  
- **Do:** Root `.gitignore` for `**/target/`, `secrets/*.json` (allow `*.example`), IDE files as appropriate.  
- **Don’t:** Business logic; do **not** add `gotham-datagen` yet (P10).  
- **Verification:** From package root: `mvn -q test` and `mvn -q -DskipTests package` succeed; IntelliJ can import parent POM as multi-module.  
- **Depends on:** —

### P0-T02 — Docker Compose skeleton
- **Create:** `docker-compose.yml` building `gotham-web` (context/dockerfile for the Boot module) and `imagebind-service` (placeholder `GET /health` until P6).  
- **Do:** Mount or copy `secrets/` into `gotham-web` as needed; document Compose service names/ports `:8080` / `:8081`.  
- **Don’t:** Add Compose profile `datagen` / `comfyui-service` yet (P10-T02).  
- **Verification:** `docker compose config` validates.  
- **Depends on:** P0-T01

### P0-T03 — Agent entrypoints
- **Create/Update:** `AGENTS.md`, README pointers to plan + state.  
- **Do:** State file task board stays authoritative.  
- **Verification:** Links resolve; all task IDs present in state.  
- **Depends on:** —

### P0-T04 — Shared Thymeleaf layout from mockups
- **Create:** In `gotham-web`: `templates/layout.html` + static CSS ported from `ui-mockups/`; header legends + MIT footer.  
- **Verification:** `GET /` returns 200 with chrome (minimal controller OK).  
- **Depends on:** P0-T01

### P0-T05 — Secrets scaffolding
- **Create:** `secrets/README.md`, `secrets/gcp-sa.json.example`, and `gotham-web/.../application-local.properties.example` (documenting ES/GCS real-value keys); ensure real `secrets/gcp-sa.json` and `application-local.properties` are gitignored.  
- **Do:** Document that operators place the real SA JSON key as a secret file locally / in CI secret store, and real ES/GCS values in `application-local.properties` (or env) — never in the committed `application.properties`.  
- **Verification:** Example files committed; `git check-ignore -v secrets/gcp-sa.json` and `git check-ignore -v application-local.properties` (repo root — kept out of `src/main/resources` so it never bundles into the jar) match ignore rules.  
- **Depends on:** P0-T01

---

## Phase 1 — Config, health, ES client, global fault-tolerant error pages

**Goal:** External config (placeholders + untracked override) drives the ES client; header legends get health; branded error pages cover all endpoints.

### P1-T01 — `@ConfigurationProperties` bound to `application.properties`
- **Create:** In `gotham-common`: properties for elasticsearch endpoint + api-key, gcs.*, imagebind.base-url, media limits.  
- **Do:** Committed `application.properties` holds **placeholders**; real ES/GCS values come from untracked `application-local.properties` (or env). GCS credentials loaded from the **secret file path**. Never log secret values (mask in any health/diagnostic output).  
- **Verification:** Unit test in `gotham-common` or `gotham-web` binds a test `application.properties`.  
- **Depends on:** P0-T01, P0-T05

### P1-T02 — Elasticsearch Java client bean
- **Create:** Client in `gotham-common` (or web config) using endpoint + API key from properties.  
- **Verification:** With real values filled, a **Serverless-supported** call succeeds (cluster `info` / index `exists` — **not** `ping()`/cluster-health); with placeholders, app starts but health reports Unavailable. Never log the API key.  
- **Depends on:** P1-T01

### P1-T03 — Health endpoints for chrome
- **Create:** `GET /api/health/elasticsearch`; check ImageBind `{base}/health`.  
- **Do:** Layout model/fragment for Available/Unavailable.  
- **Verification:** MockMvc or manual: legends flip with dependency state.  
- **Depends on:** P1-T02, P0-T04

### P1-T04 — Global fault tolerance & user error pages
- **Do:** Disable Whitelabel errors. Add Thymeleaf `error.html` (+ optional status-specific templates) matching [`ui-design-errors.md`](./ui-design-errors.md) and `ui-mockups/error.html`.  
- **Do:** `@ControllerAdvice` (and/or `ErrorController`) covering **all** endpoints: map domain + unexpected exceptions to the error view with **HTTP status**, **human reason**, and **reference id**; log full stack server-side only.  
- **Do:** Bounded timeouts on ES / ImageBind / GCS clients; dependency failures → **503** error page naming the service (no secrets).  
- **Do:** Prefer in-form field errors for expected validation; use the error page for unexpected failures.  
- **Verification:** Force 404 (unknown id), 500 (thrown exception), and simulated ES-down path → branded error page with reason + reference id + chrome; no stack trace in HTML.  
- **Depends on:** P0-T04, P1-T01  
- **Spec:** [`ui-design-errors.md`](./ui-design-errors.md)

---

## Phase 2 — Index bootstrap

**Goal:** Both indexes exist with approved mappings.

### P2-T01 — Mapping JSON on classpath
- **Do:** Ship `elasticsearch/*.mapping.json` into `gotham-common` (or `gotham-web`) resources.  
- **Verification:** Classpath resources load in a unit test.  
- **Depends on:** P0-T01

### P2-T02 — Idempotent index bootstrap
- **Create:** Runner/service creating `gotham-journalists` + `gotham-media-browser` if missing.  
- **Do:** Never auto-delete indexes.  
- **Verification:** Against Elastic Cloud with real properties: create then no-op on second boot.  
- **Depends on:** P1-T02, P2-T01

---

## Phase 3 — `/journalist` (master writes only)

**Goal:** `gotham-journalists` master data — domain/repo, list, create. Journalist **edit + cascade-strip delete** are relocated to **P4-T03** because they reindex article documents and require the article repository (**P4-T02**); this keeps every dependency forward-only (ID order = a valid execution order).

### P3-T01 — Journalist domain + repository
- **Create:** In `gotham-common`: model + ES repository (index/get/update/delete/search `from`/`size`).  
- **Do:** Derive `full_name`; ES auto `_id`.  
- **Verification:** Repository tests (WireMock or live ES).  
- **Depends on:** P2-T02

### P3-T02 — Journalist list UI (`GET /journalist`)
- **Do:** Thymeleaf in `gotham-web`; pagination `size` ∈ {25,50,100}; match `ui-mockups/journalist.html`.  
- **Verification:** List renders with layout chrome; empty and non-empty states OK.  
- **Depends on:** P3-T01, P0-T04, P1-T04

### P3-T03 — Journalist create
- **Do:** `GET /journalist/new`, `POST /journalist`.  
- **Verification:** Creates doc; `_id` on edit.  
- **Depends on:** P3-T02

---

## Phase 4 — `/article` CRUD + journalist edit/cascade-strip delete (no media binaries yet)

**Note:** Journalist **edit + cascade-strip delete** live here (**P4-T03**) because they reindex article documents and depend on the article repository (**P4-T02**). Placing them after P4-T02 keeps every dependency forward-only, so the "lowest-ID pending task whose deps are done" rule can be followed without back-tracking.

### P4-T01 — Article domain + projection helpers
- **Create:** In `gotham-common`; enums for status + `contribution_role`; projection builders.  
- **Verification:** Unit tests.  
- **Depends on:** P3-T01

### P4-T02 — Article repository
- **Do:** CRUD + list filters + **query articles by nested journalist_id** (for cascade-strip).  
- **Verification:** Index/get/update/delete + nested journalist query.  
- **Depends on:** P4-T01, P2-T02

### P4-T03 — Journalist edit + cascade-strip delete
- **Do:** `GET/POST /journalist/{id}` for updates; on update, reindex nested byline snapshots in every article nesting that `journalist_id`.  
- **Do:** `POST /journalist/{id}/delete` **cascade-strips**: find articles with nested `journalists.journalist_id` = id (via the P4-T02 nested query) → remove nested element → rebuild journalist projections → reindex articles → delete journalist master doc.  
- **Verification:** Update persists and refreshes article snapshots; after delete the journalist is gone and previously linked articles no longer nest that id; failures use the branded error page.  
- **Depends on:** P4-T02, P3-T03

### P4-T04 — Article list + create/edit (text + metadata + bylines)
- **Do:** Port forms without media upload (or disabled). Nest journalist snapshots on save. Match `ui-mockups/article*.html` (media fields deferred to P5).  
- **Verification:** Create/update with ≥1 journalist; validation errors stay in-form.  
- **Depends on:** P4-T02, P3-T03, P0-T04, P1-T04

### P4-T05 — Article delete
- **Do:** `POST /article/{id}/delete` removes ES doc (GCS cleanup in P5-T03).  
- **Verification:** Doc gone; unknown id → branded 404 error page.  
- **Depends on:** P4-T04

---

## Phase 5 — GCS + multimedia

### P5-T01 — GCS storage service
- **Do:** In `gotham-common`: upload/delete using **secret** SA JSON from `gotham.gcs.credentials-file`; bucket from properties; public HTTPS `storage_uri`.  
- **Do:** Enforce limits: IMAGE **10 MiB**; AUDIO **20 MiB** / **5 minutes**; VIDEO **50 MiB** / **90 seconds**.  
- **Verification:** With real secret file + bucket props, public GET of uploaded object works; over-limit rejected.  
- **Depends on:** P1-T01, P0-T05

### P5-T02 — Multimedia on article create/update
- **Do:** App-assigned `multimedia_element_id`; nest metadata; projections; HTML5 on edit (`<img>` / `<audio controls>` / `<video controls>`). Support multiple files per type (needed later by datagen 5+5+5).  
- **Verification:** Image/audio/video fixtures play in edit UI; vectors deferred to P6.  
- **Depends on:** P5-T01, P4-T04

### P5-T03 — Remove media + article delete cleans GCS
- **Do:** Remove nested media element + delete GCS object; on article delete, delete all related GCS objects then ES doc.  
- **Verification:** Objects removed from bucket; orphaned URIs do not remain after successful delete.  
- **Depends on:** P5-T02

---

## Phase 6 — ImageBind (in-repo) + embeddings

### P6-T01 — In-repo `imagebind-service`
- **Do:** Build Meta ImageBind OSS wrapper **in this repo** under `imagebind-service/` (Dockerfile + app): embed text/image/audio/video → float[1024]; `GET /health`. CPU default.  
- **Verification:** `docker compose up imagebind-service` → health 200; embed text length 1024.  
- **Depends on:** P0-T02

### P6-T02 — Java ImageBind client + stub mode
- **Do:** In `gotham-common`; `gotham.imagebind.stub=true` for CI without model weights.  
- **Verification:** Stub unit tests; live optional.  
- **Depends on:** P1-T01

### P6-T03 — Embed on article write
- **Do:** `article_embedding` + per-asset `asset_vector` (1024).  
- **Verification:** Indexed vectors length 1024.  
- **Depends on:** P6-T02, P5-T02

---

## Phase 7 — Public search (full-text)

### P7-T01 — Landing `GET /`
- **Create:** Dual-panel landing matching [`frontend-information-architecture.md`](./frontend-information-architecture.md) + `ui-mockups/index.html`.  
- **Do:** Article methods Full-text · Semantic · Hybrid; multimedia Full-text · Semantic · Hybrid · Vector; query params per IA.  
- **Verification:** Forms navigate to `/results` with correct `entity`/`mode`/filters.  
- **Depends on:** P0-T04, P1-T04

### P7-T02 — Article FTS service
- **Do:** Implement cookbook §4 ([`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md)): `multi_match`, field remap, filters, journalist nested filter, `from`/`size`, `track_total_hits`.  
- **Verification:** Sample queries return expected hits; pagination `size` ∈ {25,50,100}.  
- **Depends on:** P4-T04, P2-T02

### P7-T03 — Multimedia FTS + inner_hits
- **Do:** Implement cookbook §7: nested BM25 + `inner_hits` for asset cards.  
- **Verification:** Nested hits surface media cards with `storage_uri` playback.  
- **Depends on:** P5-T02

### P7-T04 — Results Thymeleaf pages
- **Create:** `/results` for articles + multimedia matching `ui-mockups/results-*.html` (filters, sort, pagination).  
- **Do:** Wire P7-T02/T03; status + journalist filters on article FTS.  
- **Verification:** Manual/MockMvc: filter + page round-trips preserve query params.  
- **Depends on:** P7-T01, P7-T02, P7-T03

---

## Phase 8 — Semantic · Hybrid · Vector

**Goal:** Complete search mode matrix per [`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md).

### P8-T01 — Semantic kNN
- **Do:** Cookbook §5 (articles) + §8 (multimedia nested kNN + `inner_hits`); text → ImageBind → `query_vector` length 1024.  
- **Verification:** Semantic results return; vector length enforced.  
- **Depends on:** P6-T03, P7-T04

### P8-T02 — Hybrid RRF
- **Do:** Cookbook §6 / §9 using `retriever.rrf`; shared filters; document pagination approach.  
- **Verification:** Hybrid mode returns fused ranking; filters apply to both legs.  
- **Depends on:** P8-T01, P7-T02

### P8-T03 — Multimedia vector (file) search
- **Do:** Cookbook §10: multipart → ImageBind → same nested kNN as semantic; reject article `mode=vector` with **HTTP 400** branded error page.  
- **Verification:** File upload vector search works; article vector mode → error page with reason.  
- **Depends on:** P8-T01, P7-T01, P1-T04

---

## Phase 9 — Demo smoke + static fixtures

**Goal:** CI / low-resource demo without GPU generative models. Full synthetic load is **P10**.

### P9-T01 — Static seed fixtures
- **Create:** Tiny committed fixtures (e.g. under `gotham-web/src/test/resources/fixtures/` or `docs/demo/fixtures/`) — a few journalists + articles; optional tiny IMAGE/AUDIO/VIDEO samples within media limits.  
- **Do:** Document how to load via `/journalist` and `/article` (curl or a small profile) — **not** via `gotham-datagen`.  
- **Don’t:** Depend on Ollama / ComfyUI / Kokoro.  
- **Verification:** Seed path documented; fixtures present and within size limits.  
- **Depends on:** P6-T03, P5-T02

### P9-T02 — Demo runbook + smoke script
- **Create:** Runbook covering Compose up → bootstrap indexes → static seed → exercise search modes + CRUD smoke.  
- **Do:** Script or checklist that fails clearly on health/dependency errors.  
- **Verification:** Runbook steps executable against a configured lab; smoke exits non-zero on failure.  
- **Depends on:** P8-T03, P7-T04, P1-T03, P9-T01

### P9-T03 — Integration tests: Elasticsearch + ImageBind + web
- **Create:** Failsafe suites (`*IT`) + Maven profiles `it-es` and `it-imagebind` per [`testing-strategy.md`](./testing-strategy.md).  
- **Do:** Cover index bootstrap, journalist/article CRUD + cascade-strip, FTS smoke, ImageBind health + 1024-d embed, write-path embeddings when service up; MockMvc/IT coverage for critical `/journalist`, `/article`, `/results` flows against real ES (or documented assumption skip).  
- **Don’t:** Require datagen helpers here (that is P10-T06).  
- **Verification:** `mvn -Pit-es verify` and `mvn -Pit-imagebind verify` pass on the lab with deps up (run through the `verify` phase so the reactor builds `gotham-common` first); when deps absent, tests are skipped via assumptions (not red failures). Document operator commands in runbook.  
- **Depends on:** P9-T02, P6-T03, P5-T02, P8-T03, P4-T03, P4-T05

### P9-T04 — Hardening sync pass
- **Do:** Align README/AGENTS/mockups with shipped behavior; confirm error pages still cover all routes; confirm testing docs match shipped Surefire/Failsafe layout.  
- **Don’t:** Start `gotham-datagen` (that is P10).  
- **Verification:** Checklist in plan DoD (excluding P10) can be ticked for a smoke demo; `mvn test` green.  
- **Depends on:** P9-T03

---

## Phase 10 — Synthetic data generation (**last phase**)

**Goal:** Independent **Java console** app `gotham-datagen` (plain `main`, **not** Spring Boot) generates realistic journalists + articles (with IMAGE / AUDIO / VIDEO) and loads them **only through** live `POST /journalist` and `POST /article`.  
**Spec:** [`synthetic-data-generation.md`](./synthetic-data-generation.md).

### Helper services (locked — Docker containers on MacBook Pro M4 · 32 GB · no NVIDIA)

| Modality | Model | Size / quant | Container image |
|----------|-------|--------------|-----------------|
| Text | **Qwen 2.5 7B-Instruct** | 7B (Q4_K_M) | `ollama/ollama:latest` |
| Image | **SDXL-Turbo** | few-step SDXL | in-repo **`comfyui-service/`** (CPU, multi-arch) |
| Audio / voice | **Kokoro-82M** | 82M | `ghcr.io/remsky/kokoro-fastapi-cpu` |
| Video | **Wan2.1 (T2V-1.3B)** | 1.3B | same **`comfyui-service/`** |

**Mandatory:** helpers run **only** as Docker Compose profile `datagen` services (CPU inside Docker Desktop on Mac). No native-host helper installs as the prototype path. Do **not** require CUDA `yanwk/comfyui-boot`.

### Locked default volumes

| Item | Count |
|------|------:|
| Journalists | **15** |
| Articles | **25** |
| IMAGE / AUDIO / VIDEO per article | **5 / 5 / 5** |
| Video clip length | **5 seconds** each |
| Total media assets | **375** |

Skip flags for IMAGE/AUDIO/VIDEO when helpers are unavailable; **text (Qwen 7B) is required**. Full runs on M4 Docker CPU are long (especially video) — document overnight expectation in the runbook.

### P10-T01 — Parent POM + Java console `gotham-datagen` skeleton
- **Create:** `gotham-datagen/` as a **plain Java console** module (`public static void main`); package `com.gotham.newsmediabrowser.datagen`; add module to parent `pom.xml`.  
- **Do:** Runnable jar via `Main-Class` manifest (shade optional); placeholder properties/CLI for `gotham.datagen.*` URLs/volumes/model id (`qwen2.5:7b-instruct`, 15 / 25 / 5+5+5 / 5 s). Use Java HTTP client (or lightweight lib) — **no** Spring Boot dependencies on this module.  
- **Do:** JUnit 5 test skeleton so `mvn -pl gotham-datagen test` passes (e.g. CLI help / property defaults).  
- **Don’t:** Add `@SpringBootApplication` / `spring-boot-maven-plugin` to `gotham-datagen`; don’t embed datagen inside `gotham-web`; don’t call generative APIs yet.  
- **Verification:** `mvn -pl gotham-datagen -am test` and `package` succeed; `java -jar … --help` (or equivalent) exits 0; IntelliJ shows a non-Boot application module.  
- **Depends on:** P0-T01, P9-T04

### P10-T02 — Compose profile `datagen` (Ollama · ComfyUI · Kokoro containers)
- **Create/Update:** `docker-compose.yml` profile **`datagen`** with services: `ollama` (`ollama/ollama:latest`), `comfyui` (**build** `./comfyui-service` CPU multi-arch with SDXL-Turbo + Wan2.1 workflows), `kokoro` (`ghcr.io/remsky/kokoro-fastapi-cpu`); named volumes for models/checkpoints; document first-run `ollama pull qwen2.5:7b-instruct`.  
- **Do:** Keep `gotham-web` + `imagebind-service` always-on; helpers are **mandatory containers** for P10 (not optional native installs).  
- **Don’t:** Depend on CUDA-only images; don’t document native Ollama/ComfyUI as the supported path.  
- **Verification:** `docker compose --profile datagen config` validates; `docker compose --profile datagen up -d` brings helpers to healthy ports `:11434` / `:8188` / `:8880` on M4 Docker Desktop (or CI mock documented).  
- **Depends on:** P0-T02, P10-T01

### P10-T03 — Helper HTTP clients (Qwen 7B · SDXL-Turbo · Kokoro · Wan)
- **Create:** Clients in `gotham-datagen` for Ollama chat (`qwen2.5:7b-instruct`), ComfyUI T2I (SDXL-Turbo) + T2V (Wan2.1 **5 s** clips), Kokoro TTS; health-check each container before use.  
- **Do:** Enforce product media caps and synthetic video target **5 s**; tolerate slow **CPU-in-container** generation (timeouts documented, not silent failures).  
- **Do:** **Unit tests** with mocked HTTP servers for each client (success + down/error paths).  
- **Verification:** `mvn -pl gotham-datagen test` green; clients fail clearly when containers are down.  
- **Depends on:** P10-T02

### P10-T04 — Orchestrator → `POST /journalist` & `POST /article`
- **Do:** Pipeline: Qwen 7B → **15** journalists → **25** articles (+ captions) → **5** images + **5** audios + **5** videos per article → multipart/form matching CRUD contracts → collect ids; print summary with reasons/reference ids on failures.  
- **Do:** Unit tests for orchestration with mocked helpers + mocked web API (skip flags, abort-if-Ollama-down).  
- **Don’t:** Write directly to Elasticsearch or GCS; don’t run inside the `gotham-web` process.  
- **Verification:** `mvn -pl gotham-datagen test` green; against running `gotham-web` + **Docker** helpers (manual or P10-T06): creates docs; media counts match defaults; skip flags honored.  
- **Depends on:** P10-T03, P3-T03, P4-T04, P5-T02, P6-T03

### P10-T05 — Datagen runbook + verification report
- **Create:** Operator runbook for **M4 32 GB + Docker Desktop**: `docker compose --profile datagen up`, model pulls, Docker memory settings, expected volumes **15 / 25 / 5+5+5**, overnight notes for CPU video, **IT profile** `it-datagen-helpers`.  
- **Do:** Sample: `mvn -pl gotham-datagen -am package && java -jar gotham-datagen/target/gotham-datagen-*.jar` (or `exec:java` with `DatagenMain`).  
- **Verification:** Runbook completes on the M4 lab with containers **or** documents skip-flag path.  
- **Depends on:** P10-T04

### P10-T06 — Integration tests: datagen helpers + orchestrator
- **Create:** Failsafe profile `it-datagen-helpers` per [`testing-strategy.md`](./testing-strategy.md).  
- **Do:** Health + minimal generation against Docker **Ollama**, **ComfyUI**, **Kokoro**; small orchestrator run (reduced counts OK for IT) posting through live `/journalist` and `/article` when `gotham-web` is up.  
- **Verification:** `mvn -Pit-datagen-helpers failsafe:integration-test failsafe:verify` passes on lab with `datagen` profile up; assumptions skip cleanly when containers absent. State file P10 closable.  
- **Depends on:** P10-T05, P10-T02, P9-T03

_Ticked for the P0–P9 smoke demo (P9-T04 hardening sync). P10 items stay open._

- [x] IntelliJ opens parent POM as multi-module (`gotham-common`, `gotham-web`; `gotham-datagen` after P10) — reactor builds green on JDK 25  
- [x] Compose brings up `gotham-web` + in-repo `imagebind-service` (`docker compose config` valid; app + stub embedder run in P9-T03)  
- [x] ES endpoint + API key supplied (placeholders committed; real values in untracked `application-local.properties` / env)  
- [x] GCS SA JSON supplied as secret file; bucket props in properties  
- [x] Both indexes bootstrapped (`IndexBootstrapper`; `IndexBootstrapIT` green)  
- [x] `/journalist` + `/article` CRUD; journalist delete **cascade-strips** (`JournalistServiceIT` green)  
- [x] Search modes per capability matrix (full-text · semantic · hybrid · multimedia vector; article `mode=vector` → 400)  
- [x] Header health legends + MIT footer  
- [x] **Fault tolerant UX:** unexpected errors on any endpoint show branded error page with reason (no Whitelabel/stack dumps) — verified live (smoke: 400 + 503 branded)  
- [x] **Unit tests** for backend + frontend (MockMvc) with `mvn test` green (199)  
- [x] **Integration tests** for Elasticsearch + ImageBind (**P9-T03** — `it-es` 15 ITs, `it-imagebind` 3 ITs green on the lab); datagen helpers (**P10-T06**) pending  
- [x] P9 static smoke path works without generative helpers (`docs/demo/` fixtures + `seed.sh` + `smoke.sh`; smoke passed text-only)  
- [ ] **P10:** `docker compose --profile datagen` runs Ollama + ComfyUI + Kokoro containers; **Java console** `gotham-datagen` loads **15** / **25** / **5+5+5** via HTTP CRUD using **Qwen 7B / SDXL-Turbo / Kokoro / Wan 1.3B** (or documented skip flags)  
- [ ] State file tasks completed (through P9; **P10** remains)  

---

## 12. Locked human decisions (2026-09-07)

| # | Decision |
|---|----------|
| Q1 | ES **endpoint + API key**: placeholders in committed `application.properties`; **real values in untracked `application-local.properties` (or env)** — revised 2026-09-07 from the original "hardcoded" to avoid committing secrets |
| Q2 | GCS ids/bucket: placeholders in `application.properties` (real values in untracked override); **SA JSON key file treated as secret** (`secrets/gcp-sa.json`) |
| Q3 | ImageBind **built in-repo** |
| Q4 | Layout: Maven modules + `imagebind-service/` in same package — **yes** |
| Q5 | Journalist delete: **cascade-strip** (+ reindex affected articles) |
| Q6 | Commits: **one per task** |
| Q7 | Package: **`com.gotham.newsmediabrowser`** |
| Q8 | No extra Antigravity/Claude task format beyond Markdown plan + state |
| Extra | **Multi-module Maven** for IntelliJ IDEA Ultimate (`gotham-common` + `gotham-web` + parent; **`gotham-datagen` console module in P10**) |
| Extra | **Fault-tolerant UX:** global error pages with reason on all endpoints ([`ui-design-errors.md`](./ui-design-errors.md)) |
| Extra | **Synthetic data (last phase P10):** Java **console** app `gotham-datagen` (**not** Spring Boot); load only via `/journalist` & `/article` |
| Extra | **Datagen volumes:** 15 journalists · 25 articles · 5 IMAGE + 5 AUDIO + 5 VIDEO (5 s) per article; P9 static seed kept |
| Extra | **Lab hardware:** MacBook Pro M4 · 32 GB · no NVIDIA; lighter models **Qwen 2.5 7B**, **SDXL-Turbo**, **Kokoro-82M**, **Wan2.1 1.3B** |
| Extra | **Helpers mandatory as Docker containers** (Compose profile `datagen`: Ollama + `comfyui-service` + Kokoro CPU) |
| Extra | **Testing:** unit tests for all backend + frontend (MockMvc); integration tests for ES, ImageBind, and datagen helpers ([`testing-strategy.md`](./testing-strategy.md)) |
