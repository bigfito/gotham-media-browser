# Gotham News & Media Browser — ER Design

**Version:** 1.3  
**Status:** Approved **logical** artifact (design aid for denormalized ES model)  
**Scope:** Single-brand online news & articles prototype. No authentication or user management.  
**Physical persistence:** **Not implemented as RDBMS.** Prototype persists in **Elasticsearch** (`gotham-journalists`, `gotham-media-browser`) + **GCS** only.  
**Related:** [`elasticsearch-denormalized-model.md`](./elasticsearch-denormalized-model.md) · [`architecture-components.md`](./architecture-components.md) · [`architecture-end-to-end.md`](./architecture-end-to-end.md)

## Entity-relationship diagram

```mermaid
erDiagram
    JOURNALIST ||--o{ ARTICLE_AUTHORSHIP : "writes"
    ARTICLE ||--o{ ARTICLE_AUTHORSHIP : "authored by"
    ARTICLE ||--|| ARTICLE_METADATA : "has"
    ARTICLE ||--o{ MULTIMEDIA_ELEMENT : "contains"
    MULTIMEDIA_ELEMENT ||--|| MULTIMEDIA_METADATA : "has"

    JOURNALIST {
        BIGINT id PK
        VARCHAR first_name
        VARCHAR last_name
        VARCHAR email UK
        TEXT bio
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    ARTICLE {
        BIGINT id PK
        VARCHAR title
        VARCHAR subtitle
        TEXT summary
        TEXT body
        VARCHAR slug UK
        VARCHAR status
        VARCHAR language
        TIMESTAMP published_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    ARTICLE_AUTHORSHIP {
        BIGINT article_id PK_FK
        BIGINT journalist_id PK_FK
        INT byline_order
        VARCHAR contribution_role
    }

    ARTICLE_METADATA {
        BIGINT id PK
        BIGINT article_id FK_UK
        VARCHAR section
        TEXT tags
        VARCHAR location
        VARCHAR source
        VARCHAR seo_title
        VARCHAR seo_description
        VARCHAR seo_keywords
        VARCHAR canonical_url
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    MULTIMEDIA_ELEMENT {
        BIGINT id PK
        BIGINT article_id FK
        VARCHAR media_type
        VARCHAR storage_uri
        VARCHAR mime_type
        INT position
        VARCHAR caption
        VARCHAR credit
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    MULTIMEDIA_METADATA {
        BIGINT id PK
        BIGINT multimedia_element_id FK_UK
        VARCHAR title
        TEXT description
        VARCHAR alt_text
        VARCHAR original_filename
        BIGINT file_size_bytes
        VARCHAR checksum
        INT width
        INT height
        BIGINT duration_ms
        VARCHAR codec
        INT bitrate_kbps
        DECIMAL frame_rate
        INT sample_rate_hz
        INT channels
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
```

## Cardinality summary

| Relationship | Cardinality | Notes |
|--------------|-------------|--------|
| Journalist ↔ Article | M:N via `ARTICLE_AUTHORSHIP` | Each article requires ≥ 1 journalist |
| Article → ArticleMetadata | 1:1 | Mandatory metadata row per article |
| Article → MultimediaElement | 1:N | Zero or more media items |
| MultimediaElement → MultimediaMetadata | 1:1 | Mandatory metadata row per media item |

## Crow’s-foot sketch

```
JOURNALIST ——< ARTICLE_AUTHORSHIP >—— ARTICLE
                                         ||
                                         ||
                                  ARTICLE_METADATA
                                         |
                                         |
                                         ^
                                 MULTIMEDIA_ELEMENT
                                         ||
                                         ||
                               MULTIMEDIA_METADATA
```

## Keys, uniqueness, and constraints

### Primary keys
- All entities use `BIGINT` identity columns (`id`), except `ARTICLE_AUTHORSHIP`, which uses a composite PK `(article_id, journalist_id)`.

### Foreign keys
| Child | Column | Parent |
|-------|--------|--------|
| `ARTICLE_AUTHORSHIP` | `article_id` | `ARTICLE.id` |
| `ARTICLE_AUTHORSHIP` | `journalist_id` | `JOURNALIST.id` |
| `ARTICLE_METADATA` | `article_id` | `ARTICLE.id` |
| `MULTIMEDIA_ELEMENT` | `article_id` | `ARTICLE.id` |
| `MULTIMEDIA_METADATA` | `multimedia_element_id` | `MULTIMEDIA_ELEMENT.id` |

### Unique constraints
| Table | Constraint |
|-------|------------|
| `JOURNALIST` | `email` unique when not null |
| `ARTICLE` | `slug` unique |
| `ARTICLE_AUTHORSHIP` | `(article_id, byline_order)` unique |
| `ARTICLE_METADATA` | `article_id` unique |
| `MULTIMEDIA_METADATA` | `multimedia_element_id` unique |
| `MULTIMEDIA_ELEMENT` | `(article_id, position)` unique (recommended) |

### Check / domain constraints (application-enforced)
| Rule | Description |
|------|-------------|
| `ARTICLE.status` | One of `DRAFT`, `PUBLISHED`, `ARCHIVED` |
| `MULTIMEDIA_ELEMENT.media_type` | One of `IMAGE`, `AUDIO`, `VIDEO` |
| `ARTICLE_AUTHORSHIP.contribution_role` | Optional: `AUTHOR`, `CO_AUTHOR`, `CONTRIBUTING` |
| Article authorship | Every article has at least one authorship row |
| Image metadata | Prefer `width` / `height` present when `media_type = IMAGE` |
| A/V metadata | Prefer `duration_ms` present when `media_type` is `AUDIO` or `VIDEO` |

### Cascades (logical → ES write rules)
- Delete `ARTICLE` → remove nested multimedia (+ GCS objects) and the article document  
- Delete nested multimedia element → drop from parent article + delete GCS object  
- Delete `JOURNALIST` → **cascade-strip**: remove nested `journalists[]` entries with that `journalist_id` from all articles and rebuild the journalist projections — as a **partial** update of just those fields, never a full reindex — then delete the `gotham-journalists` document

## Enumerations

```text
ArticleStatus        = DRAFT | PUBLISHED | ARCHIVED
MediaType            = IMAGE | AUDIO | VIDEO
ContributionRole     = AUTHOR | CO_AUTHOR | CONTRIBUTING
```

## Notes for physical realization (prototype)

- This ER is **logical only** — used to derive the denormalized Elasticsearch model.  
- **No Cloud SQL / RDBMS** in the prototype.  
- Top-level persistence: ES indexes `gotham-journalists` + `gotham-media-browser` with **auto `_id`**.  
- Binaries: **GCS public objects**.  
- Nested multimedia elements use app-assigned string ids (no ES `_id` for nested objects).  
- `tags` may remain a list/keyword array in ES rather than a bridge table.
