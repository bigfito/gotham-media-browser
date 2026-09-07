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

### Stack lock (do not change without human approval)

| Item | Value |
|------|--------|
| Language | Java **25** |
| Framework | Spring Boot **4.1.1** |
| Build | Maven **3.9.x** **multi-module** (IntelliJ IDEA Ultimate–friendly) |
| UI | Thymeleaf (server-rendered) |
| Search | Elasticsearch Java API Client (Boot BOM ~9.4.x) |
| Compose | `gotham-web` + `imagebind-service` |
| Embeddings | Meta ImageBind **built in-repo**, sync HTTP, **1024-d** |
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

`imagebind-service/` stays in the same repo for cohesion but is **Compose-built**, not a Maven module.

### Credentials & secrets (locked)

| Secret / param | How it is provided |
|----------------|-------------------|
| Elasticsearch endpoint | **Hardcoded** in `gotham-web/.../application.properties` as a global property |
| Elasticsearch API key | **Hardcoded** in the same `application.properties` |
| GCS bucket / project ids | **Hardcoded** in `application.properties` |
| GCS service account JSON key | **Secret file** under `secrets/` (e.g. `secrets/gcs-sa.json`); path referenced from properties; **never commit** the real JSON — commit `secrets/gcs-sa.json.example` + `.gitignore` |

Example property names (agents must use these unless renaming everywhere):

```properties
gotham.elasticsearch.endpoint=https://YOUR-ES-ENDPOINT
gotham.elasticsearch.api-key=YOUR_API_KEY
gotham.gcs.project-id=YOUR_GCP_PROJECT
gotham.gcs.bucket=YOUR_PUBLIC_BUCKET
gotham.gcs.credentials-file=secrets/gcs-sa.json
gotham.imagebind.base-url=http://imagebind-service:8081
```

---

## Phase map

```text
P0  Scaffold & agent harness (multi-module Maven)
P1  Config, health, ES client plumbing
P2  Index bootstrap (mappings → Elastic)
P3  /journalist CRUD (cascade-strip on delete)
P4  /article CRUD (story + metadata + bylines, no media yet)
P5  GCS + multimedia nested CRUD + HTML5 playback
P6  imagebind-service (in-repo) + write-time embeddings
P7  Public search: landing + /results full-text
P8  Semantic · Hybrid · Vector search
P9  Seed data, E2E demo script, hardening
```

Dependency spine: `P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8 → P9`

---

## Phase 0 — Scaffold & agent harness

**Goal:** Buildable multi-module Maven project + Compose stubs + agent docs. Importable in IntelliJ IDEA Ultimate.

### P0-T01 — Parent POM + `gotham-common` + `gotham-web` skeleton
- **Create:** Parent `pom.xml` (`packaging` `pom`, modules `gotham-common`, `gotham-web`); both child modules; Java 25; Spring Boot 4.1.1 parent/BOM; package `com.gotham.newsmediabrowser`.  
- **Do:** `gotham-web` has `@SpringBootApplication`, empty `application.properties` with **placeholder** ES/GCS/ImageBind keys (see credentials table); `gotham-common` empty library jar.  
- **Do:** Root `.gitignore` for `**/target/`, `secrets/*.json` (allow `*.example`), IDE files as appropriate.  
- **Don’t:** Business logic.  
- **Verification:** From package root: `mvn -q -DskipTests package` succeeds; IntelliJ can import parent POM as multi-module.  
- **Depends on:** —

### P0-T02 — Docker Compose skeleton
- **Create:** `docker-compose.yml` building `gotham-web` (context/dockerfile for the Boot module) and `imagebind-service` (placeholder `GET /health` until P6).  
- **Do:** Mount or copy `secrets/` into `gotham-web` as needed; document Compose service names/ports `:8080` / `:8081`.  
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
- **Create:** `secrets/README.md`, `secrets/gcs-sa.json.example`, ensure real `secrets/gcs-sa.json` is gitignored.  
- **Do:** Document that operators place the real SA JSON key as a secret file locally / in CI secret store.  
- **Verification:** Example file committed; `git check-ignore -v secrets/gcs-sa.json` matches ignore rule (create empty ignored file in test or assert pattern).  
- **Depends on:** P0-T01

---

## Phase 1 — Config, health, ES client

**Goal:** Hardcoded properties drive ES client; header legends get health.

### P1-T01 — `@ConfigurationProperties` bound to `application.properties`
- **Create:** In `gotham-common`: properties for elasticsearch endpoint + api-key, gcs.*, imagebind.base-url, media limits.  
- **Do:** Values come from **hardcoded** `gotham-web` `application.properties` (placeholders until human pastes real values). GCS credentials loaded from **secret file path**.  
- **Verification:** Unit test in `gotham-common` or `gotham-web` binds a test `application.properties`.  
- **Depends on:** P0-T01, P0-T05

### P1-T02 — Elasticsearch Java client bean
- **Create:** Client in `gotham-common` (or web config) using endpoint + API key from properties.  
- **Verification:** With real properties filled, ping/info succeeds; with placeholders, app starts but health reports Unavailable.  
- **Depends on:** P1-T01

### P1-T03 — Health endpoints for chrome
- **Create:** `GET /api/health/elasticsearch`; check ImageBind `{base}/health`.  
- **Do:** Layout model/fragment for Available/Unavailable.  
- **Verification:** MockMvc or manual: legends flip with dependency state.  
- **Depends on:** P1-T02, P0-T04

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

## Phase 3 — `/journalist` CRUD

**Goal:** Full CRUD on `gotham-journalists`. **Delete = cascade-strip.**

