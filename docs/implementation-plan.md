# Gotham News & Media Browser — Implementation Plan

**Audience:** AI coding agents (Claude Code, Google Antigravity, Cursor, etc.) and humans  
**Companion state file:** [`implementation-state.md`](./implementation-state.md) — **update after every task**  
**Canonical architecture:** [`architecture-end-to-end.md`](./architecture-end-to-end.md)  
**Package root:** `gotham-news-media-browser/`

---

## 0. Agent operating rules (read first)

1. **Single source of truth for progress** is [`implementation-state.md`](./implementation-state.md).  
2. Before starting work: open the state file → pick the **lowest-ID** task with status `pending` whose dependencies are all `done`.  
3. Claim it: set status to `in_progress`, set `claimed_by` to your agent/session id, set `started_at` (ISO-8601 UTC).  
4. Implement **only that task** unless the task explicitly says otherwise.  
5. When finished: mark `done`, fill `completed_at`, add a one-line `notes` entry (files touched / commands run).  
6. If blocked: set `blocked`, write the blocker in `notes`, do **not** invent credentials or skip acceptance criteria.  
7. Do **not** implement features from later phases early (no “while I’m here”).  
8. Do **not** introduce an RDBMS, `/admin`, Elastic `semantic_text`, or auth.  
9. Prefer matching existing mockups under `ui-mockups/` and specs under `docs/`.  
10. After each task: run the task’s **Verification** commands; leave the tree buildable.

### Stack lock (do not change without human approval)

| Item | Value |
|------|--------|
| Language | Java **25** |
| Framework | Spring Boot **4.1.1** |
| Build | Maven **3.9.x** |
| UI | Thymeleaf (server-rendered) |
| Search | Elasticsearch Java API Client (Boot BOM ~9.4.x) |
| Compose | `gotham-web` + `imagebind-service` |
| Embeddings | Meta ImageBind, sync HTTP, **1024-d** |
| Storage | Elastic Cloud Serverless + **public** GCS |

### Assumptions (pending human confirmation)

See §12 Open questions. Until answered, agents must follow the **Default** column in that section.

---

## Phase map

```text
P0  Scaffold & agent harness
P1  Config, health, ES client plumbing
P2  Index bootstrap (mappings → Elastic)
P3  /journalist CRUD
P4  /article CRUD (story + metadata + bylines, no media yet)
P5  GCS + multimedia nested CRUD + HTML5 playback
P6  imagebind-service + write-time embeddings
P7  Public search: landing + /results full-text
P8  Semantic · Hybrid · Vector search
P9  Seed data, E2E demo script, hardening
```

Dependency spine: `P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8 → P9`  
(P5 and P6 can partially overlap only if P4 is `done` and the state file marks parallel claims explicitly.)

---

## Phase 0 — Scaffold & agent harness

**Goal:** Empty but runnable Spring Boot app + Compose stubs + agent entry docs.

### P0-T01 — Create Maven multi-module or single-module skeleton
- **Create:** `gotham-news-media-browser/app/` (recommended single module `gotham-web`) with `pom.xml` for Java 25 / Spring Boot 4.1.1 / Thymeleaf / Validation / Elasticsearch client / GCS client deps.  
- **Do:** `Application` main class, `application.yml` placeholders, `.gitignore` for `target/`, `.env.example`.  
- **Don’t:** Business logic.  
- **Verification:** `mvn -q -f app/pom.xml -DskipTests package` succeeds.  
- **Depends on:** —

### P0-T02 — Docker Compose skeleton
- **Create:** `docker-compose.yml` with `gotham-web` (build context `app/`) and `imagebind-service` (placeholder Dockerfile that serves `GET /health` → 200 until real ImageBind lands in P6).  
- **Do:** Document env vars for ES URL, ES API key, GCS bucket.  
- **Verification:** `docker compose config` validates.  
- **Depends on:** P0-T01

### P0-T03 — Agent entrypoints
- **Create/Update:** package `AGENTS.md`, root pointers in `README.md` to plan + state.  
- **Do:** Instruct agents to use task IDs and update `implementation-state.md`.  
- **Verification:** Links resolve; state file lists all task IDs from this plan.  
- **Depends on:** —

