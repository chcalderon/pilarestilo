# Load test — purchase → dispatch → delivery

Synthetic load for the "can one 12 GB box hold this" question (see the
`vps-undersized-swap-added` / `monolith-dissolution-direction` memories). Drives the
real TRANSFER checkout flow from **9 concurrent buyers + 1 admin** for ~10 minutes.

**All data is simulated and disposable.** Full footprint in [`DATA-MAP.md`](./DATA-MAP.md).

## Run it (local)

```bash
# full compose stack must be up (kafka + microservices + cache + observability)
bash scripts/loadtest/prep.sh        # snapshot + inflate stock + provider LOG + 2 extra admins
bash scripts/loadtest/run.sh         # k6 in docker on infra_pe_net + resource sampler
bash scripts/loadtest/cleanup.sh     # restore + delete simulated data + verify
```

Knobs (env): `BUYERS` (default 9), `ADMIN_VUS` (default 1, max 3 — the CMS operators),
`HOLD` (default 8m), `ADMIN_DURATION` (default 9m30s), `LABEL` (artifact suffix).

Staged run to find the ceiling — `prep` once, `cleanup` once at the end:

```bash
bash scripts/loadtest/prep.sh
for n in 15 30 50; do
  BUYERS=$n ADMIN_VUS=3 HOLD=6m ADMIN_DURATION=7m30s LABEL=b$n bash scripts/loadtest/run.sh
done
bash scripts/loadtest/cleanup.sh
```

## Artifacts (gitignored)

| File | What |
|---|---|
| `k6-output.log` | k6 console + end-of-run summary |
| `summary.json` | machine-readable metrics |
| `metrics-sample.log` | `docker stats` + monolith Prometheus, every 15 s |
| `.stock-snapshot.sql`, `.provider-snapshot.txt`, `.run-start.txt` | prep state for cleanup |

## What to read in the result

- **`checks` rate** — order + proof both 2xx. This is the real "did the request succeed" signal.
- `http_req_duration p(95)` — end-to-end latency under load.
- `lt_step_payment_visible_ms` — how far the async `OrderCreated → payment` Kafka consumer falls
  behind (p95 3 s at 15 buyers, ~16 s at 50 — that consumer is the first thing to saturate).
- `lt_purchase_complete` vs `lt_wait_shipped_ms` — in `delivery` mode, the operator backlog.
- `metrics-sample.log`: any container near its `mem_limit`, `hikaricp_connections_pending > 0`
  (pool starvation), `system_load_average_1m` vs cores, Kafka lag climbing.
- container restarts / OOM: `docker inspect --format '{{.RestartCount}} {{.State.OOMKilled}}'`.
- **`docker logs infra-backend-1 | grep ERROR`** — the ground truth. A clean run has zero.

**Ignore `http_req_failed`.** This flow polls `GET /payments/order/{id}` for a Kafka-async row
(404 until it lands) and 3 operators race on claim/dispatch (409) — those are expected and passed
per-request, but the metric still reports 40–80 %. It is not a health signal for this test.

## Prod run

Same scripts, but point `BASE_URL` at the prod network / host and run `prep`/`cleanup` against
the prod DB. Do it watched, with a gentler ramp (`HOLD=3m`), ready to Ctrl-C. Prod order data is
also disposable today, but tidy up afterward anyway.
