# Gotham News & Media Browser — Denormalized Model Diagram

**Index:** `gotham-media-browser` (Elasticsearch 9.x)  
**Related:** [`elasticsearch-denormalized-model.md`](./elasticsearch-denormalized-model.md) · [`../elasticsearch/gotham-media-browser.mapping.json`](../elasticsearch/gotham-media-browser.mapping.json)

## Document structure

```mermaid
flowchart TB
  subgraph DOC["Article document — index: gotham-media-browser<br/>_id = article_id"]
    direction TB

    subgraph ED["Editorial"]
      E1["article_id : long"]
      E2["title / subtitle / summary / body : text"]
      E3["slug / status / language : keyword"]
      E4["published_at / created_at / updated_at : date"]
    end

    subgraph MD["Article metadata — flattened"]
      M1["section / tags / location / source : keyword + text"]
      M2["seo_title / seo_description / seo_keywords : text"]
      M3["canonical_url : keyword"]
    end

    subgraph FLAT["Root search helpers — indexer-populated"]
      F1["journalist_names : text + keyword"]
      F2["multimedia_text : text"]
    end

    subgraph NEST["Nested objects"]
      direction LR
      subgraph J["journalists[] — nested"]
        J1["journalist_id : long"]
        J2["first_name / last_name / full_name : text"]
        J3["email : keyword · bio : text"]
        J4["byline_order : integer · contribution_role : keyword"]
      end
      subgraph MM["multimedia[] — nested"]
        MM1["multimedia_element_id : long · media_type / mime_type : keyword"]
        MM2["caption / title / description / alt_text / credit : text"]
        MM3["width / height / duration_ms / codec / bitrate / …"]
        MM4["storage_uri / checksum : keyword non-indexed"]
      end
    end

    subgraph SEARCH["Search projections"]
      S1["search_text : text — full-text / BM25"]
      S2["content_semantic : semantic_text — semantic / hybrid"]
      S3["multimedia_semantic : semantic_text — media semantic"]
      S4["content_vector : dense_vector dims=768 — vector / kNN"]
    end
  end

  E2 -.->|copy_to| S1
  E2 -.->|copy_to| S2
  M1 -.->|copy_to| S1
  M1 -.->|copy_to| S2
  F1 -.->|copy_to| S1
  F1 -.->|copy_to| S2
  F2 -.->|copy_to| S1
  F2 -.->|copy_to| S2
  F2 -.->|copy_to| S3

  classDef semantic fill:#D1FAE5,stroke:#059669,color:#064E3B;
  classDef vector fill:#DBEAFE,stroke:#2563EB,color:#1E3A8A;
  classDef lexical fill:#FEF3C7,stroke:#D97706,color:#78350F;
  classDef nested fill:#F3E8FF,stroke:#7C3AED,color:#4C1D95;
  class S1 lexical;
  class S2,S3 semantic;
  class S4 vector;
  class J,MM nested;
```

## Search capability overlay

```mermaid
flowchart LR
  Q["User query"] --> FT["Full-text<br/>multi_match on title^3,<br/>summary^2, body, search_text"]
  Q --> SEM["Semantic<br/>semantic query on<br/>content_semantic"]
  Q --> VEC["Vector<br/>knn on content_vector"]
  FT --> RRF["Hybrid RRF retriever"]
  SEM --> RRF
  VEC --> RRF
  RRF --> HITS["Ranked article hits"]

  classDef q fill:#F8FAFC,stroke:#334155;
  classDef path fill:#ECFDF5,stroke:#047857;
  classDef hybrid fill:#EFF6FF,stroke:#1D4ED8;
  class Q q;
  class FT,SEM,VEC path;
  class RRF,HITS hybrid;
```

## Relational → denormalized mapping

```mermaid
flowchart LR
  subgraph REL["Relational ER — approved"]
    A[ARTICLE]
    AM[ARTICLE_METADATA]
    AA[ARTICLE_AUTHORSHIP]
    J[JOURNALIST]
    ME[MULTIMEDIA_ELEMENT]
    MM[MULTIMEDIA_METADATA]
    A --- AM
    A --- AA
    AA --- J
    A --- ME
    ME --- MM
  end

  subgraph ES["Elasticsearch document"]
    D["gotham-media-browser<br/>Article document"]
  end

  A --> D
  AM --> D
  J --> D
  AA --> D
  ME --> D
  MM --> D
```

## Legend

| Color / block | Meaning |
|---------------|---------|
| Editorial / metadata | Source fields denormalized onto the root document |
| `journalists[]` / `multimedia[]` | `nested` for filter-safe child queries |
| `search_text` | Lexical catch-all (BM25) |
| `*_semantic` | `semantic_text` for semantic and hybrid search |
| `content_vector` | Explicit `dense_vector` (768, cosine, int8_hnsw) |

Static SVG export: [`../diagrams/gotham-media-browser-denormalized.svg`](../diagrams/gotham-media-browser-denormalized.svg)  
Rendered overview image: [`../diagrams/gotham-media-browser-es-model.png`](../diagrams/gotham-media-browser-es-model.png)

![Denormalized Elasticsearch document model](../diagrams/gotham-media-browser-es-model.png)
