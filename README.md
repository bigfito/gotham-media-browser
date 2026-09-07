# Gotham News & Media Browser

Prototype design package for a single-brand news & multimedia browser on **Elastic Cloud Serverless** + **public GCS** (no RDBMS).

## Start here

**End-to-end architecture:** [`docs/architecture-end-to-end.md`](docs/architecture-end-to-end.md)  
**Implementation plan (phased):** [`docs/implementation-plan.md`](docs/implementation-plan.md)  
**Progress / handoff state:** [`docs/implementation-state.md`](docs/implementation-state.md)  
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
| Synthetic data (P10) | `docs/synthetic-data-generation.md` |
| Mappings | `elasticsearch/*.mapping.json` |
| Mockups | `ui-mockups/` (open `index.html`) |

## Locked stack

Java 25 · Spring Boot 4.1.1 · Thymeleaf · **Maven multi-module** (`gotham-common` + `gotham-web` + **`gotham-datagen` in P10**) · ES Java client · Docker Compose (`gotham-web` + in-repo `imagebind-service`; optional profile `datagen` for Qwen / FLUX / Kokoro / Wan) · ImageBind 1024-d

Open the parent `pom.xml` in **IntelliJ IDEA Ultimate** as a Maven project.

## Credentials (prototype)

- Elasticsearch endpoint + API key: hardcoded in `gotham-web` `application.properties`  
- GCS bucket/project: hardcoded in `application.properties`  
- GCS service account JSON: **secret file** under `secrets/` (gitignored)

## Routes

- `GET /` — dual search panels  
- `GET /results` — filters, sort, pagination (`size` 25/50/100)  
- `/journalist/**` — CRUD on `gotham-journalists`  
- `/article/**` — CRUD on denormalized `gotham-media-browser`  

No `/admin`. Synthetic load (last phase): `gotham-datagen` → HTTP CRUD only. License: MIT (see repository `LICENSE`).
