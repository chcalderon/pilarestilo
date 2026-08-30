#!/usr/bin/env bash
# Load test prep — snapshot + inflate stock + silence email. Reversible by cleanup.sh.
# All changes are to the LOCAL pilarestilo DB. See DATA-MAP.md.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
PSQL=(docker exec -i pe_postgres psql -U pilar -d pilarestilo -v ON_ERROR_STOP=1)

echo "[prep] snapshot stock -> .stock-snapshot.sql"
"${PSQL[@]}" -tAc "
  SELECT format('UPDATE products SET stock=%s WHERE id=%L;', stock, id) FROM products
  UNION ALL
  SELECT format('UPDATE product_variants SET stock_on_hand=%s, stock_reserved=%s WHERE product_id=%L AND color IS NOT DISTINCT FROM %L AND size IS NOT DISTINCT FROM %L;',
                stock_on_hand, stock_reserved, product_id, color, size)
  FROM product_variants
" | tr -d '\r' | sed '/^[[:space:]]*$/d' > "$DIR/.stock-snapshot.sql"
echo "       $(grep -c ';' "$DIR/.stock-snapshot.sql") rows"

echo "[prep] snapshot notification_providers -> .provider-snapshot.txt"
"${PSQL[@]}" -tAc "SELECT notification_providers FROM system_settings ORDER BY id LIMIT 1;" | tr -d '\r' > "$DIR/.provider-snapshot.txt"
echo "       was: $(cat "$DIR/.provider-snapshot.txt")"

echo "[prep] inflate stock (100000, zero reservations)"
"${PSQL[@]}" -c "UPDATE products SET stock = 100000;"
"${PSQL[@]}" -c "UPDATE product_variants SET stock_on_hand = 100000, stock_reserved = 0;"

echo "[prep] notification_providers -> LOG"
"${PSQL[@]}" -c "UPDATE system_settings SET notification_providers = 'LOG';"

echo "[prep] flush redis (products/settings cache)"
docker exec pe_redis redis-cli FLUSHALL

echo "[prep] done"