### P0-T04 — Shared Thymeleaf layout from mockups
- **Create:** `templates/layout.html` (header legends + footer MIT/copyrights) ported from `ui-mockups/chrome.js` + `styles.css`.  
- **Do:** Static CSS under `classpath:/static/`; placeholder health attributes (`imagebind` / `elasticsearch`).  
- **Verification:** A blank `GET /` returns 200 with layout chrome.  
- **Depends on:** P0-T01

---

## Phase 1 — Config, health, ES client

**Goal:** App can connect to Elastic (or fail clearly) and expose health for header legends.

### P1-T01 — Configuration properties
- **Create:** `@ConfigurationProperties` for `gotham.elasticsearch.*`, `gotham.gcs.*`, `gotham.imagebind.base-url`, media limits.  
- **Do:** Fail fast on missing ES props when `gotham.elasticsearch.required=true` (default false until credentials — see §12).  
- **Verification:** Unit test binds sample YAML.  
- **Depends on:** P0-T01

### P1-T02 — Elasticsearch Java client bean
- **Create:** Client configured with Cloud endpoint + API key header.  
- **Verification:** Integration test skipped without credentials; with credentials, `info()` or `ping` works.  
- **Depends on:** P1-T01

### P1-T03 — Health endpoints for chrome
- **Create:** `GET /api/health/elasticsearch`, proxy/check ImageBind `GET {base}/health`.  
- **Do:** Wire layout model attributes (or fragment) for Available/Unavailable.  
- **Verification:** Manual or MockMvc: down ImageBind → Unavailable legend; ES status matches client.  
- **Depends on:** P1-T02, P0-T04

---

## Phase 2 — Index bootstrap

**Goal:** Both indexes exist with approved mappings.

### P2-T01 — Load mapping JSON from classpath
- **Do:** Copy/symlink `elasticsearch/*.mapping.json` into `app/src/main/resources/elasticsearch/`.  
- **Verification:** Resources present on classpath.  
- **Depends on:** P0-T01

### P2-T02 — Index bootstrap runner
- **Create:** Component that creates `gotham-journalists` and `gotham-media-browser` if missing (idempotent), using mapping files.  
- **Do:** Log clearly; never delete indexes automatically.  
- **Verification:** Against Elastic Cloud (when creds exist): indexes created; second boot no-op.  
- **Depends on:** P1-T02, P2-T01

---

## Phase 3 — `/journalist` CRUD

**Goal:** Full CRUD on `gotham-journalists` matching `ui-design-crud.md`.

### P3-T01 — Journalist domain + repository
- **Create:** Record/DTO + ES repository (index/get/update/delete/search with `from`/`size`).  
- **Do:** Derive `full_name`; store timestamps; use ES auto `_id`.  
- **Verification:** Repository unit/integration tests with WireMock or live ES.  
- **Depends on:** P2-T02

### P3-T02 — Journalist list UI (`GET /journalist`)
- **Do:** Thymeleaf table from mockup; pagination `page` + `size` ∈ {25,50,100} → ES `from`/`size`.  
- **Verification:** Empty list renders; seeded doc appears after create (P3-T03).  
- **Depends on:** P3-T01, P0-T04

### P3-T03 — Journalist create (`GET /journalist/new`, `POST /journalist`)
- **Do:** Form validation (first, last, email required).  
- **Verification:** POST creates doc; redirect to list/edit; `_id` visible on edit.  
- **Depends on:** P3-T02

### P3-T04 — Journalist edit + delete
- **Do:** `GET/POST /journalist/{id}`; `POST /journalist/{id}/delete` blocks if referenced by any article nested `journalist_id` (query `gotham-media-browser`).  
- **Verification:** Update persists; delete blocked when referenced (add fixture in test).  
- **Depends on:** P3-T03

---

## Phase 4 — `/article` CRUD (no media binaries yet)

**Goal:** Denormalized article docs with nested journalist snapshots.

### P4-T01 — Article domain + projection helpers
- **Create:** Article document model matching mapping; builders for `journalist_names`, `journalist_bios`, `journalist_search_text`, `article_search_text` (text parts).  
- **Do:** `contribution_role` enum `AUTHOR|CO_AUTHOR|CONTRIBUTING`; status `DRAFT|PUBLISHED|ARCHIVED`.  
- **Verification:** Unit tests for projections.  
- **Depends on:** P3-T01

