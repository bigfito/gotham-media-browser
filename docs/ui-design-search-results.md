# UI Design — Search Landing & Results

**Product:** Gotham News & Media Browser  
**Theme:** Light pastel (compatible soft wash background)  
**Chrome:** Shared header + footer on all pages  
**Mockups:** `ui-mockups/`

## Background

Solid light wash `#F7F9FC` → soft tint `#EEF5F8` (no dark ink, no heavy vignette). Pastel accents stay on controls/panels only.

## Full-text attributes (from denormalized model)

When **Full-text** is selected, the UI shows **checkboxes for text attributes** from the ES denormalized document — not free-form keyword chips.

### Articles panel / article results
| Checkbox value | Source field |
|----------------|--------------|
| `title` | article title |
| `subtitle` | subtitle |
| `summary` | summary |
| `body` | body |
| `section` | metadata section |
| `tags` | metadata tags |
| `location` | metadata location |
| `source` | metadata source |
| `seo_title` | SEO title |
| `seo_description` | SEO description |
| `seo_keywords` | SEO keywords |
| `journalist_names` | flattened bylines |
| `journalist_bios` | flattened bios |
| `journalist_search_text` | journalist catch-all projection |
| `article_search_text` | article catch-all projection |

Default checked: `title`, `subtitle`, `summary`, `body`.  
Also: optional **journalist** filter param on full-text.

### Multimedia panel / media results
| Checkbox value | Source field |
|----------------|--------------|
| `multimedia.title` | nested media title |
| `multimedia.caption` | nested caption |
| `multimedia.description` | nested description |
| `multimedia.alt_text` | nested alt text |
| `multimedia.credit` | nested credit |
| `multimedia_text` | flattened media text |
| `multimedia_search_text` | catch-all projection |

Default checked: title, caption, description, alt_text.

Backend maps checked `fields` into a `multi_match` / `bool` should over those ES fields.

## Shared chrome
`chrome.js` injects header/footer; Thymeleaf will use layout fragments later.

## Mockups
| File | Role |
|------|------|
| `index.html` | Dual-panel landing |
| `results-articles.html` | Article results |
| `results-multimedia.html` | Multimedia results |
| `styles.css` | Light pastel theme |
| `chrome.js` | Shared header/footer + mode toggles |
