# Gotham News & Media Browser — Elasticsearch Denormalized Model

**Index name:** `gotham-media-browser`  
**Target:** Elasticsearch 9.x  
**Source of truth:** Relational ER in [`er-design.md`](./er-design.md) (approved artifact)  
**Document grain:** one search document per **Article**

## Design goals

| Capability | How this index supports it |
|------------|----------------------------|
| **Full-text (lexical / BM25)** | `text` fields (`title`, `subtitle`, `summary`, `body`, captions, journalist bios) with English analysis; `search_text` catch-all |
| **Semantic search** | `semantic_text` fields (`content_semantic`, optional `multimedia_semantic`) via Inference API (auto embeddings + chunking) |
| **Vector search** | Explicit `dense_vector` on `content_vector` for app-supplied or pipeline-supplied embeddings (kNN) |
| **Hybrid search** | Combine BM25 on `text` / `search_text` with `semantic_text` and/or `knn` on `content_vector` using RRF retrievers |

Relational tables are flattened into a single article-centric document. Journalists and multimedia are **nested** so filters and inner hits work without false cross-matches.

## Denormalized document shape

```text
Article document
├── identity & editorial fields (from ARTICLE)
├── flat article metadata (from ARTICLE_METADATA)
├── journalist_names         (text)  ← flattened bylines for root search
├── multimedia_text          (text)  ← flattened captions/titles/alt/descriptions
├── journalists[] nested (from JOURNALIST + ARTICLE_AUTHORSHIP)
├── multimedia[] nested (from MULTIMEDIA_ELEMENT + MULTIMEDIA_METADATA)
└── search projection fields
    ├── search_text          (text)           ← lexical catch-all
    ├── content_semantic     (semantic_text)  ← semantic / hybrid
    ├── multimedia_semantic  (semantic_text)  ← media-oriented semantic
    └── content_vector       (dense_vector)   ← explicit vector / kNN
```

Indexer responsibility: when building the ES document, populate `journalist_names` (ordered bylines) and `multimedia_text` (concatenated media title/caption/description/alt_text). Nested `copy_to` into root fields is avoided because Elasticsearch does not reliably promote nested values into parent search fields.
## Field type strategy

| Concern | ES type | Rationale |
|---------|---------|-----------|
| IDs, slugs, status, section, tags, media_type, roles | `keyword` / `long` / `integer` | Exact match, aggregations, filters |
| Titles, body, captions, bios | `text` (+ `keyword` multi-field where needed) | Full-text search |
| Dates | `date` | Range filters / sorting |
| Booleans / sizes / dimensions | `boolean`, `long`, `integer`, `float` | Facets and technical filters |
| Semantic passage search | `semantic_text` | ES 9.x managed embeddings, chunking, hybrid with lexical |
| Explicit vector search | `dense_vector` (`dims: 768`, `cosine`, `int8_hnsw`) | Custom/external embedding models; pure kNN |
| Repeated journalists / media | `nested` | Correct scoring/filtering of child objects |

### Notes on vectors and models
- `content_semantic` relies on an **inference endpoint** (`inference_id`). Pin a stable endpoint in production so ranking stays consistent across index versions.
- `content_vector.dims` is set to **768** (common for MiniLM / many text-embedding models). Change `dims` to match the chosen model before indexing any documents.
- `semantic_text` is **text-only**. Multimedia contributes via captions, descriptions, alt text, and titles copied into the semantic/lexical corpus—not raw binary media.
- For future multimodal embeddings (image/audio/video bytes), evaluate the ES 9.x `semantic` field (preview) in a later iteration.

## Mapping reference

Ready-to-apply definition: [`elasticsearch/gotham-media-browser.mapping.json`](../elasticsearch/gotham-media-browser.mapping.json)

### Top-level properties (summary)

| Field | Type | Search role |
|-------|------|-------------|
| `article_id` | `long` | Filter / identity |
| `title` | `text` + `keyword` | Full-text + sort/agg |
| `subtitle` | `text` | Full-text |
| `summary` | `text` | Full-text |
| `body` | `text` | Full-text |
| `slug` | `keyword` | Exact lookup |
| `status` | `keyword` | Filter |
| `language` | `keyword` | Filter |
| `published_at`, `created_at`, `updated_at` | `date` | Range / sort |
| `section`, `tags`, `location`, `source` | `keyword` / `text` | Facets + text |
| `seo_*`, `canonical_url` | `text` / `keyword` | SEO / exact |
| `journalist_names` | `text` + `keyword` | Byline lexical/semantic (root) |
| `multimedia_text` | `text` | Media text lexical/semantic (root) |
| `journalists` | `nested` | Byline filters / nested queries |
| `multimedia` | `nested` | Media filters / nested queries |
| `search_text` | `text` | Lexical catch-all (BM25) |
| `content_semantic` | `semantic_text` | Semantic + hybrid |
| `multimedia_semantic` | `semantic_text` | Media-focused semantic |
| `content_vector` | `dense_vector` | Vector / kNN |

