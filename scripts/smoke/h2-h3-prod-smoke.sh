#!/usr/bin/env bash
#
# H-2 / H-3 production smoke — posts the 2 newest products to Instagram + Facebook:
#   - the newest one immediately
#   - the 2nd-newest one scheduled for +15 minutes
#
# These are REAL posts to the shop's IG/FB. Run once, on purpose.
#
# Usage:
#   PE_TOKEN=<your admin JWT> bash scripts/smoke/h2-h3-prod-smoke.sh
#
# Getting the token: log in to https://pilarestilo.com/admin, open devtools →
# Application → Cookies → copy the value of `pe_token`.
#
set -euo pipefail

API="${PE_API:-https://pilarestilo.com/api}"
TOKEN="${PE_TOKEN:?set PE_TOKEN to your admin JWT}"
CAPTION='{producto} a solo {precio}. Envios a todo Chile. #pilarestilo'

auth=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
say() { printf '\n=== %s ===\n' "$1"; }

say "2 newest products (from the catalog API)"
# The admin product list supports createdFrom/createdTo but not sort; pull a page and
# sort client-side by createdAt.
PRODUCTS_JSON=$(curl -s "${auth[@]}" "$API/products?size=100")
read -r P1_ID P1_NAME < <(python3 - "$PRODUCTS_JSON" <<'PY'
import json, sys
items = json.loads(sys.argv[1]).get("content", [])
items.sort(key=lambda p: p.get("createdAt",""), reverse=True)
p = items[0]; print(p["id"], p["name"])
PY
)
read -r P2_ID P2_NAME < <(python3 - "$PRODUCTS_JSON" <<'PY'
import json, sys
items = json.loads(sys.argv[1]).get("content", [])
items.sort(key=lambda p: p.get("createdAt",""), reverse=True)
p = items[1]; print(p["id"], p["name"])
PY
)
echo "newest      : $P1_NAME ($P1_ID)  -> publish now, IG + FB"
echo "2nd newest  : $P2_NAME ($P2_ID)  -> schedule +15 min, IG + FB"
read -r -p $'\nProceed and post for real? [y/N] ' ok
[ "$ok" = "y" ] || { echo "aborted"; exit 0; }

say "batch 1 — publish now: $P1_NAME"
curl -s "${auth[@]}" -X POST "$API/admin/publications/batch" -d "$(python3 - <<PY
import json
print(json.dumps({
  "productIds": ["$P1_ID"],
  "platforms": ["INSTAGRAM", "FACEBOOK"],
  "captionTemplate": "$CAPTION",
  "hashtags": ["#pilarestilo"],
  "campaignLabel": "Smoke H-2/H-3"
}))
PY
)" | python3 -m json.tool

WHEN=$(python3 -c "import datetime; print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(minutes=15)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
say "batch 2 — schedule for $WHEN: $P2_NAME"
curl -s "${auth[@]}" -X POST "$API/admin/publications/batch" -d "$(python3 - <<PY
import json
print(json.dumps({
  "productIds": ["$P2_ID"],
  "platforms": ["INSTAGRAM", "FACEBOOK"],
  "captionTemplate": "$CAPTION",
  "hashtags": ["#pilarestilo"],
  "campaignLabel": "Smoke H-2/H-3",
  "scheduledAt": "$WHEN"
}))
PY
)" | python3 -m json.tool

say "history now"
curl -s "${auth[@]}" "$API/admin/publications/batches" | python3 -m json.tool

cat <<EOF

Done. Check:
  - Instagram @pilar_estilo_cl and the Facebook page: the newest product should be live.
  - /admin/publicaciones -> Historial: batch 1 shows "2 publicados" (or the Meta error if
    something's off), batch 2 shows "Programada para <hora>".
  - In ~15 min the scheduled batch publishes itself. Re-check Historial: it flips to
    "2 publicados" and the job logs "Published or failed 2 due scheduled publications".
EOF
