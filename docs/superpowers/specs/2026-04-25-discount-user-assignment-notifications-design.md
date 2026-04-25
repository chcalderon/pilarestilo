# Discount User Assignment + In-App Notifications Design

**Date:** 2026-04-25
**Status:** Approved

---

## Overview

Two enhancements:
1. Discount codes can be assigned to a specific user or left open for all users (first-come-first-serve).
2. In-app notification system: persistent history in DB, bell icon always visible, history section in account page.

---

## 1. Database

**Migration: `V31__discount_user_assignment_and_notifications.sql`**

```sql
-- User assignment on discount codes
ALTER TABLE discounts ADD COLUMN assigned_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_discounts_assigned_user ON discounts(assigned_user_id);

-- In-app notification storage
CREATE TABLE notifications (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type         VARCHAR(50) NOT NULL,
  title        VARCHAR(200) NOT NULL,
  body         TEXT NOT NULL,
  metadata     JSONB,
  read_at      TIMESTAMP,
  created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE read_at IS NULL;
```

**Business rules:**
- `assigned_user_id = NULL` → code available to all users, consumed first-come-first-serve against `max_uses`
- `assigned_user_id = <uuid>` → only that user can apply it; existing UNIQUE constraint on `discount_code_usages(discount_id, user_id)` prevents double use
- `read_at = NULL` → unread; counts toward bell badge

**Notification types:**
```
DISCOUNT_CODE_ASSIGNED
ORDER_CONFIRMED
PAYMENT_RECEIVED
ORDER_SHIPPED
```
Only notifications created from this point forward are stored. Existing historical messages are not backfilled.

---

## 2. Backend (Java)

### New domain pieces

**`NotificationType` enum**
Values: `DISCOUNT_CODE_ASSIGNED`, `ORDER_CONFIRMED`, `PAYMENT_RECEIVED`, `ORDER_SHIPPED`

**`InAppNotification` domain model**
Fields: `id (UUID)`, `userId (UUID)`, `type (NotificationType)`, `title (String)`, `body (String)`, `metadata (Map<String,Object>)`, `readAt (Instant)`, `createdAt (Instant)`

**`NotificationRepository` port**
```java
void save(InAppNotification notification);
Page<InAppNotification> findByUserId(UUID userId, Pageable pageable);
long countUnreadByUserId(UUID userId);
void markAsRead(UUID notificationId, UUID userId);
void markAllAsRead(UUID userId);
```

**`InAppNotificationSender`**
Implements a new port `InAppNotificationPort`. Writes to `notifications` table. Runs in addition to (not instead of) the existing `SystemSettingsNotificationSender`. Both are triggered from event listeners.

### Discount changes

- `DiscountEntity` / `Discount` domain model: add `assignedUserId (UUID, nullable)`
- `CreateDiscountUseCase`: if `assignedUserId` present → after persisting, publish `DiscountCodeAssignedEvent(discountId, assignedUserId, code)`
- `ValidateDiscountForUserUseCase`: add check — if `discount.assignedUserId != null && !discount.assignedUserId.equals(currentUserId)` → throw `DiscountNotValidForUserException` (HTTP 422, message: "Este código no está disponible para tu cuenta")
- `DiscountCodeAssignedEventListener`: on `DiscountCodeAssignedEvent` → call `InAppNotificationSender` (write to DB) + call `SystemSettingsNotificationSender` (WhatsApp/email via existing channel logic)

### User search endpoint

```
GET /api/users/search?q={query}
Authorization: ADMIN or SELLER
Response: [{ id, name, email }]  (max 10 results, searches name + email)
```

### Notification endpoints

```
GET  /api/notifications?page=0&size=20   → paginated history, authenticated user
GET  /api/notifications/unread-count     → { count: N }
PUT  /api/notifications/{id}/read        → mark single as read
PUT  /api/notifications/read-all         → mark all as read for user
```

---

## 3. Frontend

### `NavNotificationBell.tsx` — refactor

- Always rendered regardless of notification count
- On mount: fetch `GET /api/notifications/unread-count`; poll every 60 seconds
- Badge shows unread count; hidden (not component) when count = 0 — bell icon still visible
- Dropdown on click:
  - Shows last 5 notifications (title, body truncated, relative time)
  - Unread items visually highlighted
  - Click on notification item → `PUT /api/notifications/{id}/read` + navigate if metadata contains `link`
  - "Ver historial" link at bottom → `/{locale}/account#notifications` (use current locale from Astro context)
  - Existing channel config alerts (configure WhatsApp/email preference, add phone) remain at bottom, separated by divider

### `DiscountCodeManager.tsx` — extensions

- New optional field in create form: "Asignar a usuario"
  - Autocomplete input: on type (debounced 300ms) → `GET /api/users/search?q=X`
  - Shows result list with name + email
  - On select: shows chip with name + email, removable
  - If no user selected: code created as general (all users)
- Code list table: new column "Disponible para" — shows user name+email chip or badge "Todos". The list API response must include `assignedUserName` and `assignedUserEmail` (resolved from `assigned_user_id` in the backend) — not just the UUID.
- No change to delete or validation flows

### New island: `NotificationHistory.tsx`

Rendered inside the account page (`/es/account`) in a new section anchored at `#notifications`.

- Paginated list (20 per page) of all notifications for the authenticated user
- Each row: type badge (color-coded), title, body, relative timestamp, unread indicator
  - DISCOUNT_CODE_ASSIGNED → green
  - ORDER_CONFIRMED → blue
  - PAYMENT_RECEIVED → purple
  - ORDER_SHIPPED → orange
- "Marcar todas como leídas" button (disabled if all read)
- Empty state: "No tenés notificaciones aún"
- Pagination controls

### `how-we-sell.astro` — new section

Add a "Códigos de descuento" section (bilingual ES/EN) covering:
- How to apply: enter code in cart before checkout
- Codes are per-user — each code can only be used once per account
- Codes are not transferable between accounts
- Codes do not accumulate across accounts
- If a code was assigned specifically to you, only your account can use it
- General codes are available to any user until the available uses are exhausted

---

## 4. Error & Edge Cases

| Scenario | Behavior |
|---|---|
| User tries assigned code belonging to another user | HTTP 422: "Este código no está disponible para tu cuenta" |
| General code with `max_uses` exhausted | HTTP 422: "Este código ya no tiene usos disponibles" |
| User already used the code | HTTP 422: existing "Ya utilizaste este código" message (unchanged) |
| Admin assigns code to non-existent user ID | HTTP 400 from `CreateDiscountUseCase` validation |
| Notification delivery fails (WhatsApp/email) | In-app notification still written; external failure logged, not propagated to user |

---

## 5. Out of Scope

- Backfilling historical notifications (order confirmations, payments sent before this feature)
- Multi-user assignment (code assigned to a list of users)
- Notification preferences per type (e.g., "don't notify me for discount codes")
- Real-time push (WebSocket/SSE) — polling at 60s interval is sufficient
