# UI Design — Search Landing & Results

**Product:** Gotham News & Media Browser  
**Pages:** `/` (search landing), `/results` (results)  
**Theme:** Light pastel  
**Mockups:** `ui-mockups/`

## Visual direction

Soft **pastel newsroom** — airy surfaces, mint / sky / coral accents, shared chrome on every page.

| Token | Value | Role |
|-------|-------|------|
| `--bg` | `#F4F7FB` | Page wash |
| `--surface` | `#FFFFFF` | Panels |
| `--ink` | `#2C3E5A` | Primary text |
| `--mint` | `#9FD5C5` | Full-text terms / secondary CTA |
| `--sky` | `#9EC5E8` | Articles accent |
| `--coral` | `#EFB0B8` | Multimedia accent |
| Brand font | **Fraunces** | Wordmark / titles |
| UI font | **Sora** | Controls |

Atmosphere: pastel radial washes (sky, coral, mint). No dark theme.

## Shared chrome

Every page includes the same **header** and **footer** via `chrome.js` mounts (`#site-header`, `#site-footer`):

- Header: brand wordmark + nav (Search · Article results · Media results · Admin)
- Footer: prototype label + quick links

Thymeleaf will later replace `chrome.js` with `layout.html` fragments.

## Landing (`/`) — two panels

| Panel | Methods | Full-text UX |
|-------|---------|--------------|
| Articles | Full-text · Semantic · Hybrid | Multi-term **checkboxes** + journalist filter |
| Multimedia | Full-text · Semantic · Hybrid · Vector | Multi-term **checkboxes**; Vector shows file drop |

### Multi-term full-text
1. User adds terms (input + Add / Enter).  
2. Each term appears as a **checked checkbox**.  
3. Unchecking excludes that term from the submitted query.  
4. Checked terms are joined into `q` (and may also post as repeated `term=` params in the Spring app).  
5. Semantic / Hybrid switch back to a single query field.

## Results (`/results`)

- Same shared header/footer  
- Entity badge + method tabs for that entity only  
- Full-text mode keeps the multi-term checkbox builder  
- Filters, sort, pagination unchanged in structure  

## Mockups

| File | Page |
|------|------|
| `ui-mockups/index.html` | Landing |
| `ui-mockups/results-articles.html` | Article results |
| `ui-mockups/results-multimedia.html` | Multimedia results |
| `ui-mockups/styles.css` | Pastel theme |
| `ui-mockups/chrome.js` | Shared header/footer + terms helper |

Preview: `python3 -m http.server -d ui-mockups 8765`
