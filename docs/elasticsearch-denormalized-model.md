# Gotham News & Media Browser — Elasticsearch Indexes

**Persistence:** Elastic Cloud Serverless only (+ GCS for binaries)  
**IDs:** Elasticsearch **auto `_id`** for top-level documents  
**Logical ER:** [`er-design.md`](./er-design.md) (design aid only)

## Indexes

| Index | Document | Mapping |
|-------|----------|---------|
| `gotham-journalists` | One journalist | [`gotham-journalists.mapping.json`](../elasticsearch/gotham-journalists.mapping.json) |
| `gotham-media-browser` | One article (+ nested journalists & multimedia) | [`gotham-media-browser.mapping.json`](../elasticsearch/gotham-media-browser.mapping.json) |

`gotham-journalists` is the **master source** that feeds nested `journalists[]` on article documents at write time.

## Identity

| Entity | Identifier | Type |
|--------|------------|------|
| Journalist | ES `_id` | string (auto) |
| Article | ES `_id` | string (auto) |
| Multimedia element | `multimedia.multimedia_element_id` | `keyword`, **app-assigned** (nested has no `_id`) |

Article nested field `journalists.journalist_id` **must** equal a `gotham-journalists` `_id`.

## Search capabilities

| Entity | UI search | FTS | Semantic | Hybrid | Vector |
|--------|-----------|:---:|:--------:|:------:|:------:|
| Article | Landing panel 1 | ✓ | ✓ | ✓ | ✗ |
| Multimedia | Landing panel 2 | ✓ | ✓ | ✓ | ✓ |
| Journalist | **No UI** | as **`journalist` param** on article FTS only | ✗ | ✗ | ✗ |

**Query DSL cookbook (all modes):** [`elasticsearch-search-methods.md`](./elasticsearch-search-methods.md)

### Article FTS journalist parameter
- Query param `journalist` = journalist `_id` (preferred) or name token  
- Implemented as nested filter / match on `journalists.journalist_id` or `journalists.full_name` (see cookbook §4)

### Status
Articles carry `status`: `DRAFT` | `PUBLISHED` | `ARCHIVED`.  
Public search may return all; filter via `status`.

## Embeddings (ImageBind 1024-d)

| Field | Index | Source modality |
|-------|-------|-----------------|
| `article_embedding` | `gotham-media-browser` | Article text |
| `multimedia.asset_vector` | nested on article | IMAGE / AUDIO / VIDEO bytes |

No Elastic managed inference / `semantic_text`.  
No journalist embedding (no journalist semantic UI).

## Article document shape

```text
_id  (ES auto)
├── title, subtitle, summary, body, slug, status, language, dates
├── section, tags, location, source, seo_*
├── journalist_names, journalist_bios, journalist_search_text
├── multimedia_text, multimedia_search_text, article_search_text
├── journalists[] nested { journalist_id, names, email, bio, byline_order, role }
├── multimedia[] nested { multimedia_element_id, media_type, storage_uri, …, asset_vector }
└── article_embedding
```

## Journalist document shape

```text
_id  (ES auto)
├── first_name, last_name, full_name
├── email, bio
└── created_at, updated_at
```

## Local media limits

| Type | Max size | Max duration |
|------|----------|--------------|
| IMAGE | 10 MiB | — |
| AUDIO | 20 MiB | 5 min |
| VIDEO | 50 MiB | 90 s |

## GCS
- Public bucket objects  
- `storage_uri` stored on each multimedia element; UI uses public HTTPS URL  

## Write / sync rules

| Event | Actions |
|-------|---------|
| Journalist create | Index `gotham-journalists` (auto `_id`) |
| Journalist update | Update journalist doc; reindex all articles nesting that `journalist_id` |
| Journalist delete | **Cascade-strip** nested bylines from articles, rebuild projections, reindex, then delete journalist |
| Article create | Resolve journalists by id; upload media; embed; index article (auto `_id`) |
| Article update | Same; reuse article `_id` |
| Media add/update/delete | GCS + embed + reindex parent article `_id` |
| Article delete | Delete GCS objects; delete article doc |

## Out of scope
- RDBMS  
- Signed URLs  
- Journalist search results page  
- Elastic inference endpoints  
