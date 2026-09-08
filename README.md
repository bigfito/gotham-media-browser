# Gotham News & Media Browser

Prototype design package for a single-brand news & multimedia browser on **Elastic Cloud Serverless** + **public GCS** (no RDBMS).

## Start here

**End-to-end architecture:** [`docs/architecture-end-to-end.md`](docs/architecture-end-to-end.md)  
**Implementation plan (phased):** [`docs/implementation-plan.md`](docs/implementation-plan.md)  
**Progress / handoff state:** [`docs/implementation-state.md`](docs/implementation-state.md) — **42 / 42** tasks done (P0–P10 complete). Remaining work is lab/operator (overnight synthetic load), not a numbered task.  
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
| Synthetic data (P10) | `docs/synthetic-data-generation.md` · [`docs/datagen-runbook.md`](docs/datagen-runbook.md) |
| Testing | `docs/testing-strategy.md` |
| Engineering notes (gotchas) | `docs/engineering-notes.md` |
| Mappings | `elasticsearch/*.mapping.json` |
| Mockups | `ui-mockups/` (open `index.html`) |

## Locked stack

Java 25 · Spring Boot 4.1.1 · Thymeleaf · **Maven multi-module** (`gotham-common` + `gotham-web` + **`gotham-datagen` console** — plain-`main`, non-Boot) · ES Java client · Docker Compose (`gotham-web` + `imagebind-service`; profile **`datagen`** = Ollama / ComfyUI / Kokoro) · ImageBind 1024-d · M4 32 GB lab · **unit + integration tests** (ES / ImageBind / datagen helpers) · **42 / 42** implementation tasks (P0–P10) done

Open the parent `pom.xml` in **IntelliJ IDEA Ultimate** as a Maven project.

## Credentials (prototype)

- Elasticsearch endpoint + API key: **placeholders** in committed `application.properties`; real values in untracked `application-local.properties` (or env)  
- GCS bucket/project: placeholders in `application.properties`; real values in the untracked override  
- GCS service account JSON: **secret file** under `secrets/gcp-sa.json` (gitignored)

## Run it

New here? Follow these five steps to get the app running locally. For the full guide (Docker
Compose, per-mode walkthrough, troubleshooting) see [`docs/demo/runbook.md`](docs/demo/runbook.md).

> In every command below, `./mvnw` is the Maven wrapper — no separate Maven install needed.
> On **Windows** use `mvnw.cmd` instead of `./mvnw` (or run the `./mvnw` form inside **Git Bash**).

### 1. Install the tools

- **JDK 25** — the only hard requirement. Maven must use it:  
  `export JAVA_HOME="$(/usr/libexec/java_home -v 25)"` (macOS). Check: `java -version` prints `25`.
- **Git Bash + curl** (Windows only) — to run the `.sh` helper scripts. macOS/Linux already have them.
- **Docker Desktop** — *optional* for a first look (step 3 uses the boot jar + ImageBind stub). Required
  for the real embedder, Compose stack, or P10 synthetic generation.

### 2. Add your Elasticsearch credentials (the only thing you must configure)

The app talks to an **Elastic Cloud Serverless** project. Copy the example config to an untracked
file at the repo root:

```bash
cp application-local.properties.example application-local.properties
```

Then open `application-local.properties` and fill in just the **two Elasticsearch lines** with your
project's endpoint and API key:

```properties
gotham.elasticsearch.endpoint=https://YOUR-ES-ENDPOINT
gotham.elasticsearch.api-key=YOUR_API_KEY
```

That's all you need. The example already sets `gotham.imagebind.stub=true`, which lets
semantic/hybrid/vector search run **without** the heavy ImageBind model — perfect for a first look.
Google Cloud Storage stays optional: leave its placeholders and everything works except uploading
media. (Want real embeddings and media later? The [runbook](docs/demo/runbook.md) explains it.)

### 3. Build, test, and start the app

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"   # macOS; Windows: point JAVA_HOME at JDK 25
./mvnw test                              # unit + web-slice (244 tests) — should say BUILD SUCCESS
./mvnw -DskipTests package               # builds the runnable app
java -jar gotham-web/target/gotham-web-0.0.1-SNAPSHOT.jar   # start it (run from the repo root)
```

Leave that last command running. It prints `Started GothamMediaBrowserApplication` and creates the
Elasticsearch indexes for you. The app is now at **<http://localhost:8080/>**.

### 4. Load the sample data and open the app

In a **second terminal** (leave the app running in the first):

```bash
TEXT_ONLY=1 BASE_URL=http://localhost:8080 ./docs/demo/seed.sh   # adds 4 journalists + 4 articles
```

`TEXT_ONLY=1` skips media uploads so you don't need Google Cloud Storage. (Configured GCS? Drop
`TEXT_ONLY=1` to also upload the sample image and audio.)

Now open <http://localhost:8080/> and, on the Articles panel, search `transit funding`. Try the
Full-text, Semantic, and Hybrid modes. To create your own content, use `New journalist` and
`New article` in the UI.

### 5. (Optional) Check everything works

```bash
BASE_URL=http://localhost:8080 ./docs/demo/smoke.sh   # green = every route works; non-zero = a problem
```

---

**Other ways to run** (details in the [runbook](docs/demo/runbook.md)):

```bash
# Whole stack in Docker (app :8080 + imagebind-service :8081), no local Java needed to run:
IMAGEBIND_BACKEND=stub docker compose up --build

# Integration tests against live dependencies (ITs skip when creds / helpers are absent):
ES_ENDPOINT=… ES_API_KEY=… ./mvnw -Pit-es verify
IMAGEBIND_BASE_URL=http://localhost:8081 ES_ENDPOINT=… ES_API_KEY=… ./mvnw -Pit-imagebind verify
./mvnw -Pit-datagen-helpers verify       # Ollama / ComfyUI / Kokoro + small HTTP orchestrator
```

Full synthetic load (15 journalists · 25 articles · 375 media assets) is a separate console app —
see [`docs/datagen-runbook.md`](docs/datagen-runbook.md).

## Routes

- `GET /` — dual search panels (articles + multimedia)  
- `GET /results` · `POST /results` — full-text, **semantic (kNN)**, and **hybrid (RRF)** results with filters, sort, pagination (`size` 25/50/100); multimedia **vector** search takes an uploaded file via multipart `POST`. Article `mode=vector` is rejected (**HTTP 400**).  
- `/journalist/**` — CRUD on `gotham-journalists` (delete **cascade-strips** nested bylines)  
- `/article/**` — CRUD on denormalized `gotham-media-browser` (media → public GCS; ImageBind embeddings on write)  
- `/api/health/elasticsearch` · `/api/health/imagebind` — chrome availability legends  

No `/admin`. Synthetic load: Java **console** `gotham-datagen` (**not** Spring Boot) → HTTP CRUD only (defaults 15 journalists · 25 articles · 5+5+5 media). License: MIT (see repository `LICENSE`).
