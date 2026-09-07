# syntax=docker/dockerfile:1
# =============================================================================
#  imagebind-service (P6-T01) — in-repo Meta ImageBind wrapper.
#
#  Embeds text / image / audio / video into float[1024] over a small HTTP API.
#  CPU inference by default (M4 lab, no GPU).
#
#  Build args:
#    INSTALL_IMAGEBIND=true  (default) installs the real PyTorch + ImageBind
#                            stack; `IMAGEBIND_BACKEND=imagebind` then serves
#                            real vectors (downloads ~4.5 GB weights on first
#                            embed — mount a volume on /root/.cache to persist).
#    INSTALL_IMAGEBIND=false skips the heavy stack for a fast image that only
#                            serves the deterministic `stub` backend (CI / smoke).
#  Python 3.10 matches Meta ImageBind's supported runtime.
# =============================================================================
FROM python:3.10-slim

ARG INSTALL_IMAGEBIND=true
ENV PYTHONUNBUFFERED=1 \
    IMAGEBIND_BACKEND=imagebind \
    IMAGEBIND_DEVICE=cpu

WORKDIR /app

# System libraries needed by torchaudio/soundfile (audio) and OpenCV-style I/O.
RUN if [ "$INSTALL_IMAGEBIND" = "true" ]; then \
        apt-get update && apt-get install -y --no-install-recommends \
            git build-essential libsndfile1 ffmpeg && \
        rm -rf /var/lib/apt/lists/*; \
    fi

COPY requirements.txt requirements-imagebind.txt ./
RUN pip install --no-cache-dir -r requirements.txt && \
    if [ "$INSTALL_IMAGEBIND" = "true" ]; then \
        pip install --no-cache-dir -r requirements-imagebind.txt && \
        # imagebind pins its own deps (incl. the pytorchvideo commit above); install
        # it with --no-deps so it reuses the pinned stack instead of re-resolving.
        pip install --no-cache-dir --no-deps "git+https://github.com/facebookresearch/ImageBind.git"; \
    fi

COPY app.py embedders.py ./

EXPOSE 8081

# One worker: the model is large and holds CPU; scale by running more containers.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8081", "--workers", "1"]
