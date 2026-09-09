# UI Design — Journalist & Article CRUD

**Product:** Gotham News & Media Browser  
**Routes:** `/journalist/**` · `/article/**` (no `/admin`)  
**Theme:** Light pastel (shared chrome)  
**Mockups:** `ui-mockups/journalist*.html`, `ui-mockups/article*.html`  
**Related:** [`frontend-information-architecture.md`](./frontend-information-architecture.md) · [`architecture-end-to-end.md`](./architecture-end-to-end.md) · [`ui-design-errors.md`](./ui-design-errors.md)  
**Implementation:** `/journalist` and `/article` CRUD, GCS media, ImageBind embeddings, and per-asset title/caption/alt/credit on the article form are live.

## Endpoints

### `/journalist` → index `gotham-journalists`

| UI page | Backend |
|---------|---------|
| List | `GET /journalist` |
| New | `GET /journalist/new` · `POST /journalist` |
| Edit | `GET /journalist/{id}` · `POST /journalist/{id}` |
| Delete | `POST /journalist/{id}/delete` (**cascade-strip** bylines on articles, then delete) |

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
2. **Metadata** — section, tags, location, source, SEO fields, `canonical_url`  
3. **Bylines** — multi-select journalists from `gotham-journalists`; byline order + `contribution_role` (`AUTHOR` | `CO_AUTHOR` | `CONTRIBUTING`)  
4. **Multimedia** — nested elements with HTML5 preview; upload within ImageBind local limits  

On write the backend nests journalist snapshots, uploads to public GCS, embeds via ImageBind, and refreshes denormalized text/vector fields.

Delete: `POST /journalist/{id}/delete` · `POST /article/{id}/delete`.

## Pagination on lists
Same as search results: `page` + `size` ∈ {25, 50, 100} → ES `from` / `size`.

## Mock files
| File | Backend |
|------|---------|
| `journalist.html` | `GET /journalist` |
| `journalist-new.html` | `GET /journalist/new` · form `POST /journalist` |
| `journalist-edit.html` | `GET /journalist/{id}` · `POST /journalist/{id}` · delete `POST /journalist/{id}/delete` |
| `article.html` | `GET /article` |
| `article-new.html` | `GET /article/new` · form `POST /article` |
| `article-edit.html` | `GET /article/{id}` · `POST /article/{id}` · delete `POST /article/{id}/delete` |
