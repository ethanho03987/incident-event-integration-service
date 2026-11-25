#!/usr/bin/env bash
# After a k6 run: checks that every event k6 got a 202 for was processed, with nothing stuck or dead-lettered.
# Run against a fresh stack (docker compose down -v && docker compose up -d --build --wait) so the
# database counts only contain this run's events.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SUMMARY="${SUMMARY:-loadtest/results/summary.json}"
OUT="loadtest/results/verification.md"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"

accepted=$(jq '.metrics.accepted_events.count' "$SUMMARY")
duplicates=$(jq '.metrics.duplicate_events.count // 0' "$SUMMARY")
iterations=$(jq '.metrics.iterations.count' "$SUMMARY")
rate=$(jq '.metrics.iterations.rate' "$SUMMARY")
p95=$(jq '.metrics.http_req_duration["p(95)"]' "$SUMMARY")
failed_rate=$(jq '.metrics.http_req_failed.value' "$SUMMARY")
echo "k6 reported $accepted accepted events (${rate} events/sec), $duplicates duplicates recognised"

# Processing is asynchronous: wait for the queue to drain.
deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
while true; do
  stats=$(curl -fs "$BASE_URL/api/v1/incidents/stats")
  processed=$(echo "$stats" | jq '.PROCESSED')
  pending=$(echo "$stats" | jq '.RECEIVED + .QUEUED')
  failed=$(echo "$stats" | jq '.FAILED')
  if [ "$pending" -eq 0 ] && [ "$processed" -ge "$accepted" ]; then break; fi
  if [ "$(date +%s)" -ge "$deadline" ]; then echo "Timed out waiting for processing"; break; fi
  sleep 2
done

dlq=$(curl -fs "$BASE_URL/actuator/metrics/incidents.queue.depth?tag=queue:dead_letter" | jq '.measurements[0].value')

lost=$(( accepted - processed ))
status="PASS"
if [ "$lost" -ne 0 ] || [ "$pending" -ne 0 ] || [ "$failed" -ne 0 ]; then status="FAIL"; fi

cat > "$OUT" <<EOF
| Metric | Value |
|---|---|
| Target rate | 50 events/sec |
| Achieved rate | $(printf '%.1f' "$rate") events/sec |
| Requests sent (iterations) | $iterations |
| Events accepted (HTTP 202) | $accepted |
| Duplicates correctly rejected | $duplicates |
| Events processed | $processed |
| Still pending | $pending |
| Failed / dead-lettered | $failed (DLQ depth: $dlq) |
| **Lost messages** | **$lost** |
| HTTP p95 latency | $(printf '%.1f' "$p95") ms |
| HTTP error rate | $failed_rate |
| Result | **$status** |
EOF
cat "$OUT"
[ "$status" = "PASS" ]
