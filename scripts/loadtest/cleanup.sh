#!/usr/bin/env bash
# Load test cleanup — restore stock/provider, delete simulated data, verify. See DATA-MAP.md.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
PSQL=(docker exec -i pe_postgres psql -U pilar -d pilarestilo -v ON_ERROR_STOP=1)
PSQL_N=(docker exec -i pe_postgres psql -U pilar -d pilarestilo_notifications -v ON_ERROR_STOP=1)

RUN_START="$(cat "$DIR/.run-start.txt" 2>/dev/null || echo '2000-01-01T00:00:00')"

echo "[cleanup] delete simulated purchase data (FK order)"
"${PSQL[@]}" <<'SQL'
BEGIN;
CREATE TEMP TABLE lt_users AS
  SELECT id FROM users
  WHERE email LIKE 'load\_%@loadtest.local'
     OR email IN ('loadadmin1@loadtest.local', 'loadadmin2@loadtest.local');
CREATE TEMP TABLE lt_orders AS SELECT id FROM orders WHERE customer_id IN (SELECT id FROM lt_users);
DELETE FROM dispatches         WHERE order_id IN (SELECT id FROM lt_orders);
DELETE FROM sales_documents    WHERE order_id IN (SELECT id FROM lt_orders);
DELETE FROM payments           WHERE order_id IN (SELECT id FROM lt_orders);
DELETE FROM order_items        WHERE order_id IN (SELECT id FROM lt_orders);
DELETE FROM inventory_movements WHERE reference_id IN (SELECT id FROM lt_orders);
DELETE FROM orders             WHERE id IN (SELECT id FROM lt_orders);
DELETE FROM customer_addresses WHERE customer_id IN (SELECT id FROM lt_users);
DELETE FROM data_consents      WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM data_deletion_requests WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM users              WHERE id IN (SELECT id FROM lt_users);
COMMIT;
SQL

echo "[cleanup] delete notifications created since $RUN_START"
"${PSQL_N[@]}" -c "DELETE FROM notifications WHERE created_at >= '${RUN_START}'::timestamptz;"

echo "[cleanup] restore stock from snapshot"
if [ -s "$DIR/.stock-snapshot.sql" ]; then
  cat "$DIR/.stock-snapshot.sql" | "${PSQL[@]}" -q
  echo "       restored"
else
  echo "       WARN: no .stock-snapshot.sql — stock left at 100000"
fi

echo "[cleanup] restore notification_providers"
PROV="$(cat "$DIR/.provider-snapshot.txt" 2>/dev/null || echo 'EMAIL_SMTP')"
"${PSQL[@]}" -c "UPDATE system_settings SET notification_providers = '${PROV}';"
echo "       -> $PROV"

echo "[cleanup] flush redis"
docker exec pe_redis redis-cli FLUSHALL

echo "[cleanup] verify"
"${PSQL[@]}" -tAc "
  SELECT 'products' t, count(*) n, coalesce(sum(stock),0) s FROM products
  UNION ALL SELECT 'product_variants', count(*), coalesce(sum(stock_on_hand),0) FROM product_variants
  UNION ALL SELECT 'users', count(*), 0 FROM users
  UNION ALL SELECT 'orders', count(*), 0 FROM orders
  UNION ALL SELECT 'payments', count(*), 0 FROM payments
  UNION ALL SELECT 'dispatches', count(*), 0 FROM dispatches
  UNION ALL SELECT 'sales_documents', count(*), 0 FROM sales_documents
  UNION ALL SELECT 'customer_addresses', count(*), 0 FROM customer_addresses
  UNION ALL SELECT 'lt_users_left', count(*), 0 FROM users WHERE email LIKE '%@loadtest.local';
"
echo "[cleanup] done"