Root lexical fields (`title`, `subtitle`, `summary`, `body`, metadata text, `journalist_names`, `multimedia_text`) use `copy_to` into `search_text` / `content_semantic` (and `multimedia_text` also into `multimedia_semantic`).

## Sample document

```json
{
  "article_id": 1001,
  "title": "Gotham Transit Expansion Clears Final Vote",
  "subtitle": "New subway line set for 2028",
  "summary": "City council approved funding for the cross-river line after a marathon session.",
  "body": "Full article body...",
  "slug": "gotham-transit-expansion-clears-final-vote",
  "status": "PUBLISHED",
  "language": "en",
  "published_at": "2026-09-06T18:00:00Z",
  "created_at": "2026-09-05T10:15:00Z",
  "updated_at": "2026-09-06T17:55:00Z",
  "section": "Politics",
  "tags": ["transit", "city-council", "infrastructure"],
  "location": "Gotham City",
  "source": "Gotham News Desk",
  "seo_title": "Gotham Transit Expansion Approved",
  "seo_description": "Council funds cross-river subway line targeting 2028 opening.",
  "seo_keywords": "gotham, transit, subway",
  "canonical_url": "https://gotham.example/news/gotham-transit-expansion-clears-final-vote",
  "journalist_names": ["Lois Lane", "Clark Kent"],
  "multimedia_text": "Council chamber. Council chamber after the vote. Members leave the chamber following approval. Council members exiting the chamber",
  "journalists": [
    {
      "journalist_id": 12,
      "first_name": "Lois",
      "last_name": "Lane",
      "full_name": "Lois Lane",
      "email": "lois.lane@gotham.example",
      "bio": "City hall correspondent.",
      "byline_order": 1,
      "contribution_role": "AUTHOR"
    },
    {
      "journalist_id": 27,
      "first_name": "Clark",
      "last_name": "Kent",
      "full_name": "Clark Kent",
      "email": "clark.kent@gotham.example",
      "bio": "Investigative reporter.",
      "byline_order": 2,
      "contribution_role": "CO_AUTHOR"
    }
  ],
  "multimedia": [
    {
      "multimedia_element_id": 501,
      "media_type": "IMAGE",
      "storage_uri": "gs://gotham-media/articles/1001/hero.jpg",
      "mime_type": "image/jpeg",
      "position": 1,
      "caption": "Council chamber after the vote",
      "credit": "Gotham News Photo",
      "title": "Council chamber",
      "description": "Members leave the chamber following approval.",
      "alt_text": "Council members exiting the chamber",
      "original_filename": "hero.jpg",
      "file_size_bytes": 245760,
      "checksum": "sha256:ab...",
      "width": 1920,
      "height": 1080,
      "duration_ms": null,
      "codec": null,
      "bitrate_kbps": null,
      "frame_rate": null,
      "sample_rate_hz": null,
      "channels": null
    }
  ],
  "content_vector": [0.01, 0.02]
}
```

`search_text` / `content_semantic` are filled via `copy_to` from root article fields plus `journalist_names` / `multimedia_text`. `content_vector` must be supplied by the indexing pipeline when using explicit vector search (length must equal `dims`, typically 768).

## Query patterns (illustrative)

### Full-text
```json
{
  "query": {
    "multi_match": {
      "query": "subway funding",
      "fields": ["title^3", "summary^2", "body", "search_text"]
    }
  }
}
```

### Semantic
```json
{
  "query": {
    "semantic": {
      "field": "content_semantic",
      "query": "public transport investment in the city"
    }
  }
}
```

### Vector (kNN)
```json
{
  "knn": {
    "field": "content_vector",
    "query_vector": [0.01, 0.02],
    "k": 10,
    "num_candidates": 50
  }
}
```

### Hybrid (RRF: lexical + semantic)
```json
{
  "retriever": {
    "rrf": {
      "retrievers": [
        {
          "standard": {
            "query": {
              "multi_match": {
                "query": "subway funding",
                "fields": ["title^3", "summary^2", "body", "search_text"]
              }
            }
          }
        },
        {
          "standard": {
            "query": {
              "semantic": {
                "field": "content_semantic",
                "query": "subway funding"
              }
            }
          }
        }
      ],
      "rank_constant": 60,
      "rank_window_size": 50
    }
  }
}
```

Extend the RRF retriever list with a `knn` retriever on `content_vector` when explicit embeddings are populated.

## Sync rules from relational model

| Relational change | Elasticsearch action |
|-------------------|----------------------|
| Article create/update | Upsert document `_id = article_id` |
| Authorship change | Re-index parent article document |
| Article metadata change | Re-index parent article document |
| Multimedia add/update/delete | Re-index parent article document |
| Journalist profile change | Re-index all articles by that journalist (or async fan-out) |
| Article delete | Delete document by `_id` |

## Out of scope for this mapping
- Index aliases / ILM policies (add when environments are defined)
- Ingest pipeline implementation details (inference endpoint provisioning)
- Security / document-level permissions (no user model in prototype)
