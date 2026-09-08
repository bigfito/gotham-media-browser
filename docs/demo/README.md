# Static demo fixtures (P9-T01)

A tiny, committed dataset for a **CI / low-resource demo** — no GPU generative models, no
`gotham-datagen`, no Ollama / ComfyUI / Kokoro. It is loaded **only through the HTTP CRUD**
(`POST /journalist`, `POST /article`), never written to Elasticsearch or GCS directly.

Full synthetic load (15 journalists · 25 articles · 5+5+5 media) is **P10** — see
[`../datagen-runbook.md`](../datagen-runbook.md).

## Contents

| Path | What |
|------|------|
| `fixtures/journalists.tsv` | 4 Gotham reporters — `first_name`, `last_name`, `email`, `bio` |
| `fixtures/articles.tsv` | 4 articles — `title`, `section`, `tags`, `status`, `language`, `byline_emails`, `media`, `summary`, `body` |
| `fixtures/media/skyline.png` | 8×8 RGB PNG (165 B) — the IMAGE sample |
| `fixtures/media/chime.wav` | 0.10 s mono 8 kHz PCM WAV (1.6 KB) — the AUDIO sample |
| `seed.sh` | curl loader that POSTs the fixtures via the CRUD routes |

Articles reference journalists by **email**; `seed.sh` resolves each email to the Elasticsearch
`_id` assigned on create. `media` is a filename under `fixtures/media/` or `-` for none.

Sections are drawn from the results filter set (**Politics · Business · Culture**), one article is a
**DRAFT** (to exercise the status filter), and two carry media (image + audio). All media is well
within the prototype limits (IMAGE ≤ 10 MB · AUDIO ≤ 20 MB · VIDEO ≤ 50 MB); a VIDEO sample is
intentionally omitted — hand-crafting a valid tiny MP4 needs `ffmpeg`, and it is left to the P10
generator.

## Load it

Prerequisites: `gotham-web` running with Elasticsearch configured (and GCS configured if you want
the media uploads to succeed); `bash` 4+ and `curl`.

```bash
BASE_URL=http://localhost:8080 ./docs/demo/seed.sh
```

The script pre-checks `/api/health/elasticsearch`, creates the 4 journalists (capturing their ids
from the newest-first `/journalist` list), then creates the 4 articles — attaching `skyline.png` and
`chime.wav` as multipart uploads. It exits non-zero with a clear message on the first failure.

> Media uploads go to the public GCS bucket and require valid credentials. To seed **text only**
> (no GCS needed), run with `TEXT_ONLY=1`:
>
> ```bash
> TEXT_ONLY=1 BASE_URL=http://localhost:8080 ./docs/demo/seed.sh
> ```

## Try the search modes

Open `http://localhost:8080/` and, on the **articles** panel, search `transit funding`:

- **Full-text** — matches the transit article by BM25.
- **Semantic** — kNN over `article_embedding` (needs `imagebind-service` up, or the stub backend).
- **Hybrid** — RRF of both.

On the **multimedia** panel:

- **Full-text / Semantic / Hybrid** — search `museum skyline` or `energy microgrid`.
- **Vector** — drop `docs/demo/fixtures/media/skyline.png` to rank assets by the uploaded image.

Filters to demo: **section** = Culture, **status** = include DRAFT to reveal the startups article.

## Manual alternative (no script)

Create the journalists at `/journalist/new`, then create articles at `/article/new`, selecting the
byline(s) and attaching the media files from `fixtures/media/`. The data in the TSVs is the copy to
paste.

## Regenerating the media

The samples are produced with the Python standard library (no external tools):

```bash
python - <<'PY'
import struct, zlib, wave, math
# 8x8 RGB PNG
w=h=8; raw=bytearray()
for y in range(h):
    raw.append(0)
    for x in range(w): raw+=bytes([(x*32)%256,(y*32)%256,128])
def ch(t,d):
    c=t+d; return struct.pack(">I",len(d))+c+struct.pack(">I",zlib.crc32(c)&0xffffffff)
png=b"\x89PNG\r\n\x1a\n"+ch(b"IHDR",struct.pack(">IIBBBBB",w,h,8,2,0,0,0))\
    +ch(b"IDAT",zlib.compress(bytes(raw),9))+ch(b"IEND",b"")
open("docs/demo/fixtures/media/skyline.png","wb").write(png)
# 0.10s mono 8kHz 16-bit WAV
sr=8000; n=int(sr*0.10); fr=bytearray()
for i in range(n): fr+=struct.pack("<h",int(3000*math.sin(2*math.pi*440*i/sr)))
wv=wave.open("docs/demo/fixtures/media/chime.wav","wb")
wv.setnchannels(1); wv.setsampwidth(2); wv.setframerate(sr); wv.writeframes(bytes(fr)); wv.close()
PY
```

Fixture integrity (columns, referential byline emails, media presence + size limits) is checked by
`DemoFixturesTest` in `gotham-web` (runs under `mvn test`).
