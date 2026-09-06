# Gotham News & Media Browser — Elasticsearch Denormalized Model

**Index name:** `gotham-media-browser`  
**Target:** Elasticsearch 9.x  
**Source of truth:** Relational ER in [`er-design.md`](./er-design.md) (approved artifact)  
**Document grain:** one search document per **Article**  
**Diagram:** [`elasticsearch-denormalized-diagram.md`](./elasticsearch-denormalized-diagram.md) · [`../diagrams/gotham-media-browser-denormalized.svg`](../diagrams/gotham-media-browser-denormalized.svg)

## Entity search capabilities (authoritative)

| Entity | Full-text (BM25) | Semantic | Hybrid (RRF) | Vector (kNN) |
|--------|:----------------:|:--------:|:------------:|:------------:|
| **Article** | Yes | Yes | Yes | **No** |
| **Journalist** | Yes | Yes | Yes | **No** |
| **Multimedia asset** | Yes | Yes | Yes | **Yes** |

Hybrid for article/journalist = lexical + semantic only.  
Hybrid for multimedia = lexical + semantic + vector (kNN on per-asset embeddings).

## Design goals

| Capability | Article | Journalist | Multimedia |
|------------|---------|------------|------------|
| **Full-text** | `article_search_text` + editorial `text` fields | `journalist_search_text` (+ nested name/bio for filters) | `multimedia_search_text` + nested caption/title/description/alt_text |
| **Semantic** | `article_semantic` (`semantic_text`) | `journalist_semantic` (`semantic_text`) | `multimedia_semantic` (`semantic_text`) |
| **Hybrid** | RRF(`article_search_text`, `article_semantic`) | RRF(`journalist_search_text`, `journalist_semantic`) | RRF(`multimedia_search_text`, `multimedia_semantic`, knn `multimedia.asset_vector`) |
| **Vector** | — | — | Nested `multimedia.asset_vector` (`dense_vector`, dims 768) |

Relational tables are flattened into a single article-centric document. Journalists and multimedia remain **nested** for filter-safe child queries and inner hits. Search projections are **entity-scoped** so article/journalist ranking is not mixed with multimedia vector space.

## Denormalized document shape

```text
Article document
├── identity & editorial fields (from ARTICLE)
├── flat article metadata (from ARTICLE_METADATA)
├── journalist_names / journalist_bios   ← indexer-flattened byline text
├── multimedia_text                      ← indexer-flattened media text
├── journalists[] nested (from JOURNALIST + ARTICLE_AUTHORSHIP)
├── multimedia[] nested (from MULTIMEDIA_ELEMENT + MULTIMEDIA_METADATA)
│   └── asset_vector (dense_vector)      ← vector search ONLY here
└── entity search projections
    ├── article_search_text / article_semantic           ← Article: FTS + semantic + hybrid
    ├── journalist_search_text / journalist_semantic     ← Journalist: FTS + semantic + hybrid
    └── multimedia_search_text / multimedia_semantic     ← Multimedia: FTS + semantic (+ hybrid w/ vector)
```

### Indexer responsibilities
- Populate `journalist_names` (ordered bylines) and `journalist_bios` (concatenated bios).
- Populate `multimedia_text` (concatenated media title/caption/description/alt_text).
- Populate each `multimedia[].asset_vector` from the chosen embedding pipeline (length = `dims`, default 768).
- Do **not** supply article- or journalist-level dense vectors — those entities have no vector field.

Nested `copy_to` into root fields is avoided; root helpers are populated by the indexer.

## Field type strategy

| Concern | ES type | Used by |
|---------|---------|---------|
| IDs, slugs, status, section, tags, media_type, roles | `keyword` / `long` / `integer` | Filters / aggs |
| Article / journalist / media prose | `text` (+ `keyword` multi-fields) | Full-text |
| Dates | `date` | Range / sort |
| Technical media attributes | `integer` / `long` / `float` | Facets |
| Article / journalist / media semantic | `semantic_text` | Semantic + hybrid |
| Per-asset explicit embeddings | `dense_vector` on `multimedia.asset_vector` only | Vector + multimedia hybrid |
| Repeated journalists / media | `nested` | Correct child scoring/filtering |

### Notes on vectors and models
- `article_semantic`, `journalist_semantic`, and `multimedia_semantic` use inference endpoint `gotham-media-browser-inference`. Pin a stable endpoint in production.
- Managed embeddings inside `semantic_text` support **semantic** and **hybrid** search. They are **not** a substitute for the explicit multimedia vector field required for asset kNN.
- `multimedia.asset_vector.dims` defaults to **768**. Change before indexing if the embedding model differs.
- `semantic_text` is text-only. Multimedia binary bytes are not indexed; use captions/descriptions/alt text for semantic/FTS and `asset_vector` for vector search over asset embeddings (text and/or multimodal pipeline).
- **No** root `content_vector` / article dense vector exists by design.

## Mapping reference

Ready-to-apply definition: [`elasticsearch/gotham-media-browser.mapping.json`](../elasticsearch/gotham-media-browser.mapping.json)