### P4-T02 — Article repository
- **Do:** CRUD by `_id`; list with status filter + pagination.  
- **Verification:** Index/get/update/delete against ES (or testcontainer/cloud).  
- **Depends on:** P4-T01, P2-T02

### P4-T03 — Article list + create/edit forms (text + metadata + bylines)
- **Do:** Port `article.html` / `article-new.html` / `article-edit.html` without media upload section (or disabled stub).  
- **Do:** Bylines multi-select from `gotham-journalists`; nest snapshots on save.  
- **Verification:** Create article with ≥1 journalist; edit updates nested bylines.  
- **Depends on:** P4-T02, P3-T04, P0-T04

### P4-T04 — Article delete
- **Do:** `POST /article/{id}/delete` removes ES doc (GCS cleanup no-op until P5).  
- **Verification:** Doc gone from get-by-id.  
- **Depends on:** P4-T03

---

## Phase 5 — GCS + multimedia

**Goal:** Nested multimedia with public URIs and HTML5 playback.

### P5-T01 — GCS storage service
- **Do:** Upload bytes to public bucket; return HTTPS `storage_uri`; delete object helper.  
- **Do:** Enforce IMAGE 10 MiB, AUDIO 20 MiB, VIDEO 50 MiB (duration checks best-effort).  
- **Verification:** Integration test skipped without GCS creds; with creds, object publicly readable.  
- **Depends on:** P1-T01

### P5-T02 — Multimedia on article create/update
- **Do:** App-assigned `multimedia_element_id`; nest metadata fields; HTML5 players in edit/results later.  
- **Do:** Update `multimedia_text` / `multimedia_search_text` projections.  
- **Verification:** Upload image+audio+video fixtures; edit page plays via `<img>/<audio>/<video>`.  
- **Depends on:** P5-T01, P4-T03

### P5-T03 — Remove media + article delete cleans GCS
- **Do:** Checkbox remove-on-save; article delete deletes all nested objects.  
- **Verification:** Objects removed from bucket (or mocked).  
- **Depends on:** P5-T02

---

## Phase 6 — ImageBind service + embeddings

**Goal:** 1024-d vectors on write.

### P6-T01 — Real `imagebind-service` container
- **Do:** Replace placeholder with Meta ImageBind OSS wrapper: `POST /embed/text`, `/embed/image`, `/embed/audio`, `/embed/video` → JSON float[1024]; `GET /health`.  
- **Do:** CPU-only default; document RAM/disk needs.  
- **Verification:** Compose up health 200; embed text returns length 1024.  
- **Depends on:** P0-T02

### P6-T02 — ImageBind Java client + stub mode
- **Do:** Client with timeouts; `gotham.imagebind.stub=true` returns deterministic pseudo-vectors for CI without GPU/CPU model.  
- **Verification:** Stub mode unit tests; live mode optional.  
- **Depends on:** P1-T01, P6-T01 (live optional)

### P6-T03 — Embed on article write
- **Do:** Set `article_embedding` from article text; each multimedia `asset_vector` from bytes.  
- **Do:** Re-embed when text/media changes.  
- **Verification:** Indexed doc has dims 1024 (scripted assert or get mapping + source).  
- **Depends on:** P6-T02, P5-T02

---

## Phase 7 — Public search (full-text)

**Goal:** Dual-panel landing + `/results` FTS matching IA.

### P7-T01 — Landing `GET /`
- **Do:** Port dual panels from `index.html`; modes; FTS attribute checkboxes; journalist filter; vector dropzone UI (POST later in P8).  
- **Verification:** Forms submit to `/results` with correct query params.  
- **Depends on:** P0-T04

### P7-T02 — Article FTS search service
- **Do:** `multi_match` on selected fields (remap `section`→`section.text`, etc.); filters status/section/language/journalist/dates; sort; `from`/`size`; `track_total_hits`.  
- **Verification:** Seeded articles return expected hits for known query.  
- **Depends on:** P4-T03, P2-T02

### P7-T03 — Multimedia FTS with inner_hits
- **Do:** Nested query on multimedia text fields + projections; return asset-level cards.  
- **Verification:** Query matching caption returns media card with parent link.  
- **Depends on:** P5-T02

