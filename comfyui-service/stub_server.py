"""
Deterministic stub server for ComfyUI REST API.
Emulates ComfyUI endpoints (/system_stats, /prompt, /history/{prompt_id}, /view)
so integration tests and development can run without downloading multi-gigabyte models.
"""

import io
import os
import subprocess
import tempfile
import uuid
from typing import Any, Dict
from fastapi import FastAPI, HTTPException, Query, Response
from fastapi.responses import FileResponse
from pydantic import BaseModel
from PIL import Image, ImageDraw

app = FastAPI(title="ComfyUI Stub Service", version="1.0.0")

OUTPUT_DIR = os.getenv("COMFYUI_OUTPUT_DIR", "/app/ComfyUI/output" if os.path.exists("/app/ComfyUI") else tempfile.gettempdir())
os.makedirs(OUTPUT_DIR, exist_ok=True)

PROMPT_HISTORY: Dict[str, Dict[str, Any]] = {}


class PromptRequest(BaseModel):
    prompt: Dict[str, Any] = {}
    client_id: str | None = None
    extra_data: Dict[str, Any] | None = None


def generate_stub_image(prompt_id: str, prompt_text: str = "") -> str:
    filename = f"comfy_img_{prompt_id[:8]}.png"
    filepath = os.path.join(OUTPUT_DIR, filename)
    img = Image.new("RGB", (512, 512), color=(40, 50, 70))
    draw = ImageDraw.Draw(img)
    draw.rectangle([(20, 20), (492, 492)], outline=(120, 160, 220), width=3)
    draw.text((40, 40), f"ComfyUI Stub Image\nID: {prompt_id[:8]}\n{prompt_text[:60]}", fill=(240, 240, 240))
    img.save(filepath, format="PNG")
    return filename


def generate_stub_video(prompt_id: str, duration_sec: int = 5) -> str:
    filename = f"comfy_vid_{prompt_id[:8]}.mp4"
    filepath = os.path.join(OUTPUT_DIR, filename)
    
    # Try ffmpeg if available to generate a valid 5-second test MP4
    try:
        cmd = [
            "ffmpeg", "-y", "-f", "lavfi",
            "-i", f"color=c=navy:s=320x240:d={duration_sec}:r=24",
            "-f", "lavfi", "-i", f"sine=frequency=440:duration={duration_sec}",
            "-c:v", "libx264", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-shortest",
            filepath
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    except Exception:
        # Fallback: create a dummy binary file if ffmpeg is not available
        with open(filepath, "wb") as f:
            f.write(b"\x00\x00\x00\x18ftypmp42\x00\x00\x00\x00mp42isom")
    return filename


@app.api_route("/system_stats", methods=["GET", "HEAD"])
def system_stats():
    return {
        "system": {
            "os": os.name,
            "python_version": "3.11",
            "embedded_python": False,
            "comfyui_version": "stub-1.0.0"
        },
        "devices": [
            {
                "name": "cpu",
                "type": "cpu",
                "index": 0,
                "vram_total": 32 * 1024 * 1024 * 1024,
                "vram_free": 24 * 1024 * 1024 * 1024,
                "torch_vram_total": 0,
                "torch_vram_free": 0
            }
        ]
    }


@app.api_route("/health", methods=["GET", "HEAD"])
def health():
    return {"status": "ok", "backend": "stub"}


@app.post("/prompt")
def queue_prompt(req: PromptRequest):
    prompt_id = str(uuid.uuid4())
    is_video = False
    prompt_text = "Synthetic Media"

    # Inspect prompt graph for video clues (e.g. Wan, Video, AnimateDiff)
    for node_id, node_data in req.prompt.items():
        if isinstance(node_data, dict):
            class_type = str(node_data.get("class_type", "")).lower()
            inputs = node_data.get("inputs", {})
            if "video" in class_type or "wan" in class_type or "animate" in class_type:
                is_video = True
            if "text" in inputs and isinstance(inputs["text"], str):
                prompt_text = inputs["text"]
            elif "prompt" in inputs and isinstance(inputs["prompt"], str):
                prompt_text = inputs["prompt"]

    if is_video:
        fname = generate_stub_video(prompt_id, duration_sec=5)
        output_entry = {"videos": [{"filename": fname, "subfolder": "", "type": "output"}]}
    else:
        fname = generate_stub_image(prompt_id, prompt_text=prompt_text)
        output_entry = {"images": [{"filename": fname, "subfolder": "", "type": "output"}]}

    PROMPT_HISTORY[prompt_id] = {
        "prompt": [0, prompt_id, req.prompt, req.extra_data, []],
        "outputs": {
            "9": output_entry
        },
        "status": {
            "status_str": "success",
            "completed": True,
            "messages": []
        }
    }

    return {
        "prompt_id": prompt_id,
        "number": len(PROMPT_HISTORY),
        "node_errors": {}
    }


@app.api_route("/history/{prompt_id}", methods=["GET", "HEAD"])
def get_history(prompt_id: str):
    if prompt_id in PROMPT_HISTORY:
        return {prompt_id: PROMPT_HISTORY[prompt_id]}
    return {}


@app.api_route("/history", methods=["GET", "HEAD"])
def get_all_history():
    return PROMPT_HISTORY


@app.api_route("/view", methods=["GET", "HEAD"])
def view_file(
    filename: str = Query(...),
    subfolder: str = Query(""),
    type: str = Query("output")
):
    path = os.path.join(OUTPUT_DIR, filename)
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="File not found")
    
    media_type = "image/png"
    if filename.endswith(".mp4"):
        media_type = "video/mp4"
    elif filename.endswith(".jpg") or filename.endswith(".jpeg"):
        media_type = "image/jpeg"
    elif filename.endswith(".webp"):
        media_type = "image/webp"

    return FileResponse(path, media_type=media_type, filename=filename)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8188)
