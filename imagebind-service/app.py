"""In-repo Meta ImageBind embedding service (P6-T01).

A small synchronous HTTP API that turns text / image / audio / video into
``float[1024]`` vectors for the Gotham prototype. ``gotham-web`` calls it on
article write (P6-T03) and the datagen orchestrator drives it via the web layer
(P10). CPU inference by default.

Endpoints
---------
* ``GET  /health``       — liveness + model readiness (always 200 while serving).
* ``POST /embed/text``   — JSON ``{"text": "..."}``           → embedding.
* ``POST /embed/image``  — multipart ``file`` (image/*)       → embedding.
* ``POST /embed/audio``  — multipart ``file`` (audio/*)       → embedding.
* ``POST /embed/video``  — multipart ``file`` (video/*)       → embedding.

Every embedding response has the shape
``{"modality": "text", "dim": 1024, "embedding": [ ... ]}``.

The heavy model loads in the background so ``/health`` answers immediately; until
it is ready, embedding calls return HTTP 503 with a stable error ``code``.
"""
from __future__ import annotations

import logging
import threading

from embedders import EMBEDDING_DIM, Embedder, EmbedderError, ImageBindEmbedder, build_embedder
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("imagebind-service")

SERVICE_NAME = "imagebind-service"

app = FastAPI(title="Gotham ImageBind service", version="1.0.0")

# The embedder is chosen once at import time; the real model warms up in a thread.
_embedder: Embedder = build_embedder()


class TextRequest(BaseModel):
    """JSON body for ``POST /embed/text``."""

    text: str


class EmbedResponse(BaseModel):
    """Uniform embedding payload shared by every modality."""

    modality: str
    dim: int
    embedding: list[float]


@app.on_event("startup")
def warm_up_model() -> None:
    """Loads the real model in a background thread so startup stays fast."""
    if isinstance(_embedder, ImageBindEmbedder):
        def _load() -> None:
            try:
                _embedder.load()
            except EmbedderError as exc:
                log.error("Model load failed [%s]: %s", exc.code, exc.message)

        threading.Thread(target=_load, name="imagebind-model-loader", daemon=True).start()


@app.get("/health")
def health() -> JSONResponse:
    """Liveness for the compose healthcheck and the gotham-web header legend."""
    body = {
        "status": "UP",
        "service": SERVICE_NAME,
        "backend": _embedder.name,
        "dim": EMBEDDING_DIM,
        "model_loaded": _embedder.is_ready(),
    }
    return JSONResponse(body)


@app.post("/embed/text", response_model=EmbedResponse)
def embed_text(request: TextRequest) -> EmbedResponse:
    if request.text is None or not request.text.strip():
        raise HTTPException(status_code=400, detail="Field 'text' must not be empty.")
    return _respond("text", _guard(lambda: _embedder.embed_text(request.text)))


@app.post("/embed/image", response_model=EmbedResponse)
async def embed_image(file: UploadFile = File(...)) -> EmbedResponse:
    data = await _read(file)
    return _respond("image", _guard(lambda: _embedder.embed_image(data, file.filename or "image")))


@app.post("/embed/audio", response_model=EmbedResponse)
async def embed_audio(file: UploadFile = File(...)) -> EmbedResponse:
    data = await _read(file)
    return _respond("audio", _guard(lambda: _embedder.embed_audio(data, file.filename or "audio")))


@app.post("/embed/video", response_model=EmbedResponse)
async def embed_video(file: UploadFile = File(...)) -> EmbedResponse:
    data = await _read(file)
    return _respond("video", _guard(lambda: _embedder.embed_video(data, file.filename or "video")))


async def _read(file: UploadFile) -> bytes:
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Uploaded file is empty.")
    return data


def _guard(action):
    """Runs an embedding call, translating backend errors into HTTP responses."""
    try:
        return action()
    except EmbedderError as exc:
        # Model not ready yet -> 503 (retryable); everything else -> 500.
        status = 503 if exc.code == "IB-503" else 500
        log.warning("Embedding failed [%s]: %s", exc.code, exc.message)
        raise HTTPException(status_code=status, detail={"code": exc.code, "message": exc.message})


def _respond(modality: str, embedding: list[float]) -> EmbedResponse:
    if len(embedding) != EMBEDDING_DIM:
        # A backend must always return the agreed dimension; guard against drift.
        raise HTTPException(
            status_code=500,
            detail={"code": "IB-DIM", "message": f"Expected {EMBEDDING_DIM} dims, got {len(embedding)}."},
        )
    return EmbedResponse(modality=modality, dim=EMBEDDING_DIM, embedding=embedding)
