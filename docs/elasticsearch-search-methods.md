# Elasticsearch Search Methods — Query Cookbook

**Index:** `gotham-media-browser` (all public search)  
**Companion index:** `gotham-journalists` (CRUD / byline source only — **no** public search UI)  
**Client:** Elasticsearch Java API Client (Spring Boot BOM ~9.4.x)  
**Embeddings:** Meta ImageBind **1024-d** (app-supplied; no Elastic `semantic_text`)  
**Related:** [`elasticsearch-denormalized-model.md`](./elasticsearch-denormalized-model.md) · [`frontend-information-architecture.md`](./frontend-information-architecture.md) · mapping [`gotham-media-browser.mapping.json`](../elasticsearch/gotham-media-browser.mapping.json)

This document is the **Elasticsearch-side contract** for every UI search mode. Agents implement these shapes (or equivalent Java API builders), not ad-hoc queries.

---

## 1. Capability matrix

| UI entity | `mode` | ES technique | Vector field | Notes |
|-----------|--------|--------------|--------------|-------|
| Article | `fulltext` | BM25 `multi_match` (+ filters) | — | Optional `journalist` nested filter |
| Article | `semantic` | kNN | `article_embedding` | Query text → ImageBind → `query_vector` |
| Article | `hybrid` | **RRF**(BM25, kNN) | `article_embedding` | Same filters on both legs |
| Article | `vector` | — | — | **Not allowed** — reject with **HTTP 400** error page |
| Multimedia | `fulltext` | Nested BM25 + **`inner_hits`** | — | Asset-level cards |
| Multimedia | `semantic` | Nested kNN + **`inner_hits`** | `multimedia.asset_vector` | Text → ImageBind |
| Multimedia | `hybrid` | **RRF**(nested BM25, nested kNN) | `multimedia.asset_vector` | |
| Multimedia | `vector` | Nested kNN + **`inner_hits`** | `multimedia.asset_vector` | **File** → ImageBind (not text) |

Always set `"track_total_hits": true` for pagination UI.

---

## 2. Shared request parameters

### Pagination (UI → ES)

| UI | ES |
|----|----|
| `page` (1-based) | `from = (page - 1) * size` |
| `size` ∈ {25, 50, 100} | `size` |

### Common filters (bool `filter` clause)

Apply on **parent article** documents unless noted:

| UI param | ES clause |
|----------|-----------|
| `status` (multi) | `terms` on `status` (`DRAFT` \| `PUBLISHED` \| `ARCHIVED`) |
| `section` | `term` on `section` (keyword) |
| `language` | `term` on `language` |
| `published_from` / `published_to` | `range` on `published_at` (`gte` / `lte`) |
| `mediaType` (multimedia only) | nested `term` on `multimedia.media_type` (`IMAGE` \| `AUDIO` \| `VIDEO`) |

### Sort

| UI `sort` | ES `sort` |
|-----------|-----------|
| `relevance` | omit (score) — default for FTS/kNN/RRF |
| `published_at_desc` | `published_at: desc` |
| `published_at_asc` | `published_at: asc` |
| `title_asc` | `title.keyword: asc` |

When using kNN/RRF, prefer score order unless the UI explicitly requests a field sort (then apply after or as a post-filter pattern — prototype may restrict field sort to `fulltext` mode).

---

## 3. Full-text field remap

UI checkbox values → analyzable ES fields for `multi_match`:

| UI `fields` value | ES field(s) |
|-------------------|-------------|
| `title` | `title` |
| `subtitle` | `subtitle` |
| `summary` | `summary` |
| `body` | `body` |
| `section` | `section.text` |
| `tags` | `tags.text` |
| `location` | `location.text` |
| `source` | `source.text` |
| `seo_title` | `seo_title` |
| `seo_description` | `seo_description` |
| `seo_keywords` | `seo_keywords` |
| `journalist_names` | `journalist_names` |
| `journalist_bios` | `journalist_bios` |
| `journalist_search_text` | `journalist_search_text` |
| `article_search_text` | `article_search_text` |
| `multimedia.title` | `multimedia.title` (inside `nested`) |
| `multimedia.caption` | `multimedia.caption` |
| `multimedia.description` | `multimedia.description` |
| `multimedia.alt_text` | `multimedia.alt_text` |
| `multimedia.credit` | `multimedia.credit` |
| `multimedia_text` | `multimedia_text` (parent projection) |
| `multimedia_search_text` | `multimedia_search_text` |

If `fields` is empty, use defaults: articles `title,subtitle,summary,body`; multimedia nested `title,caption,description,alt_text`.

---

## 4. Article — Full-text (`mode=fulltext`)

**Precondition:** `entity=article`, query string `q`.

```http
POST /gotham-media-browser/_search
```

