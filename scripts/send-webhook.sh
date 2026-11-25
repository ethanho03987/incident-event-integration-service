#!/usr/bin/env bash
# Sends a correctly signed partner webhook, the same way a real partner system would.
#   ./scripts/send-webhook.sh            # new random event
#   ./scripts/send-webhook.sh ptn-123    # specific id (run twice to see duplicate handling)
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SECRET="${WEBHOOK_SECRET:-local-dev-secret-change-me}"
ID="${1:-ptn-$(date +%s)-$RANDOM}"
NOW_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
TS=$(date +%s)

BODY=$(printf '{"id":"%s","provider":"north-alarm-monitoring","category":"fire","priority":1,"location":{"lat":53.4808,"lng":-2.2426},"summary":"Smoke alarm, occupants trapped on 2nd floor","reported_at":"%s"}' "$ID" "$NOW_ISO")

# signature = hex(HMAC-SHA256(secret, "<timestamp>.<body>"))
SIG=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.*= //')

curl -s -w '\nHTTP %{http_code}\n' -X POST "$BASE_URL/api/v1/webhooks/partner" \
  -H 'Content-Type: application/json' \
  -H "X-Webhook-Timestamp: $TS" \
  -H "X-Webhook-Signature: sha256=$SIG" \
  -d "$BODY"
