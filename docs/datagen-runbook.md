# Datagen Operator Runbook — Synthetic Data Generation (P10)

This runbook guides operators on generating and loading the full synthetic dataset (**15 journalists · 25 articles · 375 multimedia assets**) into the Gotham News & Media Browser prototype.

---

## 1. Hardware & Runtime Baseline

| Item | Requirement |
|------|-------------|
| **Host Machine** | Apple **MacBook Pro M4** (or Apple Silicon Mac) |
| **Host RAM** | **32 GB** unified memory |
| **GPU** | **None required** (CPU inference inside Docker Linux containers) |
| **Docker Desktop** | Settings → Resources: **Memory ≥ 16 GB** (recommended 20–24 GB), **CPUs ≥ 6** |
| **JDK** | **JDK 25 LTS** |
| **Build Tool** | Apache Maven 3.9.x |

> [!NOTE]
> Docker Desktop on Apple Silicon runs Linux ARM64 VMs. In-container inference runs via CPU multithreading (`torch` CPU / ONNX / Ollama CPU). Lighter models (**Qwen 2.5 7B**, **SDXL-Turbo**, **Kokoro-82M**, **Wan2.1 1.3B**) were specifically locked to ensure stability on a 32 GB Mac without requiring NVIDIA GPUs.

---

## 2. Docker Compose Stack Setup

### 2.1 Start the services

Start the complete prototype stack, including the `datagen` profile helper containers:

```bash
# Start all services (gotham-web, imagebind-service, ollama, comfyui, kokoro)
docker compose --profile datagen up -d
```

### 2.2 Verify Container Status

```bash
docker compose --profile datagen ps
```

Expected healthy ports:
* **`gotham-web`**: `http://localhost:8080`
* **`imagebind-service`**: `http://localhost:8081` (`/health` → `200 UP`)
* **`ollama`**: `http://localhost:11434` (`/api/tags` → `200`)
* **`comfyui`**: `http://localhost:8188` (`/system_stats` → `200`)
* **`kokoro`**: `http://localhost:8880` (`/docs` → `200`)

### 2.3 Pull Ollama Text Model (First Run Only)

Download the locked Qwen 2.5 7B Instruct model into the named volume `ollama-models`:

```bash
docker exec -it gotham-ollama ollama pull qwen2.5:7b-instruct
```

---

## 3. Running `gotham-datagen`

`gotham-datagen` is a standalone, plain Java console application (NOT Spring Boot). It communicates **only through HTTP CRUD** (`POST /journalist`, `POST /article` multipart) to exercise the live validation, GCS uploads, ImageBind embeddings, and Elasticsearch indexing.

### 3.1 Build the runnable jar

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
mvn -pl gotham-datagen -am clean package
```

This creates the self-contained executable jar `gotham-datagen/target/gotham-datagen.jar`.

### 3.2 Inspect Options & Defaults

```bash
java -jar gotham-datagen/target/gotham-datagen.jar --help
```

### 3.3 Dry-Run (Check configuration without network calls)

```bash
java -jar gotham-datagen/target/gotham-datagen.jar --dry-run
```

---

## 4. Execution Profiles

### Profile A: Full Generative Run (Locked Prototype Target)

Generates **15 journalists**, **25 articles**, **125 images** (SDXL-Turbo), **125 audios** (Kokoro), and **125 videos** (Wan2.1 5s) — total **375 media assets**.

```bash
java -jar gotham-datagen/target/gotham-datagen.jar
```

> [!IMPORTANT]
> **Overnight Execution Note:** Generating 125 video clips of 5 seconds each using Wan2.1 on CPU is computationally heavy (~3–6 minutes per clip). A full run is intended for overnight or background lab execution.

---

### Profile B: Fast Daytime Run (Text + Image + Audio, Skip Video)

Skips the heavy Wan2.1 video generation while populating rich imagery and voice dispatches:

```bash
java -jar gotham-datagen/target/gotham-datagen.jar --skip-video
```

---

### Profile C: Lightweight Text-Only Run (Zero Media, No GCS/ComfyUI/Kokoro required)

Generates full textual journalism and bylines:

```bash
java -jar gotham-datagen/target/gotham-datagen.jar --skip-image --skip-audio --skip-video
```

---

### Profile D: Custom Scaled Volumes (Debugging / Testing)

```bash
java -jar gotham-datagen/target/gotham-datagen.jar \
  --journalists=3 \
  --articles=5 \
  --images-per-article=2 \
  --audio-per-article=1 \
  --skip-video
```

---

## 5. Verification in the Browser

After running `gotham-datagen`:

1. **Journalists:** Navigate to `http://localhost:8080/journalist`. Verify 15 journalists appear with formatted dates, bios, and cascade-strip delete actions.
2. **Articles:** Navigate to `http://localhost:8080/article`. Verify 25 articles are listed across sections with byline chips, status badges (`PUBLISHED`, `DRAFT`, `ARCHIVED`), and media attachments.
3. **Multimedia Players:** Open any article edit page (e.g. `/article/{id}`) to verify HTML5 `<img>`, `<audio controls>`, and `<video controls>` render from Google Cloud Storage.
4. **Search Capabilities:** Open `http://localhost:8080/` and test all four modes:
   * **Full-text (BM25):** Search `"transit funding"` in Panel 1 (Articles) and Panel 2 (Multimedia).
   * **Semantic (kNN):** Search `"public transport municipal budget"` in Semantic mode.
   * **Hybrid (RRF):** Search `"city council policy"` in Hybrid mode.
   * **Vector:** Drop an image into Panel 2 Vector mode to perform cross-modal search.

---

## 6. Troubleshooting & FAQ

| Symptom | Cause | Resolution |
|---------|-------|------------|
| `Docker container exit code 137` | Out Of Memory (OOM) inside Docker | Increase Docker Desktop memory to ≥ 16 GB in Settings → Resources. |
| `Ollama HTTP 404 / Model not found` | Qwen 2.5 model has not been pulled | Run `docker exec -it gotham-ollama ollama pull qwen2.5:7b-instruct`. |
| `ComfyUI connection refused` | Container starting or not launched | Check `docker compose --profile datagen ps` or use `--skip-image --skip-video`. |
| `gotham-web HTTP 413` | Uploaded media exceeds size limits | Datagen automatically enforces product limits (Image ≤ 10 MB, Audio ≤ 20 MB, Video ≤ 50 MB / 5s). |
