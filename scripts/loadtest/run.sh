#!/usr/bin/env bash
# Run the k6 purchase-flow load test against the local stack + sample resource use.
# Usage:  bash scripts/loadtest/run.sh          (9 buyers, 8m hold)
#         BUYERS=5 HOLD=4m bash scripts/loadtest/run.sh
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
NET="${LT_NET:-infra_pe_net}"
BUYERS="${BUYERS:-9}"
ADMIN_VUS="${ADMIN_VUS:-1}"
HOLD="${HOLD:-8m}"
ADMIN_DURATION="${ADMIN_DURATION:-9m30s}"
LABEL="${LABEL:-b${BUYERS}}"

date -u +%Y-%m-%dT%H:%M:%S > "$DIR/.run-start.txt"
echo "[run] start $(cat "$DIR/.run-start.txt")Z  buyers=$BUYERS admins=$ADMIN_VUS hold=$HOLD net=$NET label=$LABEL"

# ---- resource sampler (background) --------------------------------------
{
  for i in $(seq 1 60); do
    echo "=== $(date -u +%H:%M:%S) ==="
    docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.CPUPerc}}' \
      | grep -E 'backend|_service|pe_kafka|pe_postgres|pe_redis' || true
    curl -sk -m5 https://localhost/api/actuator/prometheus 2>/dev/null \
      | grep -E '^(system_load_average_1m|hikaricp_connections_active|hikaricp_connections_pending|hikaricp_connections_max|jvm_gc_pause_seconds_sum|kafka_consumer_fetch_manager_records_lag_max)' \
      | sed 's/^/  monolith /' || true
    sleep 15
  done
} > "$DIR/metrics-sample.log" 2>&1 &
SAMPLER=$!
trap 'kill $SAMPLER 2>/dev/null || true' EXIT

# ---- k6 ----------------------------------------------------------------
MSYS_NO_PATHCONV=1 docker run --rm --network "$NET" \
  -e BASE_URL=http://backend:8080/api -e BUYERS="$BUYERS" -e ADMIN_VUS="$ADMIN_VUS" \
  -e HOLD="$HOLD" -e ADMIN_DURATION="$ADMIN_DURATION" \
  -v "$DIR":/lt grafana/k6 run /lt/purchase-flow.js \
  --summary-export="/lt/summary-${LABEL}.json" 2>&1 | tee "$DIR/k6-${LABEL}.log"
cp "$DIR/metrics-sample.log" "$DIR/metrics-${LABEL}.log" 2>/dev/null || true

kill $SAMPLER 2>/dev/null || true
echo "[run] done. artifacts: k6-output.log, summary.json, metrics-sample.log"
