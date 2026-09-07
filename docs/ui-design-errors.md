# UI Design — Fault Tolerance & Error Pages

**Product:** Gotham News & Media Browser  
**Applies to:** **All** HTTP endpoints (search, results, `/journalist/**`, `/article/**`, health consumers, file uploads)  
**Theme:** Light pastel (shared chrome)  
**Mockup:** `ui-mockups/error.html`

## Goal

The backend must be **fault tolerant**. Unexpected failures must never strand the user on a blank page, container stack trace, or raw JSON. Every failure ends in a **clear, branded error page** (or in-form validation when the error is a recoverable input problem) that explains **why** something failed and what to do next.

## UX principles

1. **Same chrome** — header (brand + service legends) and MIT footer on every error page.  
2. **One job** — calm headline, short reason, next actions. No dashboard clutter.  
3. **Human reason** — plain language (“Elasticsearch could not be reached”, “Article not found”, “Video exceeds 50 MiB”).  
4. **No secret leakage** — never show API keys, SA JSON, connection strings, or full exception stacks in the browser.  
5. **Actionable** — primary links: Search · Journalists · Articles · Go back.  
6. **Honest status** — correct HTTP status; page title matches severity.  
7. **Traceability** — show a short **reference id** (request id) the user can quote; full stack stays in server logs only.

## Error categories

| Kind | HTTP | User-facing title (example) | Reason source |
|------|------|-----------------------------|---------------|
| Validation / bad input | 400 | “Check your input” | Field messages preferred **on the form**; use error page only if unbound |
| Not found | 404 | “We couldn’t find that” | Missing journalist/article `_id`, unknown route |
| Payload / media limit | 413 | “Upload too large” | Image/audio/video limits |
| Dependency unavailable | 503 | “A required service is unavailable” | ES, ImageBind, or GCS timeout/down |
| Unexpected | 500 | “Something went wrong” | Uncaught exceptions — sanitized message + reference id |

## Page content (required)

```text
[ shared header ]

  Something went wrong          ← expressive but calm (Fraunces)
  We couldn’t complete that     ← one short supporting sentence
  request.

  Reason
  Elasticsearch timed out while loading articles.

  Reference  a1b2c3d4

  [ Back to search ]  Journalists · Articles

[ shared footer ]
```

## Technical contract (Spring / Thymeleaf)

| Mechanism | Role |
|-----------|------|
| `@ControllerAdvice` + problem handlers | Map exceptions → error view model for **all** controllers |
| `ErrorController` / `error.html` | Container/fallback errors (Whitelabel **disabled**) |
| Domain exceptions | e.g. `NotFoundException`, `DependencyException`, `MediaLimitException` with user-safe messages |
| Client timeouts | ES, ImageBind, GCS: bounded timeouts/retries where safe; on failure → dependency error page |
| Logging | ERROR with reference id + full stack; UI gets sanitized reason only |

Whitelabel error pages must be **off**. Default path: Thymeleaf template `error.html` (and optional `error/{status}.html`).

## Form endpoints vs error page

- **Expected validation** (blank title, invalid email): re-render the form with field errors (best UX).  
- **Unexpected** mid-request failures (ES 5xx after submit): **error page** with reason — still fault tolerant, not a silent redirect.

## Mockup

| File | Role |
|------|------|
| `ui-mockups/error.html` | Static example of the global error page |