### Top-level properties (summary)

| Field | Type | Entity search role |
|-------|------|--------------------|
| `article_id` | `long` | Identity |
| `title`, `subtitle`, `summary`, `body` | `text` | Article FTS → `article_search_text` / `article_semantic` |
| `slug`, `status`, `language` | `keyword` | Filters |
| `published_at`, `created_at`, `updated_at` | `date` | Range / sort |
| `section`, `tags`, `location`, `source` | `keyword` / `text` | Article facets + FTS/semantic |
| `seo_*`, `canonical_url` | `text` / `keyword` | Article SEO |
| `journalist_names`, `journalist_bios` | `text` | Journalist FTS → `journalist_search_text` / `journalist_semantic` |
| `multimedia_text` | `text` | Multimedia FTS → `multimedia_search_text` / `multimedia_semantic` |
| `journalists` | `nested` | Journalist structure / filters (no vector) |
| `multimedia` | `nested` | Multimedia structure / filters + `asset_vector` |
| `article_search_text` | `text` | Article full-text |
| `article_semantic` | `semantic_text` | Article semantic / hybrid |
| `journalist_search_text` | `text` | Journalist full-text |
| `journalist_semantic` | `semantic_text` | Journalist semantic / hybrid |
| `multimedia_search_text` | `text` | Multimedia full-text |
| `multimedia_semantic` | `semantic_text` | Multimedia semantic / hybrid |
| `multimedia.asset_vector` | `dense_vector` | **Multimedia vector only** |

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
  "journalist_bios": "City hall correspondent. Investigative reporter.",
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
      "channels": null,
      "asset_vector": [0.01, 0.02]
    }
  ]
}
```

`article_*`, `journalist_*`, and `multimedia_search_text` / `multimedia_semantic` are filled via `copy_to` from the corresponding root source fields. `multimedia[].asset_vector` must be supplied by the indexing pipeline (length = `dims`).

## Query patterns (illustrative)

### Article — full-text
```json
{
  "query": {
    "multi_match": {
      "query": "subway funding",
      "fields": ["title^3", "summary^2", "body", "article_search_text"]
    }
  }
}
```

### Article — semantic
```json
{
  "query": {
    "semantic": {
      "field": "article_semantic",
      "query": "public transport investment in the city"
    }
  }
}
```

### Article — hybrid (lexical + semantic only)
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
                "fields": ["title^3", "summary^2", "body", "article_search_text"]
              }
            }
          }
        },
        {
          "standard": {
            "query": {
              "semantic": {
                "field": "article_semantic",
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

### Journalist — hybrid (lexical + semantic only)
```json
{
  "retriever": {
    "rrf": {
      "retrievers": [
        {
          "standard": {
            "query": {
              "multi_match": {
                "query": "city hall correspondent",
                "fields": ["journalist_names^2", "journalist_bios", "journalist_search_text"]
              }
            }
          }
        },
        {
          "standard": {
            "query": {
              "semantic": {
                "field": "journalist_semantic",
                "query": "city hall correspondent"
              }
            }
          }
        }
      ]
    }
  }
}
```

### Multimedia — vector (kNN on nested asset embeddings)
```json
{
  "query": {
    "nested": {
      "path": "multimedia",
      "query": {
        "knn": {
          "field": "multimedia.asset_vector",
          "query_vector": [0.01, 0.02],
          "k": 10,
          "num_candidates": 50
        }
      },
      "inner_hits": {}
    }
  }
}
```

### Multimedia — hybrid (lexical + semantic + vector)
```json
{
  "retriever": {
    "rrf": {
      "retrievers": [
        {
          "standard": {
            "query": {
              "multi_match": {
                "query": "council chamber vote",
                "fields": ["multimedia_text", "multimedia_search_text"]
              }
            }
          }
        },
        {
          "standard": {
            "query": {
              "semantic": {
                "field": "multimedia_semantic",
                "query": "council chamber vote"
              }
            }
          }
        },
        {
          "knn": {
            "field": "multimedia.asset_vector",
            "query_vector": [0.01, 0.02],
            "k": 10,
            "num_candidates": 50
          }
        }
      ],
      "rank_constant": 60,
      "rank_window_size": 50
    }
  }
}
```

## Sync rules from relational model

| Relational change | Elasticsearch action |
|-------------------|----------------------|
| Article create/update | Upsert document `_id = article_id` |
| Authorship change | Re-index parent article document |
| Article metadata change | Re-index parent article document |
| Multimedia add/update/delete | Re-index parent article document (refresh `multimedia_text` + each `asset_vector`) |
| Journalist profile change | Re-index all articles by that journalist (refresh `journalist_names` / `journalist_bios`) |
| Article delete | Delete document by `_id` |

## Out of scope for this mapping
- Index aliases / ILM policies (add when environments are defined)
- Ingest pipeline implementation details (inference endpoint + multimedia embedding pipeline)
- Security / document-level permissions (no user model in prototype)
- Multimodal `semantic` field for raw image/audio/video bytes (future iteration)
