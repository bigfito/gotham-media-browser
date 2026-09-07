"""Embedding backends for the in-repo ImageBind service (P6-T01).

Two interchangeable strategies implement the same :class:`Embedder` contract, so
the HTTP layer never depends on how a vector is produced:

* :class:`ImageBindEmbedder` — the real Meta ImageBind ``imagebind_huge`` model
  (CPU by default). Text / image / audio / video → ``float[1024]``. Heavy: it
  needs the PyTorch + ImageBind stack and downloads ~4.5 GB of weights on first
  use.
* :class:`DeterministicEmbedder` — a lightweight stand-in that hashes the input
  into a stable unit vector of the same 1024 dimensions, with no ML dependencies.
  It lets CI and quick smoke checks exercise the full HTTP contract in seconds,
  mirroring the Java client's ``gotham.imagebind.stub`` mode used in P6-T02.

The active backend is chosen by :func:`build_embedder` from the
``IMAGEBIND_BACKEND`` environment variable (``imagebind`` by default).
"""
from __future__ import annotations

import hashlib
import logging
import os
from abc import ABC, abstractmethod
from typing import List

log = logging.getLogger("imagebind-service.embedders")

#: Every modality shares this embedding size in ImageBind.
EMBEDDING_DIM = 1024


class EmbedderError(RuntimeError):
    """Raised with a stable error ``code`` when embedding cannot be produced."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


class Embedder(ABC):
    """A modality-aware embedding backend producing 1024-d float vectors."""

    #: Human-readable backend name, surfaced on ``GET /health``.
    name: str = "abstract"

    def is_ready(self) -> bool:
        """Whether embeddings can be served right now (e.g. weights loaded)."""
        return True

    @abstractmethod
    def embed_text(self, text: str) -> List[float]:
        ...

    @abstractmethod
    def embed_image(self, data: bytes, filename: str) -> List[float]:
        ...

    @abstractmethod
    def embed_audio(self, data: bytes, filename: str) -> List[float]:
        ...

    @abstractmethod
    def embed_video(self, data: bytes, filename: str) -> List[float]:
        ...


class DeterministicEmbedder(Embedder):
    """Hash-seeded stand-in vectors — deterministic, dependency-free, CI-friendly.

    The same input always yields the same unit vector, so tests can assert on
    stability without the multi-gigabyte ImageBind model. These vectors are NOT
    semantically meaningful; use the ``imagebind`` backend for real search.
    """

    name = "stub"

    def embed_text(self, text: str) -> List[float]:
        return self._vector(("text:" + (text or "")).encode("utf-8"))

    def embed_image(self, data: bytes, filename: str) -> List[float]:
        return self._vector(b"image:" + data)

    def embed_audio(self, data: bytes, filename: str) -> List[float]:
        return self._vector(b"audio:" + data)

    def embed_video(self, data: bytes, filename: str) -> List[float]:
        return self._vector(b"video:" + data)

    def _vector(self, seed_bytes: bytes) -> List[float]:
        """Builds a stable, L2-normalized 1024-d vector from a content hash.

        The SHA-256 digest seeds a repeatable byte stream that is mapped to the
        [-1, 1] range and then normalized, so every vector is a unit vector like
        a real embedding.
        """
        digest = hashlib.sha256(seed_bytes).digest()
        raw = bytearray()
        counter = 0
        while len(raw) < EMBEDDING_DIM:
            raw.extend(hashlib.sha256(digest + counter.to_bytes(4, "big")).digest())
            counter += 1
        values = [(byte / 127.5) - 1.0 for byte in raw[:EMBEDDING_DIM]]
        norm = sum(value * value for value in values) ** 0.5
        if norm == 0.0:
            return values
        return [value / norm for value in values]


class ImageBindEmbedder(Embedder):
    """The real Meta ImageBind ``imagebind_huge`` model (CPU by default).

    The PyTorch + ImageBind imports and the ~4.5 GB weight download happen lazily
    on :meth:`load`, so the HTTP server can start and answer ``/health`` while the
    model is still warming up. Until it is ready, embedding calls raise
    :class:`EmbedderError` (``IB-503``) and the API maps that to HTTP 503.
    """

    name = "imagebind"

    def __init__(self, device: str = "cpu"):
        self._device = device
        self._model = None
        self._data = None
        self._modality_type = None
        self._torch = None

    def is_ready(self) -> bool:
        return self._model is not None

    def load(self) -> None:
        """Loads the model once. Safe to call again (no-op when already loaded)."""
        if self._model is not None:
            return
        try:
            import torch
            from imagebind import data as imagebind_data
            from imagebind.models import imagebind_model
            from imagebind.models.imagebind_model import ModalityType
        except ImportError as exc:  # the heavy stack is not installed in this image
            raise EmbedderError(
                "IB-001",
                "ImageBind dependencies are not installed. Build the image with "
                "INSTALL_IMAGEBIND=true or run with IMAGEBIND_BACKEND=stub.",
            ) from exc

        log.info("Loading imagebind_huge weights on device '%s' (first run downloads ~4.5 GB)…", self._device)
        model = imagebind_model.imagebind_huge(pretrained=True)
        model.eval()
        model.to(self._device)

        self._torch = torch
        self._data = imagebind_data
        self._modality_type = ModalityType
        self._model = model
        log.info("ImageBind model ready.")

    def embed_text(self, text: str) -> List[float]:
        self._ensure_ready()
        inputs = {self._modality_type.TEXT: self._data.load_and_transform_text([text], self._device)}
        return self._run(inputs, self._modality_type.TEXT)

    def embed_image(self, data: bytes, filename: str) -> List[float]:
        self._ensure_ready()
        with self._as_temp_file(data, filename) as path:
            inputs = {self._modality_type.VISION: self._data.load_and_transform_vision_data([path], self._device)}
            return self._run(inputs, self._modality_type.VISION)

    def embed_audio(self, data: bytes, filename: str) -> List[float]:
        self._ensure_ready()
        with self._as_temp_file(data, filename) as path:
            inputs = {self._modality_type.AUDIO: self._data.load_and_transform_audio_data([path], self._device)}
            return self._run(inputs, self._modality_type.AUDIO)

    def embed_video(self, data: bytes, filename: str) -> List[float]:
        self._ensure_ready()
        with self._as_temp_file(data, filename) as path:
            inputs = {self._modality_type.VISION: self._data.load_and_transform_video_data([path], self._device)}
            return self._run(inputs, self._modality_type.VISION)

    def _run(self, inputs, modality) -> List[float]:
        try:
            with self._torch.no_grad():
                embeddings = self._model(inputs)
            vector = embeddings[modality][0]
            return vector.detach().cpu().tolist()
        except EmbedderError:
            raise
        except Exception as exc:  # noqa: BLE001 — any inference failure is one error to the caller
            raise EmbedderError("IB-500", f"ImageBind inference failed: {exc}") from exc

    def _ensure_ready(self) -> None:
        if self._model is None:
            raise EmbedderError("IB-503", "ImageBind model is still loading; retry shortly.")

    def _as_temp_file(self, data: bytes, filename: str):
        import contextlib
        import tempfile

        suffix = os.path.splitext(filename or "")[1]

        @contextlib.contextmanager
        def _managed():
            handle = tempfile.NamedTemporaryFile(suffix=suffix, delete=False)
            try:
                handle.write(data)
                handle.flush()
                handle.close()
                yield handle.name
            finally:
                with contextlib.suppress(OSError):
                    os.unlink(handle.name)

        return _managed()


def build_embedder() -> Embedder:
    """Selects the backend from ``IMAGEBIND_BACKEND`` (``imagebind`` by default)."""
    backend = os.getenv("IMAGEBIND_BACKEND", "imagebind").strip().lower()
    if backend == "stub":
        log.info("Using deterministic stub embedder (no ML model).")
        return DeterministicEmbedder()
    if backend == "imagebind":
        device = os.getenv("IMAGEBIND_DEVICE", "cpu").strip().lower()
        return ImageBindEmbedder(device=device)
    raise EmbedderError("IB-002", f"Unknown IMAGEBIND_BACKEND '{backend}' (use 'imagebind' or 'stub').")
