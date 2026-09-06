# Gotham News & Media Browser — Elasticsearch Denormalized Model

**Index name:** `gotham-media-browser`  
**Target:** Elastic Cloud Serverless (Elasticsearch 9.x API)  
**Source of truth (logical):** Relational ER in [`er-design.md`](./er-design.md) (approved artifact)  
**Runtime persistence:** **Elasticsearch only** (no RDBMS). Binaries in **GCS**.  
**Embeddings:** **Meta ImageBind** helper (1024-d), not Elastic managed inference  
**Document grain:** one search document per **Article**  
**Architecture:** [`architecture-components.md`](./architecture-components.md)  
**Diagram:** [`elasticsearch-denormalized-diagram.md`](./elasticsearch-denormalized-diagram.md)

## Entity search capabilities (authoritative)

| Entity | Full-text (BM25) | Semantic | Hybrid (RRF) | Vector (kNN) |
|--------|:----------------:|:--------:|:------------:|:------------:|
| **Article** | Yes | Yes | Yes | **No** (no Vector tab) |
| **Journalist** | Yes | Yes | Yes | **No** (no Vector tab) |
| **Multimedia asset** | Yes | Yes | Yes | **Yes** |

### How semantic / vector work with ImageBind

ImageBind uses **one joint embedding space** (1024-d). The Spring app calls the ImageBind container synchronously.

| UX tab | Mechanism |
|--------|-----------|
| **Full-text** | BM25 on `*_search_text` |
| **Semantic** | Query **text** → ImageBind → kNN on entity embedding field |
| **Hybrid** | RRF(BM25, semantic kNN) |
| **Vector** (multimedia only) | Query **image/audio/video** → ImageBind → kNN on `multimedia.asset_vector` |

For multimedia, Semantic and Vector both knn against `asset_vector`; they differ by **query modality**.

## Design goals

| Capability | Article | Journalist | Multimedia |
|------------|---------|------------|------------|
| **Full-text** | `article_search_text` | `journalist_search_text` | `multimedia_search_text` + nested text |
| **Semantic** | kNN `article_embedding` | kNN `journalist_embedding` | kNN `multimedia.asset_vector` (text query) |
| **Hybrid** | RRF(BM25, article kNN) | RRF(BM25, journalist kNN) | RRF(BM25, asset kNN) |
| **Vector** | — | — | kNN `multimedia.asset_vector` (media query) |

## Denormalized document shape

```text
Article document
├── identity & editorial fields
├── flat article metadata
├── journalist_names / journalist_bios     ← indexer-flattened
├── multimedia_text                        ← indexer-flattened media text
├── journalists[] nested
├── multimedia[] nested
│   ├── ... metadata ...
│   ├── storage_uri                        ← GCS object URI
│   └── asset_vector (dense_vector 1024) ← ImageBind image/audio/video
├── article_search_text                    ← FTS
├── journalist_search_text                 ← FTS
├── multimedia_search_text                 ← FTS
├── article_embedding (dense_vector 1024)  ← ImageBind text (semantic/hybrid)
└── journalist_embedding (dense_vector 1024) ← ImageBind text (semantic/hybrid)
```

### Indexer responsibilities
- Flatten `journalist_names`, `journalist_bios`, `multimedia_text`.
- Upload binaries to GCS; store `storage_uri` on each multimedia element.
- Call ImageBind for article text → `article_embedding`.
- Call ImageBind for journalist text → `journalist_embedding`.
- Call ImageBind for each media file → `multimedia[].asset_vector`.
- Do **not** use Elastic `semantic_text` / inference endpoints in this prototype.

## Field type strategy

| Concern | ES type | Notes |
|---------|---------|-------|
| Identity / facets | `keyword` / `long` / `integer` / `date` | Filters, sorting |
| Prose | `text` (`gotham_english`) | Full-text |
| Article / journalist semantic | `dense_vector` dims **1024** | App-supplied ImageBind text embeddings |
| Multimedia vector / semantic | `dense_vector` dims **1024** on `asset_vector` | App-supplied ImageBind media embeddings |
| Nested journalists / media | `nested` | Filter-safe child queries |

## Mapping reference

Ready-to-apply definition: [`elasticsearch/gotham-media-browser.mapping.json`](../elasticsearch/gotham-media-browser.mapping.json)

### Search projection fields

| Field | Type | Role |
|-------|------|------|
| `article_search_text` | `text` | Article BM25 |
| `journalist_search_text` | `text` | Journalist BM25 |
| `multimedia_search_text` | `text` | Multimedia BM25 |
| `article_embedding` | `dense_vector` (1024) | Article semantic / hybrid |
| `journalist_embedding` | `dense_vector` (1024) | Journalist semantic / hybrid |
| `multimedia.asset_vector` | `dense_vector` (1024) | Multimedia semantic / vector / hybrid |
| `multimedia.storage_uri` | `keyword` | GCS URI (non-indexed payload OK) |

## Sample document (excerpt)

```json
{
  "article_id": 1001,
  "title": "Gotham Transit Expansion Clears Final Vote",
  "summary": "City council approved funding for the cross-river line.",
  "body": "Full article body...",
  "slug": "gotham-transit-expansion-clears-final-vote",
  "status": "PUBLISHED",
  "language": "en",
  "journalist_names": ["Lois Lane", "Clark Kent"],
  "journalist_bios": "City hall correspondent. Investigative reporter.",
  "multimedia_text": "Council chamber after the vote",
  "journalists": [
    {
      "journalist_id": 12,
      "first_name": "Lois",
      "last_name": "Lane",
      "full_name": "Lois Lane",
      "bio": "City hall correspondent.",
      "byline_order": 1,
      "contribution_role": "AUTHOR"
    }
  ],
  "multimedia": [
    {
      "multimedia_element_id": 501,
      "media_type": "IMAGE",
      "storage_uri": "gs://gotham-media-browser/articles/1001/hero.jpg",
      "mime_type": "image/jpeg",
      "position": 1,
      "caption": "Council chamber after the vote",
      "title": "Council chamber",
      "width": 1920,
      "height": 1080,
      "asset_vector": [0.01, 0.02]
    }
  ],
  "article_embedding": [0.01, 0.02],
  "journalist_embedding": [0.03, 0.04]
}
```

Vectors are length **1024** at runtime (truncated above).

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

### Article — semantic (ImageBind text → kNN)
```json
{
  "knn": {
    "field": "article_embedding",
    "query_vector": [0.01, 0.02],
    "k": 10,
    "num_candidates": 50
  }
}
```

### Article — hybrid (RRF BM25 + kNN)
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
          "knn": {
            "field": "article_embedding",
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

### Multimedia — vector (media query → ImageBind → nested kNN)
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

## Sync / write path

| Event | Actions |
|-------|---------|
| Article create/update | ImageBind text embed → upsert ES doc `_id = article_id` |
| Media add/update | Upload GCS → ImageBind media embed → reindex parent article |
| Media delete | Delete GCS object → reindex parent without element |
| Journalist credit/profile change | Re-embed journalist text → reindex affected articles |
| Article delete | Delete ES doc; delete related GCS objects |

## Out of scope
- Elastic managed `semantic_text` / inference endpoints  
- RDBMS system of record  
- Async embedding workers (sync is required for local prototype)  
- AuthN/AuthZ  