```json
{
  "from": 0,
  "size": 25,
  "track_total_hits": true,
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "transit funding",
            "fields": ["title", "subtitle", "summary", "body"],
            "type": "best_fields",
            "operator": "and"
          }
        }
      ],
      "filter": [
        { "terms": { "status": ["PUBLISHED", "DRAFT", "ARCHIVED"] } },
        { "term": { "section": "Politics" } },
        { "term": { "language": "en" } },
        {
          "range": {
            "published_at": {
              "gte": "2026-01-01T00:00:00Z",
              "lte": "2026-09-06T23:59:59Z"
            }
          }
        },
        {
          "bool": {
            "should": [
              {
                "nested": {
                  "path": "journalists",
                  "query": {
                    "term": { "journalists.journalist_id": "j_lois_lane" }
                  }
                }
              },
              {
                "nested": {
                  "path": "journalists",
                  "query": {
                    "match": { "journalists.full_name": "Lois Lane" }
                  }
                }
              }
            ],
            "minimum_should_match": 1
          }
        }
      ]
    }
  }
}
```

**Journalist param rules**
- If value looks like an id (or was chosen from master list): prefer `term` on `journalists.journalist_id` only.  
- If free text: `match` on `journalists.full_name` (and optionally `journalist_names` on parent).  
- Omit the journalist `should` block when param absent.

---

## 5. Article — Semantic (`mode=semantic`)

**App step:** `query_vector = ImageBind.embedText(q)` → `float[1024]`.

```json
{
  "from": 0,
  "size": 25,
  "track_total_hits": true,
  "knn": {
    "field": "article_embedding",
    "query_vector": [0.01, 0.02],
    "k": 25,
    "num_candidates": 100,
    "filter": {
      "bool": {
        "filter": [
          { "terms": { "status": ["PUBLISHED", "DRAFT"] } }
        ]
      }
    }
  }
}
```

(`query_vector` shown truncated — must be length **1024**.)

---

## 6. Article — Hybrid (`mode=hybrid`)

Use the **retriever RRF** API (ES 8.8+ / 9.x / Serverless). Both legs share the same filters.

```json
{
  "size": 25,
  "track_total_hits": true,
  "retriever": {
    "rrf": {
      "rank_window_size": 50,
      "rank_constant": 60,
      "retrievers": [
        {
          "standard": {
            "query": {
              "bool": {
                "must": [
                  {
                    "multi_match": {
                      "query": "transit funding",
                      "fields": ["title", "subtitle", "summary", "body"],
                      "type": "best_fields"
                    }
                  }
                ],
                "filter": [
                  { "terms": { "status": ["PUBLISHED", "DRAFT"] } }
                ]
              }
            }
          }
        },
        {
          "knn": {
            "field": "article_embedding",
            "query_vector": [0.01, 0.02],
            "k": 25,
            "num_candidates": 100,
            "filter": {
              "bool": {
                "filter": [
                  { "terms": { "status": ["PUBLISHED", "DRAFT"] } }
                ]
              }
            }
          }
        }
      ]
    }
  }
}
```

**Pagination note:** classic `from` with RRF retrievers can be limited on some deployments. Prototype strategy: request `rank_window_size` ≥ `page * size`, then slice the window in the app for the current page, **or** use `from`/`size` when the cluster supports it on `retriever` searches. Document the chosen approach in code comments when implementing P8-T02.

---

## 7. Multimedia — Full-text (`mode=fulltext`)

Search nested multimedia text (and optional parent projections). Return **asset-level** hits via `inner_hits`.

```json
{
  "from": 0,
  "size": 25,
  "track_total_hits": true,
  "query": {
    "bool": {
      "must": [
        {
          "nested": {
            "path": "multimedia",
            "query": {
              "bool": {
                "must": [
                  {
                    "multi_match": {
                      "query": "council chamber",
                      "fields": [
                        "multimedia.title",
                        "multimedia.caption",
                        "multimedia.description",
                        "multimedia.alt_text"
                      ],
                      "type": "best_fields"
                    }
                  }
                ],
                "filter": [
                  { "terms": { "multimedia.media_type": ["IMAGE", "AUDIO", "VIDEO"] } }
                ]
              }
            },
            "inner_hits": {
              "name": "matched_media",
              "size": 5,
              "_source": [
                "multimedia.multimedia_element_id",
                "multimedia.media_type",
                "multimedia.storage_uri",
                "multimedia.title",
                "multimedia.caption",
                "multimedia.description",
                "multimedia.alt_text",
                "multimedia.credit",
                "multimedia.mime_type",
                "multimedia.position"
              ]
            }
          }
        }
      ],
      "filter": [
        { "terms": { "status": ["PUBLISHED", "DRAFT"] } }
      ]
    }
  },
  "_source": ["title", "status", "section", "slug", "published_at"]
}
```

**UI mapping:** one result card per `inner_hits.matched_media` hit; parent article from the outer hit `_source` / `_id`.

If the user also checked parent projections (`multimedia_text`, `multimedia_search_text`), add a `should`/`must` `multi_match` on those parent fields (without nested). Prefer nested+`inner_hits` as the primary path so cards stay asset-scoped.

---

## 8. Multimedia — Semantic (`mode=semantic`)

**App step:** `query_vector = ImageBind.embedText(q)`.

