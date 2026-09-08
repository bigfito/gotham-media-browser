# UI Design — Search Landing & Results

**Product:** Gotham News & Media Browser  
**Theme:** Light pastel (compatible soft wash background)  
**Chrome:** Shared header + footer on all pages  
**Mockups:** `ui-mockups/`  
**Related:** [`frontend-information-architecture.md`](./frontend-information-architecture.md) · [`architecture-end-to-end.md`](./architecture-end-to-end.md) · [`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md) · [`ui-design-errors.md`](./ui-design-errors.md)  
**Implementation:** Landing + `/results` are live with all four modes — full-text (P7), semantic, hybrid, and multimedia vector (P8).

## Background

Solid light wash `#F7F9FC` → soft tint `#EEF5F8` (no dark ink, no heavy vignette). Pastel accents stay on controls/panels only.

## Full-text attributes (from denormalized model)

When **Full-text** is selected, the UI shows **checkboxes for text attributes** from the ES denormalized document — not free-form keyword chips.

### Articles panel / article results
| Checkbox value | Source field |
|----------------|--------------|
| `title` | article title |
| `subtitle` | subtitle |
| `summary` | summary |
| `body` | body |
| `section` | metadata section |
| `tags` | metadata tags |
| `location` | metadata location |
| `source` | metadata source |
| `seo_title` | SEO title |
| `seo_description` | SEO description |
| `seo_keywords` | SEO keywords |
| `journalist_names` | flattened bylines |
| `journalist_bios` | flattened bios |
| `journalist_search_text` | journalist catch-all projection |
| `article_search_text` | article catch-all projection |

Default checked: `title`, `subtitle`, `summary`, `body`.  
Also: optional **journalist** filter param on full-text.

### Multimedia panel / media results
| Checkbox value | Source field |
|----------------|--------------|
| `multimedia.title` | nested media title |
| `multimedia.caption` | nested caption |
| `multimedia.description` | nested description |
| `multimedia.alt_text` | nested alt text |
| `multimedia.credit` | nested credit |
| `multimedia_text` | flattened media text |
| `multimedia_search_text` | catch-all projection |

Default checked: title, caption, description, alt_text.

Backend maps checked `fields` into a `multi_match` / `bool` should over those ES fields.  
Checkbox values `section`, `tags`, `location`, and `source` are remapped to analyzable `*.text` subfields.

**Elasticsearch Query DSL for FTS / Semantic / Hybrid / Vector:** [`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md)

## Multimedia playback (results)

Multimedia result cards use **HTML5** native elements against public `storage_uri` URLs (GCS HTTPS):

| `media_type` | Element | Notes |
|--------------|---------|--------|
| `IMAGE` | `<img>` | `alt` from `multimedia.alt_text` |
| `AUDIO` | `<audio controls preload="metadata">` | native play/seek |
| `VIDEO` | `<video controls preload="metadata">` | optional poster; native play/seek |

No third-party player libraries in the prototype.

## Pagination (Elasticsearch Search API)

UI query params map 1:1 onto Elasticsearch offset pagination:

| UI param | Allowed values | Elasticsearch |
|----------|----------------|---------------|
| `page` | 1-based integer | used to compute `from` |
| `size` | **25**, **50**, or **100** only | request `size` |

```text
from = (page - 1) × size
size = size
track_total_hits = true   # accurate total for page count
```

- Changing `size` resets to `page=1`.
- Page links preserve query, mode, fields, and filters.
- Do **not** reuse `from` as a date param — use `published_from` / `published_to` for date range filters so they never collide with ES `from`.
- Deep paging beyond `from + size` soft limits is out of scope for the mockup; stick to classic `from`/`size` (not `search_after`) for this prototype.

## Shared chrome
Header and footer are **Thymeleaf fragments** (`templates/fragments/chrome.html`). Mockups still inject chrome via `chrome.js` for static preview.

### Header service legends
Two availability legends (Available / Unavailable / Checking…):

| Legend | Source (prototype) |
|--------|--------------------|
| **ImageBind** | `chrome.js` probes `/api/health/imagebind` |
| **Elasticsearch** | `chrome.js` probes `/api/health/elasticsearch` |

Landing templates may also set `data-imagebind-status` / `data-elasticsearch-status` for static mockups.

### Footer
Shows **all copyrights** from the project license lineage, **MIT License** link, and year **2026**:
- Copyright © 2020 Packt (repository `LICENSE`)
- Copyright © 2026 Gotham News & Media Browser contributors
- MIT License reference

## Mockups
| File | Role |
|------|------|
| `index.html` | Dual-panel landing |
| `results-articles.html` | Article results + ES-synced pagination |
| `results-multimedia.html` | Multimedia results (HTML5 players) + pagination |
| `journalist.html` | `GET /journalist` list |
| `journalist-new.html` | `GET /journalist/new` (form posts to `POST /journalist`) |
| `journalist-edit.html` | `GET/POST /journalist/{id}` |
| `article.html` | `GET /article` list |
| `article-new.html` | `GET /article/new` (form posts to `POST /article`) |
| `article-edit.html` | `GET/POST /article/{id}` (bylines + HTML5 media) |
| `error.html` | Global fault-tolerant error page (reason + reference id) |
| `styles.css` | Light pastel theme |
| `chrome.js` | Shared header/footer, service legends, mode toggles |
| `media/` | Sample IMAGE / AUDIO / VIDEO fixtures for HTML5 playback |
