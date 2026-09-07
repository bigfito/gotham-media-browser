# AGENTS.md — Gotham News & Media Browser

Instructions for AI coding agents (Claude Code, Google Antigravity, Cursor, Codex, etc.).

## Mission

Implement the **Gotham News & Media Browser** prototype from the approved design under `gotham-news-media-browser/`.

## Read in this order

1. [`docs/implementation-state.md`](docs/implementation-state.md) — **what to do next**  
2. [`docs/implementation-plan.md`](docs/implementation-plan.md) — task details & verification  
3. [`docs/architecture-end-to-end.md`](docs/architecture-end-to-end.md) — system design  
4. Spec linked from the task (CRUD / search IA / mappings)

## Work loop

```text
1. Open implementation-state.md
2. Pick lowest-ID pending task whose Depends-on tasks are done
3. Claim: status=in_progress, claimed_by=<you>, started_at=<UTC>
4. Implement ONLY that task
5. Run the task Verification steps
6. Commit with message including task id (e.g. "P3-T02 Journalist list UI")
7. Mark task done in implementation-state.md; update Last updated + phase counts
```

## Hard constraints

- No RDBMS · No `/admin` · No Elastic `semantic_text` · No auth  
- CRUD routes: `/journalist/**` and `/article/**` only  
- Embeddings: ImageBind **1024-d**  
- Pagination: `size` ∈ {25, 50, 100} → ES `from`/`size`  
- Java 25 · Spring Boot 4.1.1 · Thymeleaf · Maven  

## Do not

- Skip updating the state file  
- Mark done without verification  
- Invent production credentials  
- Rewrite the design docs unless a task says to  

## UI reference

Static mockups: `ui-mockups/` (port to Thymeleaf; keep light pastel chrome).
