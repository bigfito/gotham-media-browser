# AGENTS.md — Gotham News & Media Browser

Instructions for AI coding agents (Claude Code, Google Antigravity, Cursor, Codex, etc.).

## Mission

Implement the **Gotham News & Media Browser** prototype from the approved design under `gotham-news-media-browser/`.

## Read in this order

1. [`docs/implementation-state.md`](docs/implementation-state.md) — **what to do next**  
2. [`docs/implementation-plan.md`](docs/implementation-plan.md) — task details & verification  
3. [`docs/architecture-end-to-end.md`](docs/architecture-end-to-end.md) — system design  
4. Spec linked from the task (CRUD / search IA / mappings / [`docs/elasticsearch-search-methods.md`](docs/elasticsearch-search-methods.md) for query modes / [`docs/synthetic-data-generation.md`](docs/synthetic-data-generation.md) for P10)

## Work loop

```text
1. Open implementation-state.md
2. Pick lowest-ID pending task whose Depends-on tasks are done
3. Claim: status=in_progress, claimed_by=<you>, started_at=<UTC>
4. Implement ONLY that task
5. Run the task Verification steps
6. Commit once with message including task id (e.g. "P3-T02 Journalist list UI")
7. Mark task done in implementation-state.md; update Last updated + phase counts
```

## Project shape (IntelliJ)

- Open **parent** `pom.xml` in IntelliJ IDEA Ultimate as a **Maven multi-module** project.  
- Modules: `gotham-common`, `gotham-web` (P0+); **`gotham-datagen`** independent Java app added in **P10** (last phase).  
- `imagebind-service/` is in-repo Docker/Python — not a Maven module.  
- Optional Compose profile `datagen`: Ollama (Qwen), ComfyUI (FLUX/Wan), Kokoro — see [`docs/synthetic-data-generation.md`](docs/synthetic-data-generation.md).  
- Datagen defaults: 15 journalists · 25 articles · 5 IMAGE + 5 AUDIO + 5 VIDEO (5 s) per article.

## Hard constraints

- No RDBMS · No `/admin` · No Elastic `semantic_text` · No auth  
- CRUD routes: `/journalist/**` and `/article/**` only  
- Journalist delete: **cascade-strip** nested bylines + reindex articles  
- Embeddings: ImageBind **1024-d**, built **in-repo**  
- Pagination: `size` ∈ {25, 50, 100} → ES `from`/`size`  
- Java 25 · Spring Boot 4.1.1 · Thymeleaf · multi-module Maven  
- Package: `com.gotham.newsmediabrowser`  
- ES endpoint + API key: hardcoded in `application.properties`  
- GCS SA JSON: **secret file** under `secrets/` (never commit real key)  
- **Fault tolerance:** every unexpected failure shows branded error page with **reason** (see `docs/ui-design-errors.md`); no Whitelabel stack dumps to users  
- **Synthetic data:** only in **P10** via independent Java app `gotham-datagen`; load through HTTP CRUD — never bypass to ES/GCS from the generator  

## Do not

- Skip updating the state file  
- Mark done without verification  
- Commit real `secrets/*.json` keys  
- Ship endpoints without going through global error handling  
- Implement `gotham-datagen` before P10 / before CRUD+media+embeddings are done  
- Rewrite the design docs unless a task says to  

## UI reference

Static mockups: `ui-mockups/` (port to Thymeleaf; keep light pastel chrome).
