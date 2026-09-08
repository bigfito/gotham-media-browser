#!/bin/sh
set -e

# =============================================================================
#  comfyui-service entrypoint
#  Starts ComfyUI in CPU mode or runs the lightweight stub server.
# =============================================================================

BACKEND="${COMFYUI_BACKEND:-comfyui}"

if [ "$BACKEND" = "stub" ] || [ ! -d "/app/ComfyUI" ]; then
    echo "[comfyui-service] Starting ComfyUI STUB backend on port 8188..."
    exec uvicorn stub_server:app --host 0.0.0.0 --port 8188
else
    echo "[comfyui-service] Starting full ComfyUI backend in CPU mode..."
    cd /app/ComfyUI
    exec python main.py ${COMFYUI_ARGS:---cpu --listen 0.0.0.0 --port 8188}
fi
