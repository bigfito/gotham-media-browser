# Synthetic Data Generation

**Module:** `gotham-datagen` (Maven, last implementation phase **P10**)  
**Purpose:** Generate realistic journalists + denormalized articles (with IMAGE / AUDIO / VIDEO) and load them **through** the live CRUD APIs (`POST /journalist`, `POST /article`).  
**Related:** [`implementation-plan.md`](./implementation-plan.md) · [`architecture-end-to-end.md`](./architecture-end-to-end.md) · [`ui-design-crud.md`](./ui-design-crud.md)

---

## 1. Role in the prototype

| Concern | Decision |
|---------|----------|
| When | **Last phase (P10)** — after CRUD, media, embeddings, and search work |
| How data enters the system | **Only via HTTP** to `/journalist` and `/article` (exercises validation, GCS, ImageBind, projections) |
| Not allowed | Writing straight to Elasticsearch / GCS while bypassing the app (except debugging) |
| Runtime shape | **Independent Java application** in the multi-module Maven project (own main class / fat jar — **not** embedded inside `gotham-web`) |
| Target lab hardware | **MacBook Pro M4 · 32 GB unified memory · no NVIDIA GPU** |
| CI / smoke | Keep **P9** minimal static fixtures; **P10** is full synthetic load on the Mac |

---

## 2. Independent Java application (`gotham-datagen`)

```text
gotham-datagen/          # standalone Spring Boot app (web disabled / none)
  pom.xml                # depends on gotham-common; HTTP client to gotham-web + helpers
  src/.../DatagenApplication.java   # @SpringBootApplication — own process
```

| Rule | Detail |
|------|--------|
| Packaging | Maven module with `spring-boot-maven-plugin` executable jar |
| Process | Runs **separately** from `gotham-web` (different JVM; typically `mvn -pl gotham-datagen spring-boot:run` or `java -jar …`) |
| Not | A library called in-process by `gotham-web`, and not a Python-only repo |
| Package | `com.gotham.newsmediabrowser.datagen` |
| Parent POM | Lists `gotham-datagen` alongside `gotham-common` and `gotham-web` |

```bash
mvn -pl gotham-datagen spring-boot:run
# or:
java -jar gotham-datagen/target/gotham-datagen-*.jar
```

---

## 3. Target hardware (locked)

| Item | Value |
|------|--------|
| Machine | Apple **MacBook Pro M4** |
| Memory | **32 GB** unified memory |
| Discrete NVIDIA GPU | **None** |
| Acceleration | Apple **Metal / MPS** (and CPU) — **not** CUDA |

**Implication:** Do **not** rely on NVIDIA CUDA Docker images (`yanwk/comfyui-boot` CUDA builds, GPU Compose device reservations). Run modality helpers **natively on macOS** so Metal can be used. Linux CUDA containers under Docker Desktop on Mac would fall back to CPU and are not the prototype path.

---

## 4. Helper services — lighter models for M4 / 32 GB

Chosen as lighter family variants that fit **alongside** `gotham-web` + ImageBind on 32 GB unified memory:

| Modality | Model (prototype default) | Size / quant | Unified mem (approx.) | How to run on Mac | Typical API use |
|----------|---------------------------|--------------|------------------------|-------------------|-----------------|
| **Text** | **Qwen 2.5 7B-Instruct** | 7B (Q4_K_M) | ~5–6 GB | **Native Ollama** (Metal) — `ollama pull qwen2.5:7b-instruct` | Names, bios, titles, summaries, bodies, metadata, captions |
| **Image** | **SDXL-Turbo** | ~3.5B UNet / few-step | ~6–8 GB peak | **Native ComfyUI** (PyTorch MPS) | T2I → JPEG/PNG for `IMAGE` |
| **Audio / voice** | **Kokoro-82M** | 82M (FP16 / ONNX) | ~0.5 GB (CPU OK) | Native process or CPU container | TTS → WAV/MP3 for `AUDIO` |
| **Video** | **Wan2.1 (T2V-1.3B)** | 1.3B (FP16 / lighter checkpoint) | ~8–12 GB with offload | **Native ComfyUI** (MPS + CPU offload) | T2V → **5-second** MP4 clips |

### Why these (vs earlier CUDA-oriented picks)

| Previous (NVIDIA-oriented) | Prototype default on M4 32 GB | Rationale |
|----------------------------|------------------------------|-----------|
| Qwen 2.5 **14B** | Qwen 2.5 **7B** Instruct Q4 | Leaves headroom for ComfyUI + ImageBind + browser/IDE on 32 GB |
| FLUX.1 [schnell] ~12B | **SDXL-Turbo** | Much lighter; few-step generation is practical on Metal |
| Kokoro-82M | Kokoro-82M (unchanged) | Already small; fine on CPU |
| Wan2.1 T2V-1.3B | Wan2.1 T2V-1.3B (unchanged; lightest Wan) | Keep 5 s clips; expect **slow** wall-clock on MPS/CPU |

**Optional faster text fallback:** `qwen2.5:3b-instruct` if the machine is under memory pressure during video runs.

### Native helper layout (not CUDA Compose)

```text
macOS host (recommended):
  Ollama                  :11434   qwen2.5:7b-instruct
  ComfyUI (MPS)           :8188   SDXL-Turbo + Wan2.1 workflows
  Kokoro FastAPI (CPU)    :8880   Kokoro-82M

Docker Compose (always):
  gotham-web              :8080
  imagebind-service       :8081   (CPU; acceptable on Mac)

Java (host / IDE):
  gotham-datagen          → HTTP to :8080 + helpers above
```

