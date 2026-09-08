# AGENTS.md — Gotham News & Media Browser

Instructions for AI coding agents (Claude Code, Google Antigravity, Cursor, Codex, etc.).

## Mission

Implement the **Gotham News & Media Browser** prototype from the approved design under `gotham-news-media-browser/`.

**Progress (2026-09-08):** **P0–P8 + P9-T01/T02 complete (34 / 42)** — all four search modes ship; `docs/demo/` has fixtures + `seed.sh` + `smoke.sh` + `runbook.md`. Next: **P9-T03** ES + ImageBind + web integration tests. Always re-read [`docs/implementation-state.md`](docs/implementation-state.md) before picking work.

## Read in this order

1. [`docs/implementation-state.md`](docs/implementation-state.md) — **what to do next**  
2. [`docs/implementation-plan.md`](docs/implementation-plan.md) — task details & verification  
3. [`docs/architecture-end-to-end.md`](docs/architecture-end-to-end.md) — system design  
4. Spec linked from the task (CRUD / search IA / mappings / [`docs/elasticsearch-search-methods.md`](docs/elasticsearch-search-methods.md) / [`docs/synthetic-data-generation.md`](docs/synthetic-data-generation.md) / [`docs/testing-strategy.md`](docs/testing-strategy.md))

## Work loop

```text
1. Open implementation-state.md
2. Pick lowest-ID pending task whose Depends-on tasks are done
3. Claim: status=in_progress, claimed_by=<you>, started_at=<UTC>
4. Implement ONLY that task
5. Run the task Verification steps (**include `mvn test`** for coding tasks)  
6. Commit once with message including task id (e.g. "P3-T02 Journalist list UI")  
7. Mark task done in implementation-state.md; update Last updated + phase counts  
8. If that task was the LAST in its phase (phase count now N/N): **pause and compact the context window** before starting the next phase — in Claude Code run `/compact`; on other harnesses use their equivalent context-compaction/summarize step (or start a fresh session from implementation-state.md). Then resume at step 1.  
```

## Project shape (IntelliJ)

- Open **parent** `pom.xml` in IntelliJ IDEA Ultimate as a **Maven multi-module** project.  
- Modules: `gotham-common`, `gotham-web` (P0+); **`gotham-datagen`** Java **console** module (not Spring Boot) in **P10**.  
- `imagebind-service/` and `comfyui-service/` are in-repo Docker/Python — not Maven modules.  
- P10 helpers: **mandatory Docker** Compose profile `datagen` — Ollama (Qwen 7B), `comfyui-service` (SDXL-Turbo + Wan 1.3B), Kokoro — see [`docs/synthetic-data-generation.md`](docs/synthetic-data-generation.md).  
- Lab hardware: **MacBook Pro M4 · 32 GB · no NVIDIA** (CPU inference inside containers).  
- Datagen defaults: 15 journalists · 25 articles · 5 IMAGE + 5 AUDIO + 5 VIDEO (5 s) per article.  
- Plan/state: **42** tasks (P0–P10).  
- Testing: unit + MockMvc always; env-gated `*IT` in `gotham-common`; Failsafe profiles in P9/P10 — [`docs/testing-strategy.md`](docs/testing-strategy.md).

## Build & verify (JDK 25)

Set `JAVA_HOME` to the Java 25 LTS home first (Maven must run on JDK 25, not a newer default):

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"   # macOS
# Windows: set JAVA_HOME to the JDK 25 install (Maven must not pick a newer default)
mvn test                     # unit + web-slice (required before marking a coding task done)
mvn -DskipTests package      # build the gotham-web boot jar
docker compose config        # validate the Compose stack (gotham-web + imagebind-service)
```

Live `*IT` classes skip unless env vars are set. Failsafe profiles (`it-es`, `it-imagebind`, `it-datagen-helpers`) arrive in P9/P10 — see [`docs/testing-strategy.md`](docs/testing-strategy.md).

## Source of truth

[`docs/implementation-state.md`](docs/implementation-state.md) is **authoritative** for progress: its task board (42 tasks, P0–P10) drives what to do next and must match the task IDs and Depends-on in [`docs/implementation-plan.md`](docs/implementation-plan.md). Update it on every task transition.

## Hard constraints

- No RDBMS · No `/admin` · No Elastic `semantic_text` · No auth  
- CRUD routes: `/journalist/**` and `/article/**` only  
- Journalist delete: **cascade-strip** nested bylines + reindex articles  
- Embeddings: ImageBind **1024-d**, built **in-repo**  
- Pagination: `size` ∈ {25, 50, 100} → ES `from`/`size`  
- Java 25 · Spring Boot 4.1.1 · Thymeleaf · multi-module Maven  
- Package: `com.gotham.newsmediabrowser`  
- ES endpoint + API key: **placeholders** in committed `application.properties`; real values in untracked `application-local.properties` (or env) — **never commit secrets**  
- GCS SA JSON: **secret file** under `secrets/` (never commit real key)  
- Build on **JDK 25** (`JAVA_HOME` → the Java 25 LTS home), matching Spring Boot 4.1.1's baseline  
- **Fault tolerance:** every unexpected failure shows branded error page with **reason** (see `docs/ui-design-errors.md`); no Whitelabel stack dumps to users  
- **Synthetic data:** only in **P10** via Java **console** `gotham-datagen` (not Spring Boot); load through HTTP CRUD — never bypass to ES/GCS from the generator  
- **Testing:** unit tests for **all** backend + frontend (MockMvc); integration tests for Elasticsearch, ImageBind, and datagen helpers  

## Do not

- Skip updating the state file  
- Mark done without verification **or without unit tests** for code changes  
- Commit real `secrets/*.json` keys  
- Ship endpoints without going through global error handling  
- Implement `gotham-datagen` before P10 / before CRUD+media+embeddings are done  
- Make `gotham-datagen` a Spring Boot app (it must be a **console** `main`)  
- Run Ollama/ComfyUI/Kokoro as native macOS apps (Docker containers are mandatory)  
- Rewrite the design docs unless a task says to  

## UI reference

Static mockups: `ui-mockups/` (port to Thymeleaf; keep light pastel chrome).