### P3-T01 — Journalist domain + repository
- **Create:** In `gotham-common`: model + ES repository (index/get/update/delete/search `from`/`size`).  
- **Do:** Derive `full_name`; ES auto `_id`.  
- **Verification:** Repository tests (WireMock or live ES).  
- **Depends on:** P2-T02

### P3-T02 — Journalist list UI (`GET /journalist`)
- **Do:** Thymeleaf in `gotham-web`; pagination `size` ∈ {25,50,100}.  
- **Verification:** List renders.  
- **Depends on:** P3-T01, P0-T04

### P3-T03 — Journalist create
- **Do:** `GET /journalist/new`, `POST /journalist`.  
- **Verification:** Creates doc; `_id` on edit.  
- **Depends on:** P3-T02

### P3-T04 — Journalist edit + cascade-strip delete
- **Do:** `GET/POST /journalist/{id}` for updates.  
- **Do:** `POST /journalist/{id}/delete` **cascade-strips**: find articles with nested `journalists.journalist_id` = id → remove nested element → rebuild journalist projections → reindex articles → delete journalist master doc.  
- **Verification:** After delete, journalist is gone; previously linked articles no longer nest that id.  
- **Depends on:** P3-T03, P4-T02  

---

## Phase 4 — `/article` CRUD (no media binaries yet)

### P4-T01 — Article domain + projection helpers
- **Create:** In `gotham-common`; enums for status + `contribution_role`; projection builders.  
- **Verification:** Unit tests.  
- **Depends on:** P3-T01

### P4-T02 — Article repository
- **Do:** CRUD + list filters + **query articles by nested journalist_id** (for cascade-strip).  
- **Verification:** Index/get/update/delete + nested journalist query.  
- **Depends on:** P4-T01, P2-T02

### P4-T03 — Article list + create/edit (text + metadata + bylines)
- **Do:** Port forms without media upload (or disabled). Nest journalist snapshots on save.  
- **Verification:** Create/update with ≥1 journalist.  
- **Depends on:** P4-T02, P3-T03, P0-T04

### P4-T04 — Article delete
- **Do:** Remove ES doc (GCS cleanup in P5).  
- **Verification:** Doc gone.  
- **Depends on:** P4-T03

---

## Phase 5 — GCS + multimedia

### P5-T01 — GCS storage service
- **Do:** In `gotham-common`: upload/delete using **secret** SA JSON from `gotham.gcs.credentials-file`; bucket from properties; public HTTPS `storage_uri`.  
- **Do:** Enforce size limits (IMAGE 10 MiB, AUDIO 20 MiB, VIDEO 50 MiB).  
- **Verification:** With real secret file + bucket props, public GET of uploaded object works.  
- **Depends on:** P1-T01, P0-T05

### P5-T02 — Multimedia on article create/update
- **Do:** App `multimedia_element_id`; nest metadata; projections; HTML5 on edit.  
- **Verification:** Image/audio/video fixtures play in edit UI.  
- **Depends on:** P5-T01, P4-T03

### P5-T03 — Remove media + article delete cleans GCS
- **Verification:** Objects removed.  
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
- **Verification:** Forms post/get to `/results` with IA params.  
- **Depends on:** P0-T04

### P7-T02 — Article FTS service
- **Do:** Field remap `section`→`section.text`, etc.; filters; pagination.  
- **Depends on:** P4-T03, P2-T02

### P7-T03 — Multimedia FTS + inner_hits
- **Depends on:** P5-T02

### P7-T04 — Results Thymeleaf pages
- **Depends on:** P7-T01, P7-T02, P7-T03

---

## Phase 8 — Semantic · Hybrid · Vector

### P8-T01 — Semantic kNN
- **Depends on:** P6-T03, P7-T04

### P8-T02 — Hybrid RRF
- **Depends on:** P8-T01, P7-T02

### P8-T03 — Multimedia vector (file) search
- **Depends on:** P8-T01, P7-T01

---

## Phase 9 — Demo readiness

### P9-T01 — Seed profile
- **Depends on:** P6-T03, P5-T02

### P9-T02 — Demo runbook + smoke script
- **Depends on:** P8-T03, P7-T04, P1-T03

### P9-T03 — Final sync pass
- **Depends on:** P9-T02

---

## Definition of Done (prototype)

- [ ] IntelliJ opens parent POM as multi-module (`gotham-common`, `gotham-web`)  
- [ ] Compose brings up `gotham-web` + in-repo `imagebind-service`  
- [ ] ES endpoint + API key supplied via `application.properties`  
- [ ] GCS SA JSON supplied as secret file; bucket props in `application.properties`  
- [ ] Both indexes bootstrapped  
- [ ] `/journalist` + `/article` CRUD; journalist delete **cascade-strips**  
- [ ] Search modes per capability matrix  
- [ ] Header health legends + MIT footer  
- [ ] State file tasks completed  

---

## 12. Locked human decisions (2026-09-07)

| # | Decision |
|---|----------|
| Q1 | ES **endpoint + API key hardcoded** in backend `application.properties` |
| Q2 | GCS ids/bucket hardcoded in `application.properties`; **SA JSON key file treated as secret** (`secrets/`) |
| Q3 | ImageBind **built in-repo** |
| Q4 | Layout: Maven modules + `imagebind-service/` in same package — **yes** |
| Q5 | Journalist delete: **cascade-strip** (+ reindex affected articles) |
| Q6 | Commits: **one per task** |
| Q7 | Package: **`com.gotham.newsmediabrowser`** |
| Q8 | No extra Antigravity/Claude task format beyond Markdown plan + state |
| Extra | **Multi-module Maven** for IntelliJ IDEA Ultimate (`gotham-common` + `gotham-web` + parent) |