Agents document install/pull steps in the P10 runbook. Do **not** make CUDA `yanwk/comfyui-boot` the required path.

`gotham-datagen` properties (placeholders):

```properties
gotham.datagen.web-base-url=http://localhost:8080
gotham.datagen.ollama-base-url=http://localhost:11434
gotham.datagen.ollama-model=qwen2.5:7b-instruct
gotham.datagen.comfyui-base-url=http://localhost:8188
gotham.datagen.kokoro-base-url=http://localhost:8880
gotham.datagen.journalists=15
gotham.datagen.articles=25
gotham.datagen.images-per-article=5
gotham.datagen.audios-per-article=5
gotham.datagen.videos-per-article=5
gotham.datagen.video-duration-seconds=5
```

---

## 5. Locked default volumes (per full run)

| Entity / asset | Count |
|----------------|------:|
| Journalists | **15** |
| Articles | **25** |
| IMAGE per article | **5** |
| AUDIO (voice) per article | **5** |
| VIDEO per article | **5** (each **5 seconds**) |
| Media assets total | **375** (= 25 × 15) |

**Operator note (M4):** A full run is long — especially **125** Wan video clips on Metal/CPU, plus ImageBind embedding of all 375 assets. Prefer overnight / multi-session runs; skip flags remain for debugging (`--skip-image`, `--skip-audio`, `--skip-video`). Volumes stay locked; runtime expectations are hardware-honest.

---

## 6. Generation pipeline

```mermaid
flowchart TD
  APP[gotham-datagen<br/>independent Java app] --> TXT[Ollama Metal · Qwen 2.5 7B]
  APP --> IMG[ComfyUI MPS · SDXL-Turbo]
  APP --> AUD[Kokoro · TTS]
  APP --> VID[ComfyUI MPS · Wan2.1 1.3B]
  TXT -->|JSON fields| APP
  IMG -->|image bytes ×5| APP
  AUD -->|audio bytes ×5| APP
  VID -->|5s video ×5| APP
  APP -->|POST /journalist ×15| WEB[gotham-web]
  APP -->|POST /article multipart ×25| WEB
  WEB --> ES[(Elasticsearch)]
  WEB --> GCS[(GCS)]
  WEB --> IB[ImageBind]
```

### Steps (per run)

1. **Health-check** helpers + `gotham-web`.  
2. **Journalists (15):** Qwen 7B → `POST /journalist` → collect `_id`s.  
3. **Articles (25):** story + metadata + bylines + status mix.  
4. **Multimedia (per article):** 5× SDXL-Turbo images · 5× Kokoro audio · 5× Wan **5 s** videos (captions from Qwen).  
5. **POST `/article`** multipart matching CRUD contract.  
6. `gotham-web` handles GCS, ImageBind, projections, ES.  
7. Summary report (ids, failures, reference ids).

---

## 7. Content constraints

- Language default `en`; Gotham / civic-news tone.  
- Every article ≥ 1 journalist.  
- Status mix: DRAFT, PUBLISHED, and at least one ARCHIVED.  
- Do not invent ES `_id`s client-side.  
- `multimedia_element_id` remains **app-assigned** inside `gotham-web`.  
- Synthetic video duration: **5 seconds**.

---

## 8. Failure & skip policy

| Condition | Behavior |
|-----------|----------|
| Ollama / Qwen unavailable | Abort run (text is required) |
| Kokoro unavailable | Continue without AUDIO if `--skip-audio`; else fail |
| ComfyUI / SDXL-Turbo unavailable | Continue without IMAGE if `--skip-image`; else fail |
| ComfyUI / Wan unavailable | Continue without VIDEO if `--skip-video`; else fail |
| Unified-memory pressure / OOM | Prefer fail before POST; suggest pausing video or using `qwen2.5:3b-instruct` fallback |
| `gotham-web` 4xx/5xx | Log reason + reference id; continue or `--fail-fast` |

---

## 9. Out of scope

- Training or fine-tuning models  
- Replacing ImageBind  
- Requiring NVIDIA CUDA for the prototype lab  
- Public UI for datagen  
- Committing generated binaries to git  

---

## 10. Locked decisions (human)

| # | Decision | Status |
|---|----------|--------|
| D1 | Lab hardware: **MacBook Pro M4 · 32 GB · no NVIDIA GPU** → native Metal helpers + **lighter** models (Qwen 7B, SDXL-Turbo, Kokoro, Wan 1.3B) | **Locked** |
| D2 | Volumes: **15** journalists · **25** articles · **5** IMAGE + **5** AUDIO + **5** VIDEO (5 s) per article | **Locked** |
| D3 | `gotham-datagen` is an **independent Java application** in the multi-module Maven project | **Locked** |
| D4 | P9 static seed remains for CI without generative helpers | **Locked** |

---

## 11. Validation checklist (design)

| Check | Result |
|-------|--------|
| Module is **last** phase (**P10**) | ✓ |
| Independent Java app in multi-module project | ✓ |
| Hardware = M4 32 GB, no CUDA requirement | ✓ |
| Lighter models: Qwen 7B · SDXL-Turbo · Kokoro · Wan 1.3B | ✓ |
| Native macOS helpers (Metal/MPS), not CUDA Compose | ✓ |
| Volumes 15 / 25 / 5+5+5 (5 s video) | ✓ |
| Data enters only via `/journalist` and `/article` | ✓ |
| P9 static fixtures kept | ✓ |
| Plan/state includes P10 (41 tasks) | ✓ |
