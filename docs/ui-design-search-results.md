# UI Design — Search Landing & Results

**Product:** Gotham News & Media Browser  
**Pages:** `/` (search landing), `/results` (results)  
**Stack target:** Thymeleaf + shared CSS (mockups in `ui-mockups/`)

## Visual direction

Editorial **night-desk** — ink surfaces, cool mist gradients, one sharp signal accent. Not a marketing splash page; the first viewport is a **search workspace** with brand as the hero signal.

| Token | Value | Role |
|-------|-------|------|
| `--ink` | `#070B14` | Page depth |
| `--panel` | `#121A2B` | Panel surfaces |
| `--panel-edge` | `#243049` | Borders |
| `--mist` | `#9BB0D0` | Secondary text |
| `--paper` | `#E8EEF8` | Primary text |
| `--signal` | `#FF4D6D` | Accent / CTA |
| `--focus` | `#5CE1FF` | Focus rings / active tabs |
| Brand font | **Fraunces** | Display / wordmark |
| UI font | **Sora** | Controls, body |

Atmosphere: radial vignette + subtle diagonal grid (CSS), no stock photos in the first viewport.

## Landing (`/`) — composition

One viewport, one job: choose a search lane and run it.

```text
┌──────────────────────────────────────────────────────────┐
│  GOTHAM NEWS & MEDIA BROWSER                    Admin    │  ← brand-dominant
│  Search the newsroom desk — articles or media assets.    │  ← one supporting line
├─────────────────────────────┬────────────────────────────┤
│  ARTICLES                   │  MULTIMEDIA                │
│  Full-text · Semantic ·     │  Full-text · Semantic ·    │
│  Hybrid                     │  Hybrid · Vector           │
│  [ query ................ ] │  [ query ................ ]│
│  (journalist filter FTS)    │  [ file drop if Vector ]   │
│  [ Search articles → ]      │  [ Search multimedia → ]   │
└─────────────────────────────┴────────────────────────────┘
```

### Rules
- Brand wordmark is the largest type on the page (not the panel titles).
- Panels are peers — equal width, equal visual weight; no card stack of promos.
- Article panel: method tabs **Full-text | Semantic | Hybrid** only.
- Multimedia panel: **Full-text | Semantic | Hybrid | Vector**; Vector reveals file dropzone.
- Article Full-text shows optional **Journalist** field (FTS parameter).
- Primary CTAs use `--signal`; active method uses `--focus` underline.

## Results (`/results`) — composition

```text
┌──────────────────────────────────────────────────────────┐
│  Brand · entity badge · sticky search (methods for entity)│
├──────────────┬───────────────────────────────────────────┤
│ Filters      │ Sort · “N results”                        │
│ status       │ ┌ result row / media tile ─────────────┐  │
│ section      │ └──────────────────────────────────────┘  │
│ journalist*  │ …                                         │
│ media type†  │ Pagination                                │
│ dates        │                                           │
└──────────────┴───────────────────────────────────────────┘
* article entity   † multimedia entity
```

### Rules
- Sticky header mirrors the **originating panel’s** methods only.
- Left filter rail (collapses under search on narrow screens).
- Article results: editorial rows (title, deck, byline, section, status chip, date).
- Multimedia results: media tiles (thumb/type glyph, caption, parent article, type chip).
- Pagination + sort always visible when there are hits.
- Empty state: short copy + link back to the other panel.

## Interaction notes
- Progressive enhancement: GET forms; Vector uses POST multipart.
- Keyboard: tabs are real radio/segmented controls; focus rings use `--focus`.
- Motion: 150–220ms tab indicator + panel hover border only (restraint).

## Mockups
| File | Page |
|------|------|
| `ui-mockups/index.html` | Landing dual-panel search |
| `ui-mockups/results-articles.html` | Article results |
| `ui-mockups/results-multimedia.html` | Multimedia results |
| `ui-mockups/styles.css` | Shared tokens + layout |

Open with any static server, e.g. `python3 -m http.server -d ui-mockups 8765`.