```json
{
  "size": 25,
  "track_total_hits": true,
  "knn": {
    "field": "multimedia.asset_vector",
    "query_vector": [0.01, 0.02],
    "k": 25,
    "num_candidates": 100,
    "filter": {
      "bool": {
        "filter": [
          { "terms": { "status": ["PUBLISHED", "DRAFT"] } }
        ]
      }
    },
    "inner_hits": {
      "name": "matched_media",
      "size": 3,
      "_source": [
        "multimedia.multimedia_element_id",
        "multimedia.media_type",
        "multimedia.storage_uri",
        "multimedia.title",
        "multimedia.caption"
      ]
    }
  },
  "_source": ["title", "status", "section", "slug"]
}
```

If the cluster requires an explicit nested knn context for nested vectors, use the Java API’s nested-knn builder equivalent; keep `inner_hits` so the UI can render the matched asset.

Optional: nest a `media_type` filter inside the knn filter using a `nested` query on `multimedia` when filtering by type for semantic mode.

---

## 9. Multimedia — Hybrid (`mode=hybrid`)

RRF of nested BM25 (section 7 query) and nested kNN (section 8). Same filters on both retrievers; both should request `inner_hits` where supported. If a retriever leg cannot attach `inner_hits`, fetch the parent hit and re-query nested matches for displayed ids (fallback acceptable for prototype).

```json
{
  "size": 25,
  "track_total_hits": true,
  "retriever": {
    "rrf": {
      "rank_window_size": 50,
      "rank_constant": 60,
      "retrievers": [
        {
          "standard": {
            "query": {
              "nested": {
                "path": "multimedia",
                "query": {
                  "multi_match": {
                    "query": "council chamber",
                    "fields": [
                      "multimedia.title",
                      "multimedia.caption",
                      "multimedia.description",
                      "multimedia.alt_text"
                    ]
                  }
                },
                "inner_hits": { "name": "matched_media", "size": 3 }
              }
            }
          }
        },
        {
          "knn": {
            "field": "multimedia.asset_vector",
            "query_vector": [0.01, 0.02],
            "k": 25,
            "num_candidates": 100,
            "filter": {
              "bool": {
                "filter": [
                  { "terms": { "status": ["PUBLISHED", "DRAFT"] } }
                ]
              }
            }
          }
        }
      ]
    }
  }
}
```

---

## 10. Multimedia — Vector (`mode=vector`)

**UI:** multipart file upload (not `q` text).  
**App steps:**

1. Validate media limits (IMAGE 10 MiB · AUDIO 20 MiB / 5 min · VIDEO 50 MiB / 90 s).  
2. `query_vector = ImageBind.embedImage|Audio|Video(bytes)` → `float[1024]`.  
3. Run the **same nested kNN** as §8 (semantic), optionally filtered by detected `media_type`.

No BM25 leg. Article `mode=vector` is rejected.

---

## 11. Recommended tunables (prototype defaults)

| Parameter | Default | Where |
|-----------|---------|--------|
| `size` | 25 | all |
| `k` (knn) | `size` | semantic / vector / hybrid knn leg |
| `num_candidates` | `max(100, 4 * k)` | knn |
| `rank_window_size` | 50 | RRF |
| `rank_constant` | 60 | RRF |
| `inner_hits.size` | 3–5 | multimedia |
| ES client timeout | bounded (e.g. 5–10s) | fault tolerance → user error page |
| ImageBind timeout | bounded (e.g. 30–120s for video) | vector / semantic |

Expose as `application.properties` under `gotham.search.*` when implementing.

---

## 12. Response → UI mapping

| Search | Outer hit | Card content |
|--------|-----------|--------------|
| Article any mode | article `_id` + `_source` | Title, deck/summary, status, bylines, date → link `/article/{id}` |
| Multimedia any mode | article parent + `inner_hits.matched_media` | HTML5 player from `storage_uri`, caption/title, media_type, parent link |

Always return `hits.total.value` (with `track_total_hits`) for “Page X of Y”.

---

## 13. Fault tolerance (search path)

| Failure | HTTP to user | Reason example |
|---------|--------------|----------------|
| ImageBind down/timeout on semantic/hybrid/vector | 503 | “ImageBind could not embed the query.” |
| ES timeout / connection error | 503 | “Elasticsearch could not be reached while searching.” |
| `mode=vector` on articles | 400 | “Vector search is only available for multimedia.” |
| Empty `q` on text modes | 400 or form validation | “Enter a search query.” |
| Upload over limit (vector) | 413 | “Video exceeds 50 MiB.” |

See [`ui-design-errors.md`](./ui-design-errors.md).

---

## 14. Out of scope

- Searching `gotham-journalists` as a results entity  
- Elastic managed inference / `semantic_text`  
- `search_after` deep pagination (prototype uses `from`/`size` within soft limits)  

---

## 15. Implementation plan mapping

| Doc section | Plan task |
|-------------|-----------|
| §4 Article FTS | P7-T02 |
| §7 Multimedia FTS | P7-T03 |
| §5 / §8 Semantic | P8-T01 |
| §6 / §9 Hybrid | P8-T02 |
| §10 Vector | P8-T03 |
