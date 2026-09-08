# Gotham News & Media Browser

Prototype design package for a single-brand news & multimedia browser on **Elastic Cloud Serverless** + **public GCS** (no RDBMS).

## Start here

**End-to-end architecture:** [`docs/architecture-end-to-end.md`](docs/architecture-end-to-end.md)  
**Implementation plan (phased):** [`docs/implementation-plan.md`](docs/implementation-plan.md)  
**Progress / handoff state:** [`docs/implementation-state.md`](docs/implementation-state.md) — **35 / 42** tasks done (P0–P8 + P9-T01/T02/T03; Failsafe `it-es`/`it-imagebind` green on the lab; next **P9-T04** Hardening sync pass)  
**Agent instructions:** [`AGENTS.md`](AGENTS.md)

| Area | Path |
|------|------|
| Logical ER | `docs/er-design.md` |
| ES model | `docs/elasticsearch-denormalized-model.md` |
| ES search methods (DSL) | `docs/elasticsearch-search-methods.md` |
| ES / dual-index diagrams | `docs/elasticsearch-denormalized-diagram.md` · `diagrams/` |
| Components | `docs/architecture-components.md` |
| Frontend IA | `docs/frontend-information-architecture.md` |
| Search UI | `docs/ui-design-search-results.md` |
| CRUD UI | `docs/ui-design-crud.md` |
| Error UX | `docs/ui-design-errors.md` |
| Demo (P9) | `docs/demo/` — `runbook.md` · `seed.sh` · `smoke.sh` · `fixtures/` |
| Synthetic data (P10) | `docs/synthetic-data-generation.md` |
| Testing | `docs/testing-strategy.md` |
| Mappings | `elasticsearch/*.mapping.json` |
| Mockups | `ui-mockups/` (open `index.html`) |

## Locked stack

Java 25 · Spring Boot 4.1.1 · Thymeleaf · **Maven multi-module** (`gotham-common` + `gotham-web` + **`gotham-datagen` console in P10**) · ES Java client · Docker Compose (`gotham-web` + `imagebind-service`; profile **`datagen`** = Ollama / ComfyUI / Kokoro containers) · ImageBind 1024-d · M4 32 GB lab · **unit + integration tests** (ES / ImageBind / helpers) · **42** implementation tasks (P0–P10)

Open the parent `pom.xml` in **IntelliJ IDEA Ultimate** as a Maven project.

## Credentials (prototype)

- Elasticsearch endpoint + API key: **placeholders** in committed `application.properties`; real values in untracked `application-local.properties` (or env)  
- GCS bucket/project: placeholders in `application.properties`; real values in the untracked override  
- GCS service account JSON: **secret file** under `secrets/gcp-sa.json` (gitignored)

## Routes

- `GET /` — dual search panels (articles + multimedia)  
- `GET /results` · `POST /results` — full-text, **semantic (kNN)**, and **hybrid (RRF)** results with filters, sort, pagination (`size` 25/50/100); multimedia **vector** search takes an uploaded file via multipart `POST`. Article `mode=vector` is rejected (**HTTP 400**).  
- `/journalist/**` — CRUD on `gotham-journalists` (delete **cascade-strips** nested bylines)  
- `/article/**` — CRUD on denormalized `gotham-media-browser` (media → public GCS; ImageBind embeddings on write)  
- `/api/health/elasticsearch` · `/api/health/imagebind` — chrome availability legends  

No `/admin`. Synthetic load (last phase): Java **console** `gotham-datagen` (**not** Spring Boot) → HTTP CRUD only (defaults 15 journalists · 25 articles · 5+5+5 media). License: MIT (see repository `LICENSE`).
