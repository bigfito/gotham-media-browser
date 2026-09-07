# Implementation State — Gotham News & Media Browser

**Plan:** [`implementation-plan.md`](./implementation-plan.md)  
**Architecture:** [`architecture-end-to-end.md`](./architecture-end-to-end.md)  
**ES search DSL:** [`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md)  
**Synthetic data (P10):** [`synthetic-data-generation.md`](./synthetic-data-generation.md)  
**Testing:** [`testing-strategy.md`](./testing-strategy.md)  
**Last updated:** 2026-09-07T23:05:00Z  
**Active phase:** P4 (in progress — P4-T01, P4-T02 done)  
**Prototype status:** `in_progress`  
**Next task:** `P4-T03` (Journalist edit + cascade-strip delete — deps P4-T02, P3-T03 done)

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
| P2 | Index bootstrap | 2/2 | done |
| P3 | `/journalist` — list + create + edit form | 3/3 | done |
| P4 | `/article` CRUD + journalist cascade-strip delete | 2/5 | in progress |
| P5 | GCS + multimedia | 0/3 | pending |
| P6 | ImageBind + embeddings | 0/3 | pending |
| P7 | Public FTS search | 0/4 | pending |
| P8 | Semantic · Hybrid · Vector | 0/3 | pending |
| P9 | Demo smoke + static fixtures + ES/ImageBind ITs | 0/4 | pending |
| P10 | Synthetic data generation (**last**) | 0/6 | pending |

**Totals:** 16 / **42** tasks done

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
| P2-T01 | P2 | Mapping JSON on classpath | done | P0-T01 | JavaMentor | 2026-09-07T19:45:00Z | 2026-09-07T19:55:00Z | Single source of truth kept at repo-root elasticsearch/*.mapping.json (docs still link there); gotham-common pom adds ../elasticsearch as a resource dir (targetPath elasticsearch, *.mapping.json) so both mappings ship on the classpath. IndexDefinition enum (JOURNALISTS, MEDIA_BROWSER) resolves resource path + loadMappingJson() (fail-fast if absent) — reused by P2-T02. IndexDefinitionTest (5 cases: parametrized load+valid JSON+_meta.index match, path convention, 1024-d vectors, missing-resource contract). mvn -pl gotham-common test green (14). Verified mappings present in target/classes and packaged jar under elasticsearch/. |
| P2-T02 | P2 | Idempotent index bootstrap | done | P1-T02, P2-T01 | JavaMentor | 2026-09-07T20:05:00Z | 2026-09-07T20:35:00Z | IndexBootstrapper (gotham-common, @Component): per IndexDefinition exists()->skip / create() from mapping JSON; never deletes/modifies. Feeds settings+mappings separately via withJson(Reader) (CreateIndexRequest.Builder has no raw-JSON setter; transport JacksonJsonpMapper.jsonProvider() throws, so use standalone parsson provider). serverlessSafe() strips number_of_shards/number_of_replicas (Serverless rejects them; mapping files keep them for portability). IndexBootstrapRunner (gotham-web, ApplicationRunner) runs at startup and degrades (logs ERROR, app keeps running) if ES down — verified live: contextLoads + placeholder boot start despite failure. IndexBootstrapperTest (4: create-all, skip-all + never-delete, serverlessSafe strip, failure wraps in IndexBootstrapException naming index). mvn test green (35). LIVE against real Serverless: boot#1 {JOURNALISTS=ALREADY_EXISTS(from earlier run), MEDIA_BROWSER=CREATED}; boot#2 both ALREADY_EXISTS (idempotent). Both indexes GET 200; article_embedding + asset_vector dims=1024. FIXED real mapping bug in gotham-media-browser.mapping.json: copy_to was inside multi-field .text subfields (section/tags/location/source) which ES forbids — moved copy_to to the parent keyword field. |
| P3-T01 | P3 | Journalist domain + repository | done | P2-T02 | JavaMentor | 2026-09-07T20:45:00Z | 2026-09-07T21:05:00Z | Journalist record (gotham-common: first/last/email/bio/createdAt/updatedAt; derived fullName() joining non-blank parts; id = ES auto _id, null until persisted; withId/withTimestamps). JournalistPage(items,total) for pagination. JournalistRepository (@Repository): create/findById/update/deleteById/findAll(from,size, newest-first, trackTotalHits). Document mapping via Map<String,Object> (snake_case, ISO-8601 dates, full_name written, id never in strict _source) — mapper-agnostic, no jackson annotations. Refresh.True on writes for immediate consistency. ES failures -> DependencyException(503). Unit tests: JournalistTest (4, fullName edge cases) + JournalistRepositoryTest (3, toDocument/fromSource round-trip). Live JournalistRepositoryIT (@Tag integration, assumeTrue on ES_ENDPOINT/ES_API_KEY + reachability; runnable via -Dtest=JournalistRepositoryIT): create->read->update->search->delete round-trip PASSED against real Serverless (auto-cleans its doc). mvn test green (42; IT excluded by *IT naming). |
| P3-T02 | P3 | Journalist list UI (GET /journalist) | done | P3-T01, P0-T04, P1-T04 | JavaMentor | 2026-09-07T21:15:00Z | 2026-09-07T21:35:00Z | JournalistController GET /journalist: page/size params (size in {25,50,100}, else 25; page clamped >=1; from=(page-1)*size), calls JournalistRepository.findAll, maps to JournalistRow(id,fullName,email,updated formatted MMM d yyyy UTC). Thymeleaf journalist/list.html ported from ui-mockups/journalist.html: chrome fragments + crud-hero + size select (GET form, resets to page 1) + data-table + Prev/Next pagination + empty-state; edit/delete actions target id routes (wired in P4-T03). Added .empty-state CSS. JournalistControllerTest (5 @WebMvcTest slice: non-empty+chrome+paging meta, empty state, from-offset on page2/size50, invalid size fallback, page<=0 clamp). mvn test green (47). LIVE: seeded 2 journalists into real ES -> list shows 2 newest-first with formatted dates + delete routes; size=50 respected; size=7 -> 25; empty state before/after (seeds cleaned up). Chrome extension offline so no screenshot; curl-verified render. |
| P3-T03 | P3 | Journalist create | done | P3-T02 | JavaMentor | 2026-09-07T21:45:00Z | 2026-09-07T22:05:00Z | GET /journalist/new + POST /journalist on JournalistController. JournalistForm (Bean Validation: firstName/lastName @NotBlank+@Size, email @NotBlank+@Email, bio @Size; trims + toNewJournalist()). Valid -> repository.create -> PRG redirect:/journalist + flash "Created <name>."; invalid -> redisplay journalist/new with in-form field errors (never the global error page), no persist. journalist/new.html ported from ui-mockups/journalist-new.html (th:object/th:field/#fields errors). Added spring-boot-starter-validation to gotham-web. Added .field-error + .flash CSS; flash banner on list; fixed list Edit link to design route /journalist/{id}. Tests: JournalistCreateControllerTest (@WebMvcTest, 3: empty form+chrome, valid->redirect+flash+create, invalid->in-form errors+no persist). mvn test green (50). LIVE vs real Serverless: GET form renders 4 fields; POST valid -> 302 /journalist, doc created and shown; flash banner "Created Clark Kent." after redirect; Edit links carry real ES _id (=> _id on edit); POST invalid -> 200 in-form errors, no persist. Demo docs cleaned up. |
| P4-T01 | P4 | Article domain + projection helpers | done | P3-T01 | JavaMentor | 2026-09-07T22:15:00Z | 2026-09-07T22:35:00Z | gotham-common article package: ArticleStatus{DRAFT,PUBLISHED,ARCHIVED} + ContributionRole{AUTHOR,CO_AUTHOR,CONTRIBUTING} enums (lenient fromValue; role optional->null). ArticleJournalist nested byline snapshot (fromJournalist(j,order,role); fullName() derived). ArticleMetadata (section/tags/location/source/seo*/canonicalUrl; empty(); never-null tags). Article aggregate (core + metadata + journalists; newArticle factory, withId/withTimestamps/withJournalists; never-null collections; projections NOT stored to avoid drift). ArticleProjections stateless helper: orderedByByline, journalistNames, journalistBios (feed journalist_names/bios -> copy_to journalist_search_text; multimedia_text deferred to P5). No repository/media yet. Tests: ArticleEnumsTest(4), ArticleProjectionsTest(4), ArticleTest(3). mvn test green (61). |
| P4-T02 | P4 | Article repository | done | P4-T01, P2-T02 | JavaMentor | 2026-09-07T22:45:00Z | 2026-09-07T23:05:00Z | ArticleRepository (@Repository) over gotham-media-browser: create/findById/update/deleteById/findAll(status?,journalistId?,from,size newest-first, trackTotalHits) + findByJournalistId (sweeps all pages via nested query, for cascade-strip P4-T03). Doc via Map<String,Object>: flattens metadata, writes status enum name + ISO dates, rebuilds journalist_names/journalist_bios projections + ordered nested journalists[] each write (no drift). fromSource round-trips incl. nested bylines (ordered) + roles. Query DSL verified via javap: bool.filter([term status, nested journalists.journalist_id]) else match_all; sort created_at desc. Refresh.True; ES failure -> DependencyException(503). ArticlePage(items,total). Unit ArticleRepositoryTest (3: toDocument projections+ordered nested, round-trip, null-role/empty-bylines). Live ArticleRepositoryIT (@Tag integration, assumeTrue): create->read->update(DRAFT->PUBLISHED)->status filter->nested journalist query->delete PASSED vs real Serverless (auto-clean). mvn test green (64; IT excluded). |
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
