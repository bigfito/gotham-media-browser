#!/usr/bin/env bash
#
# seed.sh — load the static demo fixtures into a running gotham-web via its HTTP CRUD only.
#
# It POSTs journalists to /journalist and articles to /article (multipart), exactly as the browser
# forms do — it never writes to Elasticsearch or GCS directly. Journalist Elasticsearch ids are
# captured from the /journalist list page (newest-first) so articles can reference them as bylines.
#
# Requirements: bash 4+, curl. The app must be running with Elasticsearch (and, for media, GCS)
# configured. Usage:
#   BASE_URL=http://localhost:8080 ./docs/demo/seed.sh
#   TEXT_ONLY=1 BASE_URL=http://localhost:8080 ./docs/demo/seed.sh   # skip media (no GCS needed)
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TEXT_ONLY="${TEXT_ONLY:-}"   # when set (e.g. TEXT_ONLY=1), articles are created without media uploads
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIX="$HERE/fixtures"
MEDIA="$FIX/media"

fail() { echo "ERROR: $*" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || fail "curl is required"
[[ -f "$FIX/journalists.tsv" ]] || fail "missing $FIX/journalists.tsv"
[[ -f "$FIX/articles.tsv" ]]   || fail "missing $FIX/articles.tsv"

echo "==> Target: $BASE_URL"
health="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/health/elasticsearch" || true)"
[[ "$health" == "200" ]] || echo "WARN: Elasticsearch health returned '$health' (expected 200 UP) — writes may fail."

declare -A EMAIL_TO_ID=()

# --- Journalists -------------------------------------------------------------
jcount=0
while IFS=$'\t' read -r first last email bio; do
  [[ "$first" == "first_name" ]] && continue          # header
  [[ -z "${first// }" ]] && continue                  # blank line
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/journalist" \
    --data-urlencode "firstName=$first" \
    --data-urlencode "lastName=$last" \
    --data-urlencode "email=$email" \
    --data-urlencode "bio=$bio")"
  [[ "$code" == "302" || "$code" == "200" ]] || fail "journalist '$first $last' POST -> HTTP $code"

  # Newest-first list: the first delete-form action names the journalist we just created.
  id="$(curl -s "$BASE_URL/journalist" \
        | grep -oE '/journalist/[A-Za-z0-9_-]+/delete' | head -1 \
        | sed -E 's#/journalist/([A-Za-z0-9_-]+)/delete#\1#')"
  [[ -n "$id" ]] || fail "could not capture id for journalist '$email' from /journalist"
  EMAIL_TO_ID["$email"]="$id"
  jcount=$((jcount + 1))
  echo "  + journalist $first $last  ($email -> $id)"
done < "$FIX/journalists.tsv"
echo "==> $jcount journalists loaded"

# --- Articles ----------------------------------------------------------------
acount=0
while IFS=$'\t' read -r title section tags status language bylines media summary body; do
  [[ "$title" == "title" ]] && continue               # header
  [[ -z "${title// }" ]] && continue                  # blank line

  args=(-s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/article"
        -F "title=$title" -F "summary=$summary" -F "body=$body"
        -F "status=$status" -F "language=$language" -F "section=$section" -F "tags=$tags")

  order=1
  IFS=',' read -ra emails <<< "$bylines"
  for e in "${emails[@]}"; do
    id="${EMAIL_TO_ID[$e]:-}"
    [[ -n "$id" ]] || fail "article '$title' references unknown journalist '$e'"
    args+=(-F "journalistIds=$id" -F "bylineOrder[$id]=$order" -F "role[$id]=AUTHOR")
    order=$((order + 1))
  done

  media_note=""
  if [[ -z "$TEXT_ONLY" && "$media" != "-" && -n "$media" ]]; then
    [[ -f "$MEDIA/$media" ]] || fail "article '$title' media file not found: $MEDIA/$media"
    args+=(-F "mediaFiles=@$MEDIA/$media")
    media_note=", media=$media"
  elif [[ -n "$TEXT_ONLY" && "$media" != "-" && -n "$media" ]]; then
    media_note=", media skipped (TEXT_ONLY)"
  fi

  code="$(curl "${args[@]}")"
  [[ "$code" == "302" || "$code" == "200" ]] || fail "article '$title' POST -> HTTP $code"
  acount=$((acount + 1))
  echo "  + article $title  [$status/$section$media_note]"
done < "$FIX/articles.tsv"
echo "==> $acount articles loaded"

echo "Done. Open $BASE_URL/ to search, or $BASE_URL/journalist and $BASE_URL/article to browse."
