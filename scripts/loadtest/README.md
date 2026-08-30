# Load test — purchase → dispatch → delivery

Synthetic load for the "can one 12 GB box hold this" question (see the
`vps-undersized-swap-added` / `monolith-dissolution-direction` memories). Drives the
real TRANSFER checkout flow from **9 concurrent buyers + 1 admin** for ~10 minutes.

**All data is simulated and disposable.** Full footprint in [`DATA-MAP.md`](./DATA-MAP.md).

## Run it (local)

```bash
# full compose stack must be up (kafka + microservices + cache + observability)
bash scripts/loadtest/prep.sh        # snapshot + inflate stock + provider -> LOG
bash scripts/loadtest/run.sh         # k6 in docker on infra_pe_net + resource sampler
bash scripts/loadtest/cleanup.sh     # restore + delete simulated data + verify
```

Knobs: `BUYERS=5 HOLD=4m bash scripts/loadtest/run.sh`.

## Artifacts (gitignored)

| File | What |
|---|---|
| `k6-output.log` | k6 console + end-of-run summary |
| `summary.json` | machine-readable metrics |
| `metrics-sample.log` | `docker stats` + monolith Prometheus, every 15 s |
| `.stock-snapshot.sql`, `.provider-snapshot.txt`, `.run-start.txt` | prep state for cleanup |

## What to read in the result

- `lt_purchase_complete` vs `lt_purchase_failed` — did buyers get to DELIVERED
- `http_req_duration p(95)` and `lt_wait_shipped_ms` — latency + admin backlog
- `metrics-sample.log`: any container near its `mem_limit`, `hikaricp_connections_pending > 0`
  (pool starvation), `system_load_average_1m` vs 6 cores, Kafka lag climbing
- container restarts / OOM: `docker ps` + `docker inspect --format '{{.RestartCount}} {{.State.OOMKilled}}'`

## Prod run

Same scripts, but point `BASE_URL` at the prod network / host and run `prep`/`cleanup` against
the prod DB. Do it watched, with a gentler ramp (`HOLD=3m`), ready to Ctrl-C. Prod order data is
also disposable today, but tidy up afterward anyway.
