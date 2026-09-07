# Implementation State — Gotham News & Media Browser

**Plan:** [`implementation-plan.md`](./implementation-plan.md)  
**Architecture:** [`architecture-end-to-end.md`](./architecture-end-to-end.md)  
**ES search DSL:** [`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md)  
**Synthetic data (P10):** [`synthetic-data-generation.md`](./synthetic-data-generation.md)  
**Last updated:** 2026-09-07T04:30:00Z  
**Active phase:** P0  
**Prototype status:** `not_started`  
**Next task:** `P0-T01` (or `P0-T03` — both have no deps)

---

## How agents update this file

1. Claim one `pending` task → `in_progress` (set `claimed_by`, `started_at`).  
2. On finish → `done` (set `completed_at`, brief `notes`).  
3. On blocker → `blocked` + `notes` with reason.  
4. Never mark `done` without meeting the plan’s Verification section.  
5. Bump **Last updated** on every change; refresh phase counts in Progress summary.  
6. **One git commit per task** (message includes task id).

Status values: `pending` | `in_progress` | `done` | `blocked` | `cancelled`

---

## Locked decisions (2026-09-07)

| Key | Value |
|-----|--------|
| ES endpoint + API key | Hardcoded in `gotham-web` `application.properties` |
| GCS bucket/project | Hardcoded in `application.properties` |
| GCS SA JSON | Secret file under `secrets/` (gitignored); path in properties |
| ImageBind | Built **in-repo** under `imagebind-service/` |
| Maven | **Multi-module**: parent + `gotham-common` + `gotham-web` (+ **`gotham-datagen` Java console module in P10 — not Spring Boot**) |
| Journalist delete | **Cascade-strip** + reindex articles |
| Commits | One per task |
| Package | `com.gotham.newsmediabrowser` |
| Fault tolerance | Global error pages with reason on **all** endpoints (`docs/ui-design-errors.md`) |
| Synthetic data | **Last phase P10** — Java **console** `gotham-datagen` via HTTP CRUD; helpers **must** be Docker containers |
| Datagen models | Qwen 2.5 **7B** · **SDXL-Turbo** · Kokoro-82M · Wan2.1 **1.3B** |
| Datagen volumes | **15** journalists · **25** articles · **5** IMAGE + **5** AUDIO + **5** VIDEO (5 s) each |
| Lab hardware | **MacBook Pro M4 · 32 GB · no NVIDIA GPU** (CPU inference inside Docker Desktop) |
| Extra agent formats | None (Markdown plan + state only) |

---

## Progress summary

| Phase | Title | Tasks done | Status |
|-------|-------|------------|--------|
| P0 | Scaffold & agent harness | 0/5 | pending |
| P1 | Config, health, ES client, error pages | 0/4 | pending |
| P2 | Index bootstrap | 0/2 | pending |
| P3 | `/journalist` CRUD | 0/4 | pending |
| P4 | `/article` CRUD (no media) | 0/4 | pending |
| P5 | GCS + multimedia | 0/3 | pending |
| P6 | ImageBind + embeddings | 0/3 | pending |
| P7 | Public FTS search | 0/4 | pending |
| P8 | Semantic · Hybrid · Vector | 0/3 | pending |
| P9 | Demo smoke + static fixtures | 0/3 | pending |
| P10 | Synthetic data generation (**last**) | 0/5 | pending |

**Totals:** 0 / **40** tasks done

---

## Task board

Titles and **Depends on** must match [`implementation-plan.md`](./implementation-plan.md).

| ID | Phase | Title | Status | Depends on | Claimed by | Started | Completed | Notes |
|----|-------|-------|--------|------------|------------|---------|-----------|-------|
| P0-T01 | P0 | Parent POM + gotham-common + gotham-web skeleton | pending | — | | | | |
| P0-T02 | P0 | Docker Compose skeleton | pending | P0-T01 | | | | |
| P0-T03 | P0 | Agent entrypoints | pending | — | | | | |
| P0-T04 | P0 | Shared Thymeleaf layout from mockups | pending | P0-T01 | | | | |
| P0-T05 | P0 | Secrets scaffolding | pending | P0-T01 | | | | |
| P1-T01 | P1 | @ConfigurationProperties bound to application.properties | pending | P0-T01, P0-T05 | | | | |
| P1-T02 | P1 | Elasticsearch Java client bean | pending | P1-T01 | | | | |
| P1-T03 | P1 | Health endpoints for chrome | pending | P1-T02, P0-T04 | | | | |
| P1-T04 | P1 | Global fault tolerance & user error pages | pending | P0-T04, P1-T01 | | | | |
| P2-T01 | P2 | Mapping JSON on classpath | pending | P0-T01 | | | | |
| P2-T02 | P2 | Idempotent index bootstrap | pending | P1-T02, P2-T01 | | | | |
| P3-T01 | P3 | Journalist domain + repository | pending | P2-T02 | | | | |
| P3-T02 | P3 | Journalist list UI (GET /journalist) | pending | P3-T01, P0-T04, P1-T04 | | | | |
| P3-T03 | P3 | Journalist create | pending | P3-T02 | | | | |
| P3-T04 | P3 | Journalist edit + cascade-strip delete | pending | P3-T03, P4-T02 | | | | |
| P4-T01 | P4 | Article domain + projection helpers | pending | P3-T01 | | | | |
| P4-T02 | P4 | Article repository | pending | P4-T01, P2-T02 | | | | |
| P4-T03 | P4 | Article list + create/edit (text + metadata + bylines) | pending | P4-T02, P3-T03, P0-T04, P1-T04 | | | | |
| P4-T04 | P4 | Article delete | pending | P4-T03 | | | | |
| P5-T01 | P5 | GCS storage service | pending | P1-T01, P0-T05 | | | | |
| P5-T02 | P5 | Multimedia on article create/update | pending | P5-T01, P4-T03 | | | | |
| P5-T03 | P5 | Remove media + article delete cleans GCS | pending | P5-T02 | | | | |
| P6-T01 | P6 | In-repo imagebind-service | pending | P0-T02 | | | | |
| P6-T02 | P6 | Java ImageBind client + stub mode | pending | P1-T01 | | | | |
| P6-T03 | P6 | Embed on article write | pending | P6-T02, P5-T02 | | | | |
| P7-T01 | P7 | Landing GET / | pending | P0-T04, P1-T04 | | | | |
| P7-T02 | P7 | Article FTS service | pending | P4-T03, P2-T02 | | | | |
| P7-T03 | P7 | Multimedia FTS + inner_hits | pending | P5-T02 | | | | |
| P7-T04 | P7 | Results Thymeleaf pages | pending | P7-T01, P7-T02, P7-T03 | | | | |
| P8-T01 | P8 | Semantic kNN | pending | P6-T03, P7-T04 | | | | |
| P8-T02 | P8 | Hybrid RRF | pending | P8-T01, P7-T02 | | | | |
| P8-T03 | P8 | Multimedia vector (file) search | pending | P8-T01, P7-T01, P1-T04 | | | | |
| P9-T01 | P9 | Static seed fixtures | pending | P6-T03, P5-T02 | | | | |
| P9-T02 | P9 | Demo runbook + smoke script | pending | P8-T03, P7-T04, P1-T03, P9-T01 | | | | |
| P9-T03 | P9 | Hardening sync pass | pending | P9-T02 | | | | |
| P10-T01 | P10 | Parent POM + Java console gotham-datagen skeleton | pending | P0-T01, P9-T03 | | | | |
| P10-T02 | P10 | Compose profile datagen (Ollama · ComfyUI · Kokoro containers) | pending | P0-T02, P10-T01 | | | | |
| P10-T03 | P10 | Helper HTTP clients (Qwen 7B · SDXL-Turbo · Kokoro · Wan) | pending | P10-T02 | | | | |
| P10-T04 | P10 | Orchestrator → POST /journalist & POST /article | pending | P10-T03, P3-T03, P4-T03, P5-T02, P6-T03 | | | | |
| P10-T05 | P10 | Datagen runbook + verification report | pending | P10-T04 | | | | |

---

## Blockers log

| Date | Task | Blocker | Resolution |
|------|------|---------|------------|
| | | | |

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
- secrets/gcs-sa.json present locally?
- Docker Desktop memory OK for datagen profile?
- Next recommended task:
- Risks / notes:
```
