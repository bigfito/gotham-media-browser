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

### Stack lock (do not change without human approval)

| Item | Value |
|------|--------|
| Language | Java **25** |
| Framework | Spring Boot **4.1.1** |
| Build | Maven **3.9.x** **multi-module** (IntelliJ IDEA Ultimate–friendly) |
| UI | Thymeleaf (server-rendered) |
| Search | Elasticsearch Java API Client (Boot BOM ~9.4.x) |
| Compose | `gotham-web` + `imagebind-service` (datagen helpers run **natively on macOS** in **P10**) |
| Embeddings | Meta ImageBind **built in-repo**, sync HTTP, **1024-d** |
| Synthetic data | Maven module **`gotham-datagen`** (phase **P10**, last) — see [`synthetic-data-generation.md`](./synthetic-data-generation.md) |
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
├── gotham-datagen/              # independent Java app (added in P10; last phase)
│   └── pom.xml                  # own Spring Boot main; depends on gotham-common; HTTP to web + helpers
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
| `gotham-datagen` | `gotham-datagen` | **P10** independent Java app: generate text/media via helpers → `POST /journalist` & `/article` |

`imagebind-service/` stays in the same repo for cohesion but is **Compose-built**, not a Maven module.  
**P0** scaffolds `gotham-common` + `gotham-web` only; **P10** adds `gotham-datagen` (standalone Boot app) to the parent module list.

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
P1  Config, health, ES client, **global fault-tolerant error pages**
P2  Index bootstrap (mappings → Elastic)
P3  /journalist CRUD (cascade-strip on delete)
P4  /article CRUD (story + metadata + bylines, no media yet)
P5  GCS + multimedia nested CRUD + HTML5 playback
P6  imagebind-service (in-repo) + write-time embeddings
P7  Public search: landing + /results full-text
P8  Semantic · Hybrid · Vector search
P9  Static smoke fixtures + demo runbook (no GPU required)
P10 Synthetic data generation (`gotham-datagen` + modality helpers)  ← LAST
```

Dependency spine: `P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8 → P9 → P10`

**Total tasks:** 41 (see task sections + [`implementation-state.md`](./implementation-state.md)).  
**Canonical generative design:** [`synthetic-data-generation.md`](./synthetic-data-generation.md).

---

## Phase 0 — Scaffold & agent harness

**Goal:** Buildable multi-module Maven project + Compose stubs + agent docs. Importable in IntelliJ IDEA Ultimate.

### P0-T01 — Parent POM + `gotham-common` + `gotham-web` skeleton
- **Create:** Parent `pom.xml` (`packaging` `pom`, modules `gotham-common`, `gotham-web`); both child modules; Java 25; Spring Boot 4.1.1 parent/BOM; package `com.gotham.newsmediabrowser`.  
- **Do:** `gotham-web` has `@SpringBootApplication`, empty `application.properties` with **placeholder** ES/GCS/ImageBind keys (see credentials table); `gotham-common` empty library jar.  
- **Do:** Root `.gitignore` for `**/target/`, `secrets/*.json` (allow `*.example`), IDE files as appropriate.  
- **Don’t:** Business logic; do **not** add `gotham-datagen` yet (P10).  
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

## Phase 1 — Config, health, ES client, global fault-tolerant error pages

**Goal:** Hardcoded properties drive ES client; header legends get health; branded error pages cover all endpoints.

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
- **Do:** Implement cookbook §4 ([`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md)): `multi_match`, field remap, filters, journalist nested filter, `from`/`size`, `track_total_hits`.  
- **Depends on:** P4-T03, P2-T02

### P7-T03 — Multimedia FTS + inner_hits
- **Do:** Implement cookbook §7: nested BM25 + `inner_hits` for asset cards.  
- **Depends on:** P5-T02

### P7-T04 — Results Thymeleaf pages
- **Depends on:** P7-T01, P7-T02, P7-T03

---

## Phase 8 — Semantic · Hybrid · Vector

**Goal:** Complete search mode matrix per [`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md).

### P8-T01 — Semantic kNN
- **Do:** Cookbook §5 (articles) + §8 (multimedia nested kNN + `inner_hits`); text → ImageBind → `query_vector` length 1024.  
- **Depends on:** P6-T03, P7-T04

### P8-T02 — Hybrid RRF
- **Do:** Cookbook §6 / §9 using `retriever.rrf`; shared filters; document pagination approach.  
- **Depends on:** P8-T01, P7-T02

### P8-T03 — Multimedia vector (file) search
- **Do:** Cookbook §10: multipart → ImageBind → same nested kNN as semantic; reject article vector mode with 400 error page.  
- **Depends on:** P8-T01, P7-T01

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

### P9-T03 — Hardening sync pass
- **Do:** Align README/AGENTS/mockups with shipped behavior; confirm error pages still cover all routes.  
- **Don’t:** Start `gotham-datagen` (that is P10).  
- **Verification:** Checklist in plan DoD (excluding P10) can be ticked for a smoke demo.  
- **Depends on:** P9-T02

---

## Phase 10 — Synthetic data generation (**last phase**)

**Goal:** Independent Java app `gotham-datagen` generates realistic journalists + articles (with IMAGE / AUDIO / VIDEO) and loads them **only through** live `POST /journalist` and `POST /article`.  
**Spec:** [`synthetic-data-generation.md`](./synthetic-data-generation.md).

### Helper services (locked — MacBook Pro M4 · 32 GB · no NVIDIA)

| Modality | Model | Size / quant | Unified mem (approx.) | Run on Mac |
|----------|-------|--------------|------------------------|------------|
| Text | **Qwen 2.5 7B-Instruct** | 7B (Q4_K_M) | ~5–6 GB | Native **Ollama** (Metal) |
| Image | **SDXL-Turbo** | few-step SDXL | ~6–8 GB peak | Native **ComfyUI** (MPS) |
| Audio / voice | **Kokoro-82M** | 82M | ~0.5 GB | Native / CPU |
| Video | **Wan2.1 (T2V-1.3B)** | 1.3B | ~8–12 GB w/ offload | Native **ComfyUI** (MPS + CPU offload) |

Do **not** require NVIDIA CUDA Docker images for the prototype lab.

### Locked default volumes

| Item | Count |
|------|------:|
| Journalists | **15** |
| Articles | **25** |
| IMAGE / AUDIO / VIDEO per article | **5 / 5 / 5** |
| Video clip length | **5 seconds** each |
| Total media assets | **375** |

Skip flags for IMAGE/AUDIO/VIDEO when helpers are unavailable; **text (Qwen 7B) is required**. Full runs on M4 are long (especially video) — document overnight expectation in the runbook.

### P10-T01 — Parent POM + independent `gotham-datagen` app skeleton
- **Create:** `gotham-datagen/` as a **standalone** Spring Boot application (`web` disabled or none; own `@SpringBootApplication` + executable jar); package `com.gotham.newsmediabrowser.datagen`; add module to parent `pom.xml`.  
- **Do:** Placeholder `application.properties` for `gotham.datagen.*` URLs/volumes/model id (`qwen2.5:7b-instruct`, 15 / 25 / 5+5+5 / 5 s).  
- **Don’t:** Embed datagen inside `gotham-web`; don’t call generative APIs yet.  
- **Verification:** `mvn -pl gotham-datagen -am -DskipTests package` produces a runnable jar; IntelliJ shows independent app module.  
- **Depends on:** P0-T01, P9-T03

### P10-T02 — Native macOS helpers runbook (Ollama · ComfyUI · Kokoro)
- **Create:** Runbook + any thin wrappers for **native** installs on Apple Silicon: Ollama + `qwen2.5:7b-instruct`, ComfyUI MPS workflows for **SDXL-Turbo** + **Wan2.1 1.3B**, Kokoro-82M on CPU.  
- **Do:** Keep Compose for `gotham-web` + `imagebind-service` only; document that CUDA ComfyUI images are **out of scope** for this lab.  
- **Don’t:** Make `yanwk/comfyui-boot` (CUDA) a hard dependency.  
- **Verification:** Health URLs reachable on `:11434` / `:8188` / `:8880` after following the runbook on M4 (or documented mock for CI).  
- **Depends on:** P0-T02, P10-T01

### P10-T03 — Helper HTTP clients (Qwen 7B · SDXL-Turbo · Kokoro · Wan)
- **Create:** Clients in `gotham-datagen` for Ollama chat (`qwen2.5:7b-instruct`), ComfyUI T2I (SDXL-Turbo) + T2V (Wan2.1 **5 s** clips), Kokoro TTS; health-check each before use.  
- **Do:** Enforce product media caps and synthetic video target **5 s**; tolerate slow MPS generation (timeouts documented, not silent failures).  
- **Verification:** Unit/integration tests or documented dry-run against mocked helpers; clients fail clearly when down.  
- **Depends on:** P10-T02

### P10-T04 — Orchestrator → `POST /journalist` & `POST /article`
- **Do:** Pipeline: Qwen 7B → **15** journalists → **25** articles (+ captions) → **5** images + **5** audios + **5** videos per article → multipart/form matching CRUD contracts → collect ids; print summary with reasons/reference ids on failures.  
- **Don’t:** Write directly to Elasticsearch or GCS; don’t run inside the `gotham-web` process.  
- **Verification:** Against running `gotham-web` + helpers (or recorded stubs): creates docs visible via list UIs / ES; per-article media counts match defaults; `--skip-image`/`--skip-audio`/`--skip-video` honored; abort if Ollama down.  
- **Depends on:** P10-T03, P3-T03, P5-T02, P6-T03

### P10-T05 — Datagen runbook + verification report
- **Create:** Operator runbook for **M4 32 GB**: native helper install, model pulls, memory tips (optional `qwen2.5:3b-instruct` under pressure), expected volumes **15 / 25 / 5+5+5**, long-run / overnight notes for video.  
- **Do:** Sample: `mvn -pl gotham-datagen spring-boot:run` or `java -jar gotham-datagen/target/gotham-datagen-*.jar`.  
- **Verification:** Runbook completes on the M4 lab **or** documents skip-flag path; state file P10 tasks closable.  
- **Depends on:** P10-T04

---

## Definition of Done (prototype)

- [ ] IntelliJ opens parent POM as multi-module (`gotham-common`, `gotham-web`, and after P10 `gotham-datagen`)  
- [ ] Compose brings up `gotham-web` + in-repo `imagebind-service`  
- [ ] ES endpoint + API key supplied via `application.properties`  
- [ ] GCS SA JSON supplied as secret file; bucket props in `application.properties`  
- [ ] Both indexes bootstrapped  
- [ ] `/journalist` + `/article` CRUD; journalist delete **cascade-strips**  
- [ ] Search modes per capability matrix  
- [ ] Header health legends + MIT footer  
- [ ] **Fault tolerant UX:** unexpected errors on any endpoint show branded error page with reason (no Whitelabel/stack dumps)  
- [ ] P9 static smoke path works without generative helpers  
- [ ] **P10:** Independent `gotham-datagen` Java app on **M4** loads **15** / **25** / **5+5+5** via HTTP CRUD using **Qwen 7B / SDXL-Turbo / Kokoro / Wan 1.3B** (or documented skip flags)  
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
| Extra | **Multi-module Maven** for IntelliJ IDEA Ultimate (`gotham-common` + `gotham-web` + parent; **`gotham-datagen` independent app in P10**) |
| Extra | **Fault-tolerant UX:** global error pages with reason on all endpoints ([`ui-design-errors.md`](./ui-design-errors.md)) |
| Extra | **Synthetic data (last phase P10):** independent Java app `gotham-datagen`; load only via `/journalist` & `/article` |
| Extra | **Datagen volumes:** 15 journalists · 25 articles · 5 IMAGE + 5 AUDIO + 5 VIDEO (5 s) per article; P9 static seed kept |
| Extra | **Lab hardware:** MacBook Pro M4 · 32 GB · no NVIDIA → native Metal helpers; models **Qwen 2.5 7B**, **SDXL-Turbo**, **Kokoro-82M**, **Wan2.1 1.3B** |
