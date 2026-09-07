# UI Design — Journalist & Article CRUD

**Product:** Gotham News & Media Browser  
**Routes:** `/journalist/**` · `/article/**` (no `/admin`)  
**Theme:** Light pastel (shared chrome)  
**Mockups:** `ui-mockups/journalist*.html`, `ui-mockups/article*.html`

## Endpoints

### `/journalist` → index `gotham-journalists`

| UI page | Backend |
|---------|---------|
| List | `GET /journalist` |
| New | `GET /journalist/new` · `POST /journalist` |
| Edit | `GET /journalist/{id}` · `POST /journalist/{id}` |
| Delete | `POST /journalist/{id}/delete` |

Fields: `first_name`, `last_name`, `email`, `bio` (+ read-only ES `_id`, timestamps).

### `/article` → index `gotham-media-browser`

| UI page | Backend |
|---------|---------|
| List | `GET /article` |
| New | `GET /article/new` · `POST /article` |
| Edit | `GET /article/{id}` · `POST /article/{id}` |
| Delete | `POST /article/{id}/delete` |

Form sections (one job each):

1. **Story** — title, subtitle, summary, body, slug, language, status, published_at  
2. **Metadata** — section, tags, location, source, SEO fields  
3. **Bylines** — multi-select journalists from `gotham-journalists`; byline order + role  
4. **Multimedia** — nested elements with HTML5 preview; upload within ImageBind local limits  

On write the backend nests journalist snapshots, uploads to public GCS, embeds via ImageBind, and refreshes denormalized text/vector fields.

## Pagination on lists
Same as search results: `page` + `size` ∈ {25, 50, 100} → ES `from` / `size`.

## Mock files
| File | Role |
|------|------|
| `journalist.html` | List |
| `journalist-new.html` | Create |
| `journalist-edit.html` | Edit |
| `article.html` | List |
| `article-new.html` | Create |
| `article-edit.html` | Edit (bylines + media) |
