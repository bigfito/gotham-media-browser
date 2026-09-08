# syntax=docker/dockerfile:1
# =============================================================================
#  comfyui-service (P10-T02) — in-repo CPU ComfyUI container for SDXL-Turbo & Wan2.1.
#
#  Exposes the standard ComfyUI REST API at port 8188 for:
#    - Text-to-Image (SDXL-Turbo)
#    - Text-to-Video (Wan2.1 T2V-1.3B, 5-second clips)
#
#  CPU inference by default on Apple Silicon / macOS lab (no NVIDIA GPU).
#
#  Build args:
#    INSTALL_COMFYUI=true   (default) clones ComfyUI and installs full torch CPU
#                           stack; `COMFYUI_BACKEND=comfyui` starts real ComfyUI.
#    INSTALL_COMFYUI=false  skips heavy clone for a lightweight stub container
#                           used in fast testing / CI (`COMFYUI_BACKEND=stub`).
# =============================================================================
FROM python:3.11-slim

ARG INSTALL_COMFYUI=true
ENV PYTHONUNBUFFERED=1 \
    COMFYUI_BACKEND=comfyui \
    COMFYUI_ARGS="--cpu --listen 0.0.0.0 --port 8188"

WORKDIR /app

# System tools and libraries needed for video encoding and image processing
RUN apt-get update && apt-get install -y --no-install-recommends \
        git curl ffmpeg libsndfile1 build-essential && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Clone ComfyUI and install its CPU dependencies if requested
RUN if [ "$INSTALL_COMFYUI" = "true" ]; then \
        git clone https://github.com/comfyanonymous/ComfyUI.git /app/ComfyUI && \
        cd /app/ComfyUI && \
        pip install --no-cache-dir torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu && \
        pip install --no-cache-dir -r requirements.txt; \
    fi

COPY entrypoint.sh stub_server.py ./
COPY workflows/ ./workflows/

RUN chmod +x entrypoint.sh

EXPOSE 8188

ENTRYPOINT ["/app/entrypoint.sh"]
