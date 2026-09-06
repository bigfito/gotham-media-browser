# Gotham News & Media Browser — Denormalized Model Diagram

**Index:** `gotham-media-browser` (Elastic Cloud Serverless)  
**Embeddings:** Meta ImageBind · 1024-d · no Elastic `semantic_text`  
**Related:** [`elasticsearch-denormalized-model.md`](./elasticsearch-denormalized-model.md) · [`architecture-components.md`](./architecture-components.md)

## Search capability matrix

```mermaid
flowchart LR
  subgraph ART["Article"]
    A1["Full-text"]
    A2["Semantic = kNN article_embedding"]
    A3["Hybrid = BM25 + kNN"]
    A4["Vector tab — disabled"]
  end
  subgraph JOUR["Journalist"]
    J1["Full-text"]
    J2["Semantic = kNN journalist_embedding"]
    J3["Hybrid = BM25 + kNN"]
    J4["Vector tab — disabled"]
  end
  subgraph MEDIA["Multimedia"]
    M1["Full-text"]
    M2["Semantic = text→ImageBind→asset_vector"]
    M3["Hybrid = BM25 + kNN"]
    M4["Vector = media→ImageBind→asset_vector"]
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
  subgraph DOC["Article document — gotham-media-browser"]
    ED["Editorial + metadata"]
    JNEST["journalists[] nested"]
    MNEST["multimedia[] nested<br/>storage_uri → GCS<br/>asset_vector dense_vector 1024"]
    FTS["article_search_text / journalist_search_text / multimedia_search_text"]
    EMB["article_embedding / journalist_embedding<br/>dense_vector 1024 · ImageBind text"]
  end

  IB["imagebind-service"] -->|text embed| EMB
  IB -->|image/audio/video embed| MNEST
  APP["gotham-web"] --> GCS[(GCS)]
  MNEST -.->|storage_uri| GCS
```

## Component context

```mermaid
flowchart LR
  Browser --> Web["gotham-web<br/>Java 25 · Boot 4.1.1 · Thymeleaf"]
  Web --> ES[(Elastic Cloud Serverless)]
  Web --> IB[ImageBind Docker]
  Web --> GCS[(Google Cloud Storage)]
```

## Legend

| Item | Meaning |
|------|---------|
| `*_search_text` | BM25 full-text |
| `article_embedding` / `journalist_embedding` | ImageBind **text** embeddings for semantic/hybrid |
| `multimedia.asset_vector` | ImageBind **media** embedding for semantic/vector/hybrid |
| Vector tab | Multimedia only; query is a media file |
