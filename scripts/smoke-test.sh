#!/usr/bin/env bash
#
# End-to-end smoke test against a running stack, through the gateway.
#
#   docker compose up --build -d
#   ./scripts/smoke-test.sh                                 # core flows
#   MAILPIT=http://localhost:8025 ./scripts/smoke-test.sh   # + email checks
#
# Needs only bash and curl. Every run uses fresh accounts and aliases, so it
# can be re-run against the same stack.

set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
MAILPIT="${MAILPIT:-}"
ANALYTICS_TIMEOUT="${ANALYTICS_TIMEOUT:-60}"

RUN_ID="$(date +%s)$RANDOM"
EMAIL="smoke-$RUN_ID@example.com"
OTHER_EMAIL="smoke-other-$RUN_ID@example.com"
PASSWORD="smoke-password-123"
ALIAS="smoke-$RUN_ID"

BODY_FILE="$(mktemp)"
HEADERS_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE" "$HEADERS_FILE"' EXIT

PASS=0
FAIL=0

if [ -t 1 ]; then GREEN=$'\e[32m'; RED=$'\e[31m'; DIM=$'\e[2m'; RESET=$'\e[0m'; else GREEN=; RED=; DIM=; RESET=; fi

ok()   { PASS=$((PASS + 1)); echo "  ${GREEN}✓${RESET} $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  ${RED}✗ $1${RESET}"; echo "    ${DIM}$(head -c 400 "$BODY_FILE")${RESET}"; }
section() { echo; echo "$1"; }

# req METHOD PATH [TOKEN] [JSON_BODY] [extra curl args...] -> sets STATUS, BODY
req() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  shift 4 2>/dev/null || shift $#
  local args=(-s -o "$BODY_FILE" -D "$HEADERS_FILE" -w '%{http_code}' -X "$method" "$BASE$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H "Content-Type: application/json" -d "$body")
  STATUS="$(curl "${args[@]}" "$@")"
  BODY="$(cat "$BODY_FILE")"
}

expect_status() { # expected description
  if [ "$STATUS" = "$1" ]; then ok "$2 ($STATUS)"; else fail "$2: expected $1, got $STATUS"; fi
}

json_str() { printf '%s' "$BODY" | sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -n 1; }
json_num() { printf '%s' "$BODY" | sed -n "s/.*\"$1\":\([0-9][0-9]*\).*/\1/p" | head -n 1; }
header()   { grep -i "^$1:" "$HEADERS_FILE" | head -n 1 | cut -d' ' -f2- | tr -d '\r'; }

echo "ShortLink smoke test against $BASE (run $RUN_ID)"

section "Auth"
req POST /api/auth/register "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
expect_status 201 "register"
req POST /api/auth/register "" "{\"email\":\"${EMAIL^^}\",\"password\":\"$PASSWORD\"}"
expect_status 409 "same email in different case is a duplicate"
req POST /api/auth/login "" "{\"email\":\"$EMAIL\",\"password\":\"wrong-password\"}"
expect_status 401 "wrong password rejected"
req POST /api/auth/login "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
expect_status 200 "login"
TOKEN="$(json_str accessToken)"
[ -n "$TOKEN" ] && ok "got access token" || fail "no accessToken in login response"
req GET /api/auth/me "$TOKEN"
expect_status 200 "GET /me with token"

section "Creating links"
req POST /api/urls "" '{"originalUrl":"https://example.com"}'
expect_status 401 "create without token"
req POST /api/urls "$TOKEN" '{"originalUrl":"https://example.com/generated"}'
expect_status 201 "create with generated code"
GENERATED="$(json_str shortCode)"
req POST /api/urls "$TOKEN" "{\"originalUrl\":\"https://example.com/original\",\"customAlias\":\"$ALIAS\"}"
expect_status 201 "create with custom alias"
req POST /api/urls "$TOKEN" "{\"originalUrl\":\"https://example.com/x\",\"customAlias\":\"$ALIAS\"}"
expect_status 409 "duplicate alias"
req POST /api/urls "$TOKEN" '{"originalUrl":"https://example.com/x","customAlias":"actuator"}'
expect_status 400 "reserved alias"
req POST /api/urls "$TOKEN" "{\"originalUrl\":\"$BASE/loop\"}"
expect_status 400 "URL pointing at the shortener itself"
req POST /api/urls "$TOKEN" '{"originalUrl":"ftp://example.com/file"}'
expect_status 400 "non-http URL"
req POST /api/urls "$TOKEN" '{"originalUrl":"https://example.com","expiresAt":"2001-01-01T00:00:00Z"}'
expect_status 400 "expiry in the past"

