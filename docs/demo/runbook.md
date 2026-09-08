# Demo runbook (P9-T02)

End-to-end steps to stand up a **low-resource demo** of the Gotham News & Media Browser and prove it
works: Compose up → index bootstrap → static seed → exercise the search modes + a CRUD smoke. No GPU
generative models; full synthetic load is **P10**.

Everything here talks to the app over HTTP only. Two helper scripts live next to this file:

- [`seed.sh`](./seed.sh) — loads the committed fixtures via `/journalist` + `/article` (P9-T01).
- [`smoke.sh`](./smoke.sh) — exercises health, all four search modes, the article-vector rejection,
  and a journalist CRUD round-trip; **exits non-zero on the first regression**.

---

## 1. Prerequisites

- **JDK 25** (`JAVA_HOME` → the Java 25 home) and the Maven wrapper (`./mvnw`).
- **Docker Desktop** (for `imagebind-service`; the M4 lab runs CPU inference in-container).
- An **Elastic Cloud Serverless** project: endpoint + API key.
- A **public GCS bucket** + a service-account JSON key (only needed if you want media uploads to
  succeed; text-only demos can skip it).
- `bash` 4+ and `curl` for the scripts.

## 2. Configure secrets (never committed)

Fill the untracked override at the repo root (values come from your Elastic/GCS projects):

```properties
# application-local.properties  (repo root; gitignored)
gotham.elasticsearch.endpoint=https://YOUR-ES-ENDPOINT
gotham.elasticsearch.api-key=YOUR_API_KEY
gotham.gcs.project-id=YOUR_GCP_PROJECT
gotham.gcs.bucket=YOUR_PUBLIC_BUCKET
gotham.gcs.credentials-file=secrets/gcp-sa.json
```

Place the real service-account key at `secrets/gcp-sa.json` (also gitignored). See
[`../../secrets/README.md`](../../secrets/README.md).

> **Weightless option:** set `gotham.imagebind.stub=true` to skip the ImageBind model entirely
> (deterministic offline vectors). Semantic/hybrid/vector then return results without downloading the
> 4.5 GB model — good for CI or a laptop demo.

## 3. Start the stack

### Option A — Docker Compose (app + ImageBind)

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"   # macOS
./mvnw -q -DskipTests -pl gotham-web -am package     # build the boot jar the image copies
docker compose config                                 # validate the stack
IMAGEBIND_BACKEND=stub docker compose up --build      # stub backend = fast, no model download
```

`gotham-web` serves on **:8080**, `imagebind-service` on **:8081**. Drop `IMAGEBIND_BACKEND=stub`
to run the real `imagebind_huge` model (needs ≥ 12 GB for Docker; first run downloads ~4.5 GB).

### Option B — Local boot jar (ImageBind optional)

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./mvnw -q -DskipTests -pl gotham-web -am package
java -jar gotham-web/target/gotham-web-0.0.1-SNAPSHOT.jar    # run from repo root so it loads application-local.properties
```

Optionally start just the embedder: `docker compose up imagebind-service`. Without it, full-text and
CRUD work fully; semantic/hybrid/vector degrade to a **branded HTTP 503** (see step 6).

## 4. Index bootstrap (automatic)

On startup `IndexBootstrapRunner` creates `gotham-journalists` and `gotham-media-browser` from the
committed mappings if missing, and is a **no-op** when they already exist (it never deletes). Confirm
in the log:

```
Elasticsearch index bootstrap complete: {JOURNALISTS=..., MEDIA_BROWSER=...}
```

If Elasticsearch is unreachable the app still starts and the header legend shows **Elasticsearch:
Unavailable** — fix credentials before seeding.

## 5. Load the static seed

```bash
BASE_URL=http://localhost:8080 ./docs/demo/seed.sh
```

Creates 4 journalists + 4 articles (2 with media). Media uploads need GCS configured; to seed text
only, set every `media` cell in `fixtures/articles.tsv` to `-`. See [`README.md`](./README.md).

## 6. Smoke test

```bash
BASE_URL=http://localhost:8080 ./docs/demo/smoke.sh
```

It prints an `ok` / `FAIL` line per check and exits non-zero if any fail. What it asserts:

| Check | Expected |
|-------|----------|
| `/api/health/elasticsearch` | **200** (required) |
| `/api/health/imagebind` | 200 = up · anything else = down (adjusts the expectations below) |
| `GET /` , article & multimedia **full-text** | **200** (Elasticsearch only) |
| article **`mode=vector`** | **400** branded (always rejected) |
| article/multimedia **semantic · hybrid** , multimedia **vector** upload | **200** when ImageBind is up, **503** branded when it is down |
| journalist **create → list → delete** | 302 redirects; the throwaway record is cleaned up |

The 200-vs-503 branch means the smoke passes both on a full stack and on a text-only stack, while
still proving the fault-tolerant 503 path when the embedder is absent.

## 7. Manual search walkthrough

Open <http://localhost:8080/>. On the **articles** panel search `transit funding`:

- **Full-text** → the transit article by BM25.
- **Semantic** → kNN over `article_embedding` (needs ImageBind).
- **Hybrid** → RRF of both.

On the **multimedia** panel search `museum skyline` (full-text / semantic / hybrid), or pick
**Vector** and drop `docs/demo/fixtures/media/skyline.png` to rank assets by the uploaded image.
Filters to try: **section = Culture**, and enable **DRAFT** in the status filter to reveal the
startups article.

## 8. Teardown

- Compose: `docker compose down` (add `-v` to drop the ImageBind weights volume).
- Local jar: stop the process (`Ctrl-C`, or `taskkill /PID <pid> /F` on Windows).
- The seeded journalists/articles persist in Elasticsearch; delete them from `/journalist` and
  `/article`, or leave them for the next demo.

## 9. Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| Header legend **Elasticsearch: Unavailable** | Wrong endpoint/API key in `application-local.properties`, or network. |
| `seed.sh` "could not capture id" | The journalist create did not persist (check ES health first). |
| Media articles fail at upload | GCS not configured, or the bucket is not public. Seed text-only or fix `secrets/`. |
| Semantic/vector return the branded 503 page | `imagebind-service` is down — start it (Option A) or set `gotham.imagebind.stub=true`. |
| Everything returns the branded error page | Expected fault tolerance — the page names the failing service; no stack traces reach the browser. |
