# imagebind-service

In-repo **Meta ImageBind** wrapper (P6-T01). A small synchronous HTTP API that
turns **text / image / audio / video** into `float[1024]` vectors for the Gotham
prototype. CPU inference by default (M4 lab, no GPU). Not a Maven module — it is
built and run with Docker.

## API

| Method & path      | Body                          | Response |
|--------------------|-------------------------------|----------|
| `GET  /health`     | —                             | `{"status":"UP","service":"imagebind-service","backend":"imagebind","dim":1024,"model_loaded":true}` |
| `POST /embed/text` | JSON `{"text":"…"}`           | `EmbedResponse` |
| `POST /embed/image`| multipart `file` (`image/*`)  | `EmbedResponse` |
| `POST /embed/audio`| multipart `file` (`audio/*`)  | `EmbedResponse` |
| `POST /embed/video`| multipart `file` (`video/*`)  | `EmbedResponse` |

`EmbedResponse` = `{"modality":"text","dim":1024,"embedding":[ …1024 floats… ]}`.

`GET /health` returns `200` as soon as the server is up. The real model warms up
in a background thread, so `model_loaded` may be `false` briefly after start;
during that window the embed endpoints return **HTTP 503** with a stable
`{"code":"IB-503", …}` so callers can retry.

## Backends (Strategy)

Selected by the `IMAGEBIND_BACKEND` environment variable:

- **`imagebind`** (default) — the real `imagebind_huge` model. Downloads ~4.5 GB
  of weights on first embed (persisted via the `imagebind-cache` volume). Needs
  the PyTorch + ImageBind stack, installed when the image is built with
  `INSTALL_IMAGEBIND=true` (the default).
- **`stub`** — a deterministic, dependency-free stand-in: it hashes the input
  into a stable, L2-normalized 1024-d vector. Not semantic — it exists so CI and
  smoke checks exercise the full HTTP contract in seconds, mirroring the Java
  client's `gotham.imagebind.stub` mode (P6-T02).

`IMAGEBIND_DEVICE` (default `cpu`) selects the torch device for the real backend.

## Run

Real model (production default), via the repo's Compose stack:

```bash
docker compose up imagebind-service
curl -s localhost:8081/health
curl -s -X POST localhost:8081/embed/text -H 'Content-Type: application/json' -d '{"text":"gotham transit vote"}' | python -c "import sys,json;print(len(json.load(sys.stdin)['embedding']))"
# -> 1024
```

> **Memory:** loading `imagebind_huge` peaks well above its 4.5 GB on-disk size.
> Give Docker Desktop **≥ 12 GB** (Settings → Resources) or the loader is
> OOM-killed (exit 137). The stub backend needs almost none.

Fast image without the ML stack (deterministic stub only):

```bash
docker build --build-arg INSTALL_IMAGEBIND=false -t imagebind-service:stub ./imagebind-service
docker run --rm -e IMAGEBIND_BACKEND=stub -p 8081:8081 imagebind-service:stub
```

- Port: `8081`
- Files: `app.py` (FastAPI routes), `embedders.py` (backends),
  `requirements.txt` (base), `requirements-imagebind.txt` (real model).
