# Pilar Estilo - n8n Integration Playbook

This guide describes how to connect Pilar Estilo with an external n8n instance for:

- Transactional notifications (orders/payments/preparation/shipping)
- FAQ-style assistant flows for WhatsApp and social inbox channels

---

## 1. Backend provider setup

Set notification provider to `N8N_WEBHOOK` from admin (`/admin/settings`) or env fallback:

```env
NOTIFICATION_PROVIDER=N8N_WEBHOOK
NOTIFICATION_N8N_WEBHOOK_URL=https://your-n8n.example/webhook/pilar-notifications
NOTIFICATION_N8N_API_KEY=replace-with-secret
NOTIFICATION_N8N_TOKEN_HEADER_NAME=X-PE-N8N-TOKEN
```

Recommended:

1. Configure provider = `N8N_WEBHOOK` in admin.
2. Fill n8n fields in admin notification settings:
   - Webhook URL
   - Token header name
   - API key/token
3. Keep `.env` values as fallback for emergency override.

---

## 2. Webhook payload contract (`N8N_WEBHOOK`)

Current payload shape sent by backend:

```json
{
  "eventType": "ORDER_CONFIRMATION",
  "referenceId": "uuid",
  "occurredAt": "2026-04-23T08:15:30Z",
  "recipient": {
    "phone": "+56912345678",
    "email": "cliente@correo.cl",
    "channelPreference": "AUTO",
    "allowWhatsApp": true,
    "allowEmail": true
  }
}
```

`eventType` values currently emitted:

- `ORDER_CONFIRMATION`
- `PAYMENT_RECEIVED`
- `ORDER_PREPARING`
- `ORDER_SHIPPED`

`channelPreference` values:

- `AUTO`
- `WHATSAPP`
- `EMAIL`
- `BOTH`

---

## 3. Recommended n8n notification workflow

1. `Webhook` trigger (path: `pilar-notifications`).
2. `IF` node validates token header (`X-PE-N8N-TOKEN`).
3. `Switch` by `eventType`.
4. Channel routing:
   - If `recipient.allowWhatsApp = true` and phone exists -> WhatsApp node/provider.
   - If `recipient.allowEmail = true` and email exists -> SMTP/SendGrid node.
5. Audit log:
   - Save result to your n8n DB table or external log sink.

This keeps channel orchestration outside backend while preserving user preference.

---

## 4. FAQ assistant flow (WhatsApp + social)

For "is this product available?" and "where do you ship?" questions:

1. Channel trigger (WhatsApp, Instagram, Facebook inbox, etc).
2. Intent classifier node:
   - `availability`
   - `shipping`
   - `human_handoff`
3. Backend API lookups:
   - Product search: `GET /api/products/search?q=<keyword>`
   - Public store config/contact: `GET /api/system-settings/public`
4. Compose answer template and send back to channel.
5. Optional: if low confidence or user asks for advisor -> route to human handoff.

---

## 5. Security notes

- Always protect n8n webhook with secret header check.
- Keep `NOTIFICATION_N8N_API_KEY` out of git.
- Use HTTPS for n8n webhook endpoint.
- Rate-limit public assistant entry points in channel provider or gateway.
