# comfyui-service — In-repo CPU ComfyUI container (P10)

Provides Text-to-Image (**SDXL-Turbo**) and Text-to-Video (**Wan2.1 T2V-1.3B**) generation for synthetic media in `gotham-datagen`.

## Overview

- **Port:** `8188` (standard ComfyUI REST API)
- **Runtime:** CPU inference on Linux ARM64 / amd64 (MacBook Pro M4 lab)
- **Workflows:**
  - `workflows/sdxl_turbo_t2i.json`: 1-step SDXL-Turbo T2I (512×512 PNG/JPEG)
  - `workflows/wan21_t2v.json`: Wan2.1 1.3B T2V (5-second 16fps 480×320 MP4)
- **Dual Backend Modes:**
  - `COMFYUI_BACKEND=comfyui` (default): full ComfyUI execution.
  - `COMFYUI_BACKEND=stub`: fast deterministic stub server for smoke/integration tests without downloading multi-gigabyte models.

## Endpoints

- `GET /system_stats` — System info and CPU device availability
- `POST /prompt` — Queue a generation workflow graph (returns `prompt_id`)
- `GET /history/{prompt_id}` — Status and outputs (image/video file paths)
- `GET /view?filename=...` — Download raw output file bytes

## Docker Compose

Activated via the `datagen` profile:

```bash
docker compose --profile datagen up -d comfyui
```

Checkpoints are persisted in the `comfyui-models` named Docker volume mounted at `/app/ComfyUI/models`.