section "Redirects"
req GET "/$ALIAS"
expect_status 302 "redirect"
LOCATION="$(header Location)"
[ "$LOCATION" = "https://example.com/original" ] && ok "Location is the original URL" || fail "Location was '$LOCATION'"
[ "$(header Cache-Control)" = "no-store" ] && ok "redirect is not cacheable" || fail "Cache-Control was '$(header Cache-Control)'"
req GET "/$GENERATED"
expect_status 302 "generated code redirects"
req GET "/$ALIAS" "" "" -A "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1" -e "https://twitter.com/someone/status/1"
req GET "/$ALIAS" "" "" -A "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36" -e "https://news.ycombinator.com/item?id=1"
req GET /no-such-code-here
expect_status 404 "unknown code"
req GET /favicon.ico
expect_status 404 "non-code path rejected at the gateway"

section "Ownership"
req POST /api/auth/register "" "{\"email\":\"$OTHER_EMAIL\",\"password\":\"$PASSWORD\"}"
req POST /api/auth/login "" "{\"email\":\"$OTHER_EMAIL\",\"password\":\"$PASSWORD\"}"
OTHER_TOKEN="$(json_str accessToken)"
req GET "/api/urls/$ALIAS" "$OTHER_TOKEN"
expect_status 404 "another user can't see the link"
req DELETE "/api/urls/$ALIAS" "$OTHER_TOKEN"
expect_status 404 "another user can't delete the link"
req GET "/api/urls/$ALIAS" "$TOKEN"
expect_status 200 "owner can read metadata"
req GET "/api/urls?size=5" "$TOKEN"
expect_status 200 "list my links"
printf '%s' "$BODY" | grep -q "\"shortCode\":\"$ALIAS\"" && ok "list contains the alias" || fail "alias missing from list"

section "Editing (cache write-through)"
req PUT "/api/urls/$ALIAS" "$TOKEN" '{"originalUrl":"https://example.com/updated"}'
expect_status 200 "update destination"
req GET "/$ALIAS"
[ "$(header Location)" = "https://example.com/updated" ] && ok "redirect follows the edit immediately" || fail "Location after edit was '$(header Location)'"

section "Analytics (eventually consistent via Kafka -> Elasticsearch)"
EXPECTED_CLICKS=4
CLICKS=0
for _ in $(seq 1 "$ANALYTICS_TIMEOUT"); do
  req GET "/api/analytics/links/$ALIAS?days=7" "$TOKEN"
  CLICKS="$(json_num totalClicks)"
  [ "${CLICKS:-0}" -ge "$EXPECTED_CLICKS" ] && break
  sleep 1
done
if [ "${CLICKS:-0}" -ge "$EXPECTED_CLICKS" ]; then ok "totalClicks reached $CLICKS"; else fail "totalClicks was ${CLICKS:-none} after ${ANALYTICS_TIMEOUT}s (expected $EXPECTED_CLICKS)"; fi
printf '%s' "$BODY" | grep -q '"value":"twitter.com"' && ok "referrer twitter.com recorded" || fail "twitter.com missing from referrers"
printf '%s' "$BODY" | grep -q '"value":"iOS"' && ok "iOS recorded" || fail "iOS missing from operating systems"
req GET "/api/analytics/links/$ALIAS" "$OTHER_TOKEN"
[ "$(json_num totalClicks)" = "0" ] && ok "another user sees zero clicks" || fail "another user saw clicks"
req GET /api/analytics/links "$TOKEN"
expect_status 200 "top links"

section "Deactivation"
req DELETE "/api/urls/$ALIAS" "$TOKEN"
expect_status 204 "deactivate"
req GET "/$ALIAS"
expect_status 404 "deactivated link no longer redirects"
req DELETE "/api/urls/$ALIAS" "$TOKEN"
expect_status 204 "deactivate is idempotent"

if [ -n "$MAILPIT" ]; then
  section "Email (via Mailpit at $MAILPIT)"
  mail_count() {
    curl -s -G "$MAILPIT/api/v1/search" --data-urlencode "query=to:\"$EMAIL\" subject:\"$1\"" \
      | sed -n 's/.*"messages_count":\([0-9]*\).*/\1/p'
  }
  for subject in "Welcome to ShortLink" "first click"; do
    found=0
    for _ in $(seq 1 30); do
      [ "$(mail_count "$subject")" -ge 1 ] 2>/dev/null && { found=1; break; }
      sleep 1
    done
    [ "$found" = 1 ] && ok "email \"$subject\" delivered" || fail "no \"$subject\" email for $EMAIL"
  done
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "${GREEN}All $PASS checks passed.${RESET}"
else
  echo "${RED}$FAIL of $((PASS + FAIL)) checks failed.${RESET}"
  exit 1
fi
