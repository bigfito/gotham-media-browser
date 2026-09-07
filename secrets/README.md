# secrets/

Local, **untracked** secret material. Nothing real in this folder is committed.

## Files

| File | Tracked? | Purpose |
|------|----------|---------|
| `gcp-sa.json` | **No** (gitignored) | Real Google Cloud service-account key used by `gotham-web` to write media to the GCS bucket. |
| `gcp-sa.json.example` | Yes | Redacted shape of the SA key, for reference. |
| `README.md` | Yes | This file. |

The ignore rules live in the repo root `.gitignore`:

```
secrets/*.json
!secrets/*.json.example
```

So every `secrets/*.json` is ignored **except** the `*.example`.

## How to provision (local dev)

1. Obtain the GCP service-account JSON key (Console → IAM & Admin → Service Accounts → Keys).
2. Save it here as `secrets/gcp-sa.json` (exact name — it is referenced by
   `gotham.gcs.credentials-file=secrets/gcp-sa.json`).
3. Confirm it is ignored:
   ```bash
   git check-ignore -v secrets/gcp-sa.json
   ```
4. Put the real ES/GCS **values** (endpoint, api-key, bucket, project) in
   `application-local.properties` at the **repo root** (gitignored; kept out of
   `src/main/resources` so it never bundles into the jar), copied from
   `application-local.properties.example`. In Docker, use `.env` instead (see `.env.example`).

## CI / production

Inject the key from the platform secret store (never commit it), and mount/write it to
the path referenced by `gotham.gcs.credentials-file`.

> ⚠️ Never paste real keys into `application.properties`, commits, logs, or chat. If a key
> is exposed, **rotate it** in GCP.
