# Gotham News & Media Browser — Index Diagrams

**Indexes:** `gotham-journalists` · `gotham-media-browser`  
**IDs:** ES auto `_id` (top-level) · app `multimedia_element_id` (nested)  
**Related:** [`elasticsearch-denormalized-model.md`](./elasticsearch-denormalized-model.md)

## Capability matrix

```mermaid
flowchart LR
  subgraph ART["Articles — UI panel"]
    A1["Full-text + journalist param"]
    A2["Semantic"]
    A3["Hybrid"]
  end
  subgraph MED["Multimedia — UI panel"]
    M1["Full-text"]
    M2["Semantic"]
    M3["Hybrid"]
    M4["Vector"]
  end
  subgraph J["Journalists — /journalist CRUD"]
    J1["No search UI"]
    J2["Feeds article bylines"]
  end
```

## Dual-index topology

```mermaid
flowchart LR
  Admin --> JI[(gotham-journalists<br/>auto _id)]
  Admin --> AI[(gotham-media-browser<br/>auto _id)]
  JI -->|nested journalists.journalist_id| AI
  AI --> GCS[(GCS public objects)]
  IB[ImageBind] -->|article_embedding / asset_vector| AI
```

## Article document

```mermaid
flowchart TB
  DOC["Article _id"]
  DOC --> ED["Editorial + status DRAFT|PUBLISHED|ARCHIVED"]
  DOC --> JN["journalists[] nested<br/>journalist_id = gotham-journalists._id"]
  DOC --> MM["multimedia[] nested<br/>multimedia_element_id app keyword<br/>storage_uri public GCS<br/>asset_vector 1024"]
  DOC --> FTS["article_search_text · multimedia_search_text · journalist_search_text"]
  DOC --> EMB["article_embedding 1024"]
```