### P7-T04 — Results pages Thymeleaf
- **Do:** Port results mockups; pagination 25/50/100; wired filters.  
- **Verification:** Manual/MockMvc render + HTML5 players for media entity.  
- **Depends on:** P7-T02, P7-T03, P7-T01

---

## Phase 8 — Semantic · Hybrid · Vector

**Goal:** Complete search mode matrix.

### P8-T01 — Semantic search (text → ImageBind → kNN)
- **Do:** Articles: kNN `article_embedding`; Multimedia: nested kNN `asset_vector`.  
- **Verification:** Semantically similar hit ranks above unrelated BM25-only control (fixture-based).  
- **Depends on:** P6-T03, P7-T04

### P8-T02 — Hybrid RRF
- **Do:** Combine BM25 + kNN via RRF (ES reciprocal rank fusion API or equivalent bool/rrf available in cluster version).  
- **Verification:** Hybrid returns results when either leg matches.  
- **Depends on:** P8-T01, P7-T02

### P8-T03 — Multimedia vector search (file upload)
- **Do:** `mode=vector` POST multipart → ImageBind → kNN `asset_vector`.  
- **Verification:** Uploading a near-duplicate of an indexed image retrieves it.  
- **Depends on:** P8-T01, P7-T01

---

## Phase 9 — Demo readiness

**Goal:** Repeatable demo path for stakeholders.

### P9-T01 — Seed script / CommandLineRunner (optional profile `seed`)
- **Do:** Seed 3 journalists + 3 articles + sample media from `ui-mockups/media/`.  
- **Verification:** `spring.profiles.active=seed` populates indexes idempotently (doc ids or slug checks).  
- **Depends on:** P6-T03, P5-T02

### P9-T02 — E2E checklist doc + smoke script
- **Create:** `docs/demo-runbook.md` with click-path; optional `scripts/smoke.sh` curling health + search.  
- **Verification:** Runbook steps executable with credentials.  
- **Depends on:** P8-T03, P7-T04, P1-T03

### P9-T03 — Final sync pass
- **Do:** Ensure README, plan, state, architecture links accurate; no `/admin`; mockups still referenced.  
- **Verification:** Grep for forbidden terms; state file all P0–P9 tasks `done` or explicitly `cancelled`.  
- **Depends on:** P9-T02

---

## Definition of Done (prototype)

- [ ] Compose brings up `gotham-web` + `imagebind-service`  
- [ ] Both ES indexes exist with approved mappings  
- [ ] `/journalist` and `/article` CRUD work end-to-end  
- [ ] Public dual search + results for all modes in the capability matrix  
- [ ] Header legends reflect real health  
- [ ] Footer shows Packt 2020 + 2026 MIT  
- [ ] `implementation-state.md` reflects completed work  

---

## 12. Open questions (human)

| # | Question | Default until answered |
|---|----------|------------------------|
| Q1 | When will Elastic Cloud endpoint + API key be available? | Develop against optional live ES; tests skip without creds (`@EnabledIfEnvironmentVariable`) |
| Q2 | When will GCS bucket + write SA be available? Objects must be public-read? | Confirm public-read; skip GCS ITs without creds; allow local filesystem fallback **only if you approve** |
| Q3 | ImageBind: build from Meta OSS in-repo vs you provide an image? CPU-only OK for demo? | In-repo Dockerfile + CPU; stub vectors for CI |
| Q4 | Single Maven module `app/` vs `gotham-web` + `imagebind` repos? | Single repo folder; `app/` + `imagebind-service/` |
| Q5 | Delete journalist: always **block if referenced**, or cascade-strip? | **Block if referenced** (matches stricter mock copy) |
| Q6 | Should agents commit on every task or only on phase completion? | **Every task** (small commits, message includes task id) |
| Q7 | Preferred Java package name? | `com.gotham.newsmediabrowser` |
| Q8 | Antigravity/Claude Code: any required extra files (`TASKS.json`, etc.)? | Markdown plan + state only unless you specify |

**Please answer Q1–Q8** (short replies fine). The plan remains executable under Defaults; answers will be patched into this file and the state file’s `assumptions` section.
