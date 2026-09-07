# Implementation State — Gotham News & Media Browser

**Plan:** [`implementation-plan.md`](./implementation-plan.md)  
**Architecture:** [`architecture-end-to-end.md`](./architecture-end-to-end.md)  
**ES search DSL:** [`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md)  
**Synthetic data (P10):** [`synthetic-data-generation.md`](./synthetic-data-generation.md)  
**Testing:** [`testing-strategy.md`](./testing-strategy.md)  
**Last updated:** 2026-09-07T15:20:00Z  
**Active phase:** P2 (P1 complete — **compact context now** before starting P2)  
**Prototype status:** `in_progress`  
**Next task:** `P2-T01` (Mapping JSON on classpath — dep P0-T01 done)

---

## How agents update this file

1. Claim one `pending` task → `in_progress` (set `claimed_by`, `started_at`).  
2. On finish → `done` (set `completed_at`, brief `notes` including **test commands run**).  
3. On blocker → `blocked` + `notes` with reason.  
4. Never mark `done` without meeting the plan’s Verification section **and** required unit tests (`mvn test`).  
5. Bump **Last updated** on every change; refresh phase counts in Progress summary.  
6. **One git commit per task** (message includes task id).  
7. **At each phase boundary, compact context to save tokens:** when a phase reaches N/N `done` (and its last task is committed), **pause and compact the context window before the next phase** — Claude Code `/compact`, or the equivalent summarize/fresh-session step on other harnesses. Only at phase boundaries, never mid-task.

Status values: `pending` | `in_progress` | `done` | `blocked` | `cancelled`

---

## Locked decisions (2026-09-07)

| Key | Value |
|-----|--------|
| ES endpoint + API key | Placeholders in committed `application.properties`; real values in untracked `application-local.properties` (or env) — never committed |
| GCS bucket/project | Placeholders in `application.properties`; real values in untracked override |
| GCS SA JSON | Secret file `secrets/gcp-sa.json` (gitignored); path in properties |
| Build JDK | **JDK 25 LTS** (`JAVA_HOME` set to the Java 25 home; Maven 3.9.x) |
| ImageBind | Built **in-repo** under `imagebind-service/` |
| Maven | **Multi-module**: parent + `gotham-common` + `gotham-web` (+ **`gotham-datagen` Java console module in P10 — not Spring Boot**) |
| Journalist delete | **Cascade-strip** + reindex articles |
| Commits | One per task |
| Package | `com.gotham.newsmediabrowser` |
| Fault tolerance | Global error pages with reason on **all** endpoints (`docs/ui-design-errors.md`) |
| Testing | **Unit tests** for all backend + frontend (MockMvc); **integration tests** for ES, ImageBind, datagen helpers |
| Synthetic data | **Last phase P10** — Java **console** `gotham-datagen` via HTTP CRUD; helpers **must** be Docker containers |
| Datagen models | Qwen 2.5 **7B** · **SDXL-Turbo** · Kokoro-82M · Wan2.1 **1.3B** |
| Datagen volumes | **15** journalists · **25** articles · **5** IMAGE + **5** AUDIO + **5** VIDEO (5 s) each |
| Lab hardware | **MacBook Pro M4 · 32 GB · no NVIDIA GPU** (CPU inference inside Docker Desktop) |
| Extra agent formats | None (Markdown plan + state only) |
| Context compaction | **Pause and compact context at every phase boundary** (Claude Code `/compact`, or equivalent) to save tokens; never mid-task |

---

## Progress summary

| Phase | Title | Tasks done | Status |
|-------|-------|------------|--------|
| P0 | Scaffold & agent harness | 5/5 | done |
| P1 | Config, health, ES client, error pages | 4/4 | done |
| P2 | Index bootstrap | 0/2 | pending |
| P3 | `/journalist` — list + create + edit form | 0/3 | pending |
| P4 | `/article` CRUD + journalist cascade-strip delete | 0/5 | pending |
| P5 | GCS + multimedia | 0/3 | pending |
| P6 | ImageBind + embeddings | 0/3 | pending |
| P7 | Public FTS search | 0/4 | pending |
| P8 | Semantic · Hybrid · Vector | 0/3 | pending |
| P9 | Demo smoke + static fixtures + ES/ImageBind ITs | 0/4 | pending |
| P10 | Synthetic data generation (**last**) | 0/6 | pending |

**Totals:** 9 / **42** tasks done

---

## Task board

Titles and **Depends on** must match [`implementation-plan.md`](./implementation-plan.md).

| ID | Phase | Title | Status | Depends on | Claimed by | Started | Completed | Notes |
|----|-------|-------|--------|------------|------------|---------|-----------|-------|
| P0-T01 | P0 | Parent POM + gotham-common + gotham-web skeleton | done | — | JavaMentor | 2026-09-07T13:05:00Z | 2026-09-07T13:10:00Z | Parent (Boot 4.1.1, Java 25) + gotham-common + gotham-web. `mvn test` green (2 unit tests, JDK 25); `mvn -DskipTests package` builds boot jar. |
| P0-T02 | P0 | Docker Compose skeleton | done | P0-T01 | JavaMentor | 2026-09-07T13:15:00Z | 2026-09-07T13:20:00Z | compose: gotham-web (build, :8080, secrets ro mount, optional .env) + imagebind-service placeholder (:8081, /health, healthcheck). No datagen profile (P10). `docker compose config` valid; placeholder /health → 200 verified. Full image build deferred (heavy). Added Maven Wrapper. |
| P0-T03 | P0 | Agent entrypoints | done | — | JavaMentor | 2026-09-07T13:25:00Z | 2026-09-07T13:30:00Z | AGENTS.md: added Build & verify (JDK 25) + Source-of-truth note. Verified all markdown links resolve and all 42 task IDs match between plan headers and state board. |
| P0-T04 | P0 | Shared Thymeleaf layout from mockups | done | P0-T01 | JavaMentor | 2026-09-07T13:35:00Z | 2026-09-07T13:45:00Z | Ported chrome to templates/fragments/chrome.html (head/header legends/footer MIT) + static/css/styles.css; HomeController GET / -> index. @WebMvcTest renders chrome (200). Boot 4 note: @WebMvcTest moved to org.springframework.boot.webmvc.test.autoconfigure (added spring-boot-starter-webmvc-test + thymeleaf-test). |
| P0-T05 | P0 | Secrets scaffolding | done | P0-T01 | JavaMentor | 2026-09-07T13:50:00Z | 2026-09-07T13:55:00Z | Added secrets/README.md, secrets/gcp-sa.json.example, application-local.properties.example. Verified git check-ignore matches for real gcp-sa.json + application-local.properties; examples trackable; real key untracked. mvn test green. |
| P1-T01 | P1 | @ConfigurationProperties bound to application.properties | done | P0-T01, P0-T05 | JavaMentor | 2026-09-07T14:00:00Z | 2026-09-07T14:10:00Z | Records in gotham-common: ElasticsearchProperties (masked apiKey), GcsProperties, ImageBindProperties, MediaLimitsProperties (DataSize/Duration). Registered via @ConfigurationPropertiesScan on the app. Binding unit test (ApplicationContextRunner, 4 cases incl. mask + placeholder detection). mvn test green (7). |
| P1-T02 | P1 | Elasticsearch Java client bean | done | P1-T01 | JavaMentor | 2026-09-07T14:15:00Z | 2026-09-07T14:25:00Z | ElasticsearchClientConfig builds ElasticsearchClient via ElasticsearchClient.of(host,apiKey) (elasticsearch-java 9.4.5, rest5 transport). Starts with placeholders (client created, calls fail). Unit test creates bean. Real smoke (application-local.properties): cluster info GET / -> 200 serverless 9.6.0; HEAD gotham-media-browser -> 404 (index not yet created). API key never logged. |
| P1-T03 | P1 | Health endpoints for chrome | done | P1-T02, P0-T04 | JavaMentor | 2026-09-07T14:30:00Z | 2026-09-07T14:50:00Z | HealthController /api/health/{elasticsearch,imagebind} (200 UP / 503 DOWN) + ElasticsearchHealthChecker (client.info(), Serverless-safe) + ImageBindHealthChecker (HTTP). chrome.js flips legends. 9 unit/slice tests. Live: GET / 200; ES health 200 UP (live serverless); ImageBind 503 DOWN; API key not in logs. Also moved application-local.properties to repo root (out of jar). |
| P1-T04 | P1 | Global fault tolerance & user error pages | done | P0-T04, P1-T01 | JavaMentor | 2026-09-07T14:55:00Z | 2026-09-07T15:20:00Z | Domain exceptions (NotFound/Dependency/MediaLimit) in gotham-common; @ControllerAdvice + ErrorViewFactory (status/title/reason/reference id, stack logged server-side only) + GothamErrorController (/error) + branded error.html; Whitelabel off. Framework MVC exceptions keep their status via ErrorResponse. 8 tests. Live: unknown route -> 404 branded (no stack/Whitelabel). |
| P2-T01 | P2 | Mapping JSON on classpath | pending | P0-T01 | | | | |
| P2-T02 | P2 | Idempotent index bootstrap | pending | P1-T02, P2-T01 | | | | |
| P3-T01 | P3 | Journalist domain + repository | pending | P2-T02 | | | | |
| P3-T02 | P3 | Journalist list UI (GET /journalist) | pending | P3-T01, P0-T04, P1-T04 | | | | |
| P3-T03 | P3 | Journalist create | pending | P3-T02 | | | | |
| P4-T01 | P4 | Article domain + projection helpers | pending | P3-T01 | | | | |
| P4-T02 | P4 | Article repository | pending | P4-T01, P2-T02 | | | | |
| P4-T03 | P4 | Journalist edit + cascade-strip delete | pending | P4-T02, P3-T03 | | | | |
| P4-T04 | P4 | Article list + create/edit (text + metadata + bylines) | pending | P4-T02, P3-T03, P0-T04, P1-T04 | | | | |
| P4-T05 | P4 | Article delete | pending | P4-T04 | | | | |
| P5-T01 | P5 | GCS storage service | pending | P1-T01, P0-T05 | | | | |
| P5-T02 | P5 | Multimedia on article create/update | pending | P5-T01, P4-T04 | | | | |
| P5-T03 | P5 | Remove media + article delete cleans GCS | pending | P5-T02 | | | | |
| P6-T01 | P6 | In-repo imagebind-service | pending | P0-T02 | | | | |
| P6-T02 | P6 | Java ImageBind client + stub mode | pending | P1-T01 | | | | |
| P6-T03 | P6 | Embed on article write | pending | P6-T02, P5-T02 | | | | |
| P7-T01 | P7 | Landing GET / | pending | P0-T04, P1-T04 | | | | |
| P7-T02 | P7 | Article FTS service | pending | P4-T04, P2-T02 | | | | |
| P7-T03 | P7 | Multimedia FTS + inner_hits | pending | P5-T02 | | | | |
| P7-T04 | P7 | Results Thymeleaf pages | pending | P7-T01, P7-T02, P7-T03 | | | | |
| P8-T01 | P8 | Semantic kNN | pending | P6-T03, P7-T04 | | | | |
| P8-T02 | P8 | Hybrid RRF | pending | P8-T01, P7-T02 | | | | |
| P8-T03 | P8 | Multimedia vector (file) search | pending | P8-T01, P7-T01, P1-T04 | | | | |
| P9-T01 | P9 | Static seed fixtures | pending | P6-T03, P5-T02 | | | | |
| P9-T02 | P9 | Demo runbook + smoke script | pending | P8-T03, P7-T04, P1-T03, P9-T01 | | | | |
| P9-T03 | P9 | Integration tests: Elasticsearch + ImageBind + web | pending | P9-T02, P6-T03, P5-T02, P8-T03, P4-T03, P4-T05 | | | | |
| P9-T04 | P9 | Hardening sync pass | pending | P9-T03 | | | | |
| P10-T01 | P10 | Parent POM + Java console gotham-datagen skeleton | pending | P0-T01, P9-T04 | | | | |
| P10-T02 | P10 | Compose profile datagen (Ollama · ComfyUI · Kokoro containers) | pending | P0-T02, P10-T01 | | | | |
| P10-T03 | P10 | Helper HTTP clients (Qwen 7B · SDXL-Turbo · Kokoro · Wan) | pending | P10-T02 | | | | |
| P10-T04 | P10 | Orchestrator → POST /journalist & POST /article | pending | P10-T03, P3-T03, P4-T04, P5-T02, P6-T03 | | | | |
| P10-T05 | P10 | Datagen runbook + verification report | pending | P10-T04 | | | | |
| P10-T06 | P10 | Integration tests: datagen helpers + orchestrator | pending | P10-T05, P10-T02, P9-T03 | | | | |

---

## Blockers log

| Date | Task | Blocker | Resolution |
|------|------|---------|------------|
| 2026-09-07 | P3/P4 | Cross-phase back-edge: old `P3-T04` (journalist edit + cascade-strip delete) depended on `P4-T02`, so ID order was not a valid execution order. | Relocated journalist edit + cascade-strip delete into Phase 4 as `P4-T03` (after `P4-T02`). Article create/edit → `P4-T04`, article delete → `P4-T05`. All dependencies now forward-only; total stays **42** (P3 3, P4 5). |

---

## Session handoff template

```markdown
### Handoff
- Date:
- Agent:
- Last task completed:
- Task in progress:
- Branch:
- application.properties filled? ES? GCS?
- secrets/gcp-sa.json present locally? application-local.properties filled?
- mvn test green?
- Integration profiles run? it-es / it-imagebind / it-datagen-helpers?
- Docker Desktop memory OK for datagen profile?
- Next recommended task:
- Risks / notes:
```
