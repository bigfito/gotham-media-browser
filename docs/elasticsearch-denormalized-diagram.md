# Gotham News & Media Browser — Denormalized Model Diagram

**Index:** `gotham-media-browser` (Elasticsearch 9.x)  
**Related:** [`elasticsearch-denormalized-model.md`](./elasticsearch-denormalized-model.md) · [`../elasticsearch/gotham-media-browser.mapping.json`](../elasticsearch/gotham-media-browser.mapping.json)

## Search capability matrix

```mermaid
flowchart LR
  subgraph ART["Article entity"]
    A1["Full-text"]
    A2["Semantic"]
    A3["Hybrid = FTS + Semantic"]
    A4["Vector — not enabled"]
  end
  subgraph JOUR["Journalist entity"]
    J1["Full-text"]
    J2["Semantic"]
    J3["Hybrid = FTS + Semantic"]
    J4["Vector — not enabled"]
  end
  subgraph MEDIA["Multimedia asset entity"]
    M1["Full-text"]
    M2["Semantic"]
    M3["Hybrid = FTS + Semantic + Vector"]
    M4["Vector / kNN — enabled"]
  end

  classDef on fill:#D1FAE5,stroke:#059669,color:#064E3B;
  classDef off fill:#F1F5F9,stroke:#94A3B8,color:#64748B;
  classDef vec fill:#DBEAFE,stroke:#2563EB,color:#1E3A8A;
  class A1,A2,A3,J1,J2,J3,M1,M2,M3 on;
  class A4,J4 off;
  class M4 vec;
```

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

    subgraph FLAT["Root helpers — indexer-populated"]
      F1["journalist_names / journalist_bios : text"]
      F2["multimedia_text : text"]
    end

    subgraph NEST["Nested objects"]
      direction LR
      subgraph J["journalists[] — nested · no vector"]
        J1["journalist_id : long"]
        J2["first_name / last_name / full_name : text"]
        J3["email : keyword · bio : text"]
        J4["byline_order : integer · contribution_role : keyword"]
      end
      subgraph MM["multimedia[] — nested · vector enabled"]
        MM1["multimedia_element_id · media_type / mime_type"]
        MM2["caption / title / description / alt_text / credit : text"]
        MM3["width / height / duration_ms / codec / …"]
        MM4["asset_vector : dense_vector dims=768"]
      end
    end

    subgraph SEARCH["Entity-scoped search projections"]
      S1["article_search_text : text"]
      S2["article_semantic : semantic_text"]
      S3["journalist_search_text : text"]
      S4["journalist_semantic : semantic_text"]
      S5["multimedia_search_text : text"]
      S6["multimedia_semantic : semantic_text"]
    end
  end

  E2 -.->|copy_to| S1
  E2 -.->|copy_to| S2
  M1 -.->|copy_to| S1
  M1 -.->|copy_to| S2
  F1 -.->|copy_to| S3
  F1 -.->|copy_to| S4
  F2 -.->|copy_to| S5
  F2 -.->|copy_to| S6

  classDef semantic fill:#D1FAE5,stroke:#059669,color:#064E3B;
  classDef vector fill:#DBEAFE,stroke:#2563EB,color:#1E3A8A;
  classDef lexical fill:#FEF3C7,stroke:#D97706,color:#78350F;
  classDef nested fill:#F3E8FF,stroke:#7C3AED,color:#4C1D95;
  class S1,S3,S5 lexical;
  class S2,S4,S6 semantic;
  class MM4 vector;
  class J nested;
  class MM nested;
```

## Search flows by entity

```mermaid
flowchart TB
  Q["User query"] --> EA["Article path"]
  Q --> EJ["Journalist path"]
  Q --> EM["Multimedia path"]

  EA --> AFT["FTS: article_search_text"]
  EA --> ASEM["Semantic: article_semantic"]
  AFT --> AHY["Hybrid RRF"]
  ASEM --> AHY

  EJ --> JFT["FTS: journalist_search_text"]
  EJ --> JSEM["Semantic: journalist_semantic"]
  JFT --> JHY["Hybrid RRF"]
  JSEM --> JHY

  EM --> MFT["FTS: multimedia_search_text"]
  EM --> MSEM["Semantic: multimedia_semantic"]
  EM --> MVEC["Vector: multimedia.asset_vector"]
  MFT --> MHY["Hybrid RRF"]
  MSEM --> MHY
  MVEC --> MHY

  classDef art fill:#ECFDF5,stroke:#047857;
  classDef jour fill:#FFF7ED,stroke:#C2410C;
  classDef media fill:#EFF6FF,stroke:#1D4ED8;
  class EA,AFT,ASEM,AHY art;
  class EJ,JFT,JSEM,JHY jour;
  class EM,MFT,MSEM,MVEC,MHY media;
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
| Article projections | FTS + semantic + hybrid only |
| Journalist projections | FTS + semantic + hybrid only |
| Multimedia projections | FTS + semantic + hybrid + **vector** (`asset_vector`) |
| `journalists[]` | Nested; no dense vector |
| `multimedia[]` | Nested; owns the only explicit `dense_vector` |

Static SVG export: [`../diagrams/gotham-media-browser-denormalized.svg`](../diagrams/gotham-media-browser-denormalized.svg)  
Rendered overview image: [`../diagrams/gotham-media-browser-es-model.png`](../diagrams/gotham-media-browser-es-model.png)

![Denormalized Elasticsearch document model](../diagrams/gotham-media-browser-es-model.png)
