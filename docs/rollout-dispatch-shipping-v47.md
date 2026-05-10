# Rollout Guide - Dispatch Shipping Snapshot and Carrier Override Audit (V47)

Date: 2026-05-10  
Scope: checkout shipping persistence + dispatch queue consistency across all payment methods.

## Migrations

1. Apply `V46__order_shipping_selection.sql`.
2. Apply `V47__dispatch_shipping_snapshot_and_override_audit.sql`.

`V47` is backward compatible and includes:

- New `dispatches` columns for order shipping snapshot:
  - `order_shipping_zone_code`
  - `order_shipping_courier_id`
  - `order_shipping_courier_name`
  - `order_shipping_address_reference`
- New structured carrier override audit columns:
  - `carrier_override_configured`
  - `carrier_override_selected`
  - `carrier_override_by`
  - `carrier_override_at`
- Backfill from `orders` for existing dispatch rows.

## Runtime behavior after rollout

- When order status changes to `PAID`, dispatch creation snapshots shipping data from the order.
- Dispatch queue (`GET /api/despachos`) reads snapshot fields directly from `dispatches` (no per-row order lookup).
- Marking a dispatch (`POST /api/despachos/{id}/dispatch`) stores override audit in structured columns if selected carrier differs from configured courier.
- History endpoints can render override without parsing `notes`.

## Validation checklist

1. Create an order with payment method A, choose shipping zone/courier, complete payment to `PAID`.
2. Repeat with payment method B and C.
3. For each order, verify dispatch queue row contains:
   - `orderShippingZoneCode`
   - `orderShippingCourierId`
   - `orderShippingCourierName`
4. Dispatch with same carrier as configured:
   - `carrierOverride*` fields remain null.
5. Dispatch with different carrier:
   - `carrierOverrideConfigured` and `carrierOverrideSelected` are populated.
   - `carrierOverrideBy` is the authenticated dispatcher id.
   - `carrierOverrideAt` is populated.
6. Confirm account order details still show selected shipping fields.

## Rollback note

Code rollback should be paired with DB compatibility awareness: `V47` adds nullable columns only, so no destructive rollback is required for application continuity.
