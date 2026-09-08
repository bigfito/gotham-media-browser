#!/usr/bin/env bash
#
# smoke.sh — exercise a running gotham-web end to end and fail loudly on any regression.
#
# Checks: health legends, landing, all four search modes (article + multimedia), the article
# mode=vector rejection, and a journalist create → list → delete CRUD round-trip. Semantic / hybrid
# / vector depend on imagebind-service; when it is DOWN the app must degrade to a branded HTTP 503,
# so this script asserts 200 when ImageBind is up and 503 when it is down (both are correct).
#
# Requirements: bash 4+, curl. Usage:
#   BASE_URL=http://localhost:8080 ./docs/demo/smoke.sh
# Exits 0 only if every check passes; non-zero (with a FAIL list) otherwise.
#
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MEDIA="$HERE/fixtures/media"
fails=0

pass()      { echo "  ok    $*"; }
warn()      { echo "  warn  $*"; }
failcheck() { echo "  FAIL  $*" >&2; fails=$((fails + 1)); }
command -v curl >/dev/null 2>&1 || { echo "ERROR: curl is required" >&2; exit 2; }

status_get() { curl -s -o /dev/null -w '%{http_code}' "$BASE_URL$1"; }
expect_get() { # path expected label
  local code; code="$(status_get "$1")"
  [[ "$code" == "$2" ]] && pass "$3 ($1 → $code)" || failcheck "$3 ($1 → $code, expected $2)"
}

echo "==> Smoke against $BASE_URL"

# 1. Health legends (P1-T03). Elasticsearch is required; ImageBind is optional but changes what
#    the semantic/hybrid/vector modes are expected to return.
es="$(status_get /api/health/elasticsearch)"
[[ "$es" == "200" ]] && pass "Elasticsearch health UP" || failcheck "Elasticsearch health ($es, expected 200)"
ib="$(status_get /api/health/imagebind)"
imagebind_up=false
if [[ "$ib" == "200" ]]; then imagebind_up=true; pass "ImageBind health UP"
else warn "ImageBind health DOWN ($ib) — semantic/hybrid/vector expected to degrade to HTTP 503"; fi
vec_expected=$([[ "$imagebind_up" == true ]] && echo 200 || echo 503)

# 2. Landing + full-text (Elasticsearch only — always 200).
expect_get "/" 200 "landing"
expect_get "/results?entity=article&mode=fulltext&q=transit%20funding" 200 "article full-text"
expect_get "/results?entity=multimedia&mode=fulltext&q=museum" 200 "multimedia full-text"

# 3. Article mode=vector is always rejected with a branded HTTP 400 (P8-T03 / P7-T04).
expect_get "/results?entity=article&mode=vector&q=anything" 400 "article vector rejected (400)"

# 4. Semantic + hybrid (need ImageBind → 200 up / 503 down).
expect_get "/results?entity=article&mode=semantic&q=transit%20funding" "$vec_expected" "article semantic"
expect_get "/results?entity=article&mode=hybrid&q=transit%20funding" "$vec_expected" "article hybrid"
expect_get "/results?entity=multimedia&mode=semantic&q=museum" "$vec_expected" "multimedia semantic"

# 5. Multimedia vector upload (multipart POST of the image fixture).
if [[ -f "$MEDIA/skyline.png" ]]; then
  vcode="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/results" \
            -F entity=multimedia -F mode=vector -F "media=@$MEDIA/skyline.png")"
  [[ "$vcode" == "$vec_expected" ]] && pass "multimedia vector upload (→ $vcode)" \
    || failcheck "multimedia vector upload (→ $vcode, expected $vec_expected)"
else
  warn "skipping vector upload — $MEDIA/skyline.png not found"
fi

# 6. CRUD round-trip: create a throwaway journalist, see it listed, delete it (cleanup).
tag="smoke$$"
jcode="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/journalist" \
          --data-urlencode "firstName=Smoke" --data-urlencode "lastName=$tag" \
          --data-urlencode "email=$tag@example.test" --data-urlencode "bio=smoke check")"
[[ "$jcode" =~ ^(302|200)$ ]] && pass "journalist create (→ $jcode)" || failcheck "journalist create (→ $jcode)"
id="$(curl -s "$BASE_URL/journalist" | grep -oE '/journalist/[A-Za-z0-9_-]+/delete' | head -1 \
      | sed -E 's#/journalist/([A-Za-z0-9_-]+)/delete#\1#')"
if [[ -n "$id" ]]; then
  pass "journalist listed (id $id)"
  dcode="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/journalist/$id/delete")"
  [[ "$dcode" =~ ^(302|200)$ ]] && pass "journalist delete cleanup (→ $dcode)" || failcheck "journalist delete (→ $dcode)"
else
  failcheck "journalist list/capture id"
fi

echo
if [[ $fails -eq 0 ]]; then
  echo "SMOKE PASSED"
  exit 0
fi
echo "SMOKE FAILED — $fails check(s) failed"
exit 1
