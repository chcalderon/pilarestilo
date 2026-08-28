# Self-Service Password Reset — Design Spec

**Date:** 2026-08-28
**Status:** approved by user, pending implementation plan

## Context and motivation

No self-service "forgot password" flow exists anywhere in the storefront or admin panel today.
The only reset mechanism is `UserEditDrawer` in `/admin`, where an authenticated admin sets a new
password directly for a worker. A customer (or a staff member) who forgets their password has no
way to recover their account alone.

This extends the existing `shared/auth` hexagonal module rather than introducing a new one — the
password hashing (`PasswordEncoder`), the user lookup (`UserRepository`), and the auth endpoint
conventions (`AuthController`, `SecurityConfig`) all already exist and are reused as-is.

## Decisions from brainstorming

1. **Scope**: applies to every role (customers and staff), not just the storefront. Staff keeps
   their existing admin-forced reset in `UserEditDrawer` as a second path (see below) — this adds a
   self-service option, it does not replace anything.
2. **Admin-forced reset in `UserEditDrawer`**: kept as-is, coexists with self-service. Useful when a
   worker has no access to their email at that moment, or for new hires.
3. **Delivery channel**: the reset email always goes out via email specifically, bypassing
   `system_settings.notification_providers` entirely. A password reset has no meaningful WhatsApp or
   webhook equivalent — email is the login identifier — and an admin's channel toggle must never be
   able to lock someone out of recovering their own account.
4. **Session invalidation**: resetting a password invalidates every existing session (JWT/refresh)
   for that user, on every device. Found during design: access tokens live 24h and refresh tokens
   7 days, with no existing revocation mechanism (`JwtTokenProvider`/`JwtAuthenticationFilter`) —
   without this, "I think someone else has my password" resets would leave an attacker's existing
   session alive for up to a week. The same invalidation applies to the admin-forced reset in
   `UserEditDrawer`, for the same reason.

## 1. Architecture and components

**Backend — new, inside `shared/auth`:**

- **Migration `V91__password_reset_tokens.sql`**: table `password_reset_tokens` (`id`, `user_id`
  FK, `token_hash`, `expires_at`, `used_at` nullable, `created_at`). Only the hash is stored, never
  the raw token — the same principle as a password: a leaked table is useless without it.
- **Migration adds `users.session_version`** (integer, default 1, not null).
- **`RequestPasswordResetUseCase`**: given an email, looks up the user; if found, invalidates any
  prior unused token for that user, generates a 256-bit random token (`SecureRandom`), stores its
  hash with a 30-minute expiry, and calls `PasswordResetNotifier`. Returns the same success response
  whether or not the email exists — no enumeration signal, ever.
- **`ResetPasswordUseCase`**: given a token and a new password, looks up by the token's hash,
  validates it exists, is unused, and is not expired (one generic error for all three failure
  cases: "the link is invalid or has expired"), then updates `password_hash` via the existing
  `PasswordEncoder`, marks the token used, and increments `session_version`.
- **`PasswordResetNotifier`**: a dedicated adapter, bypassing `SystemSettingsNotificationSender` and
  the `notification_providers` toggle entirely — sends directly through whichever email sender is
  configured (SMTP or SendGrid).
- **`AuthController`**: two new endpoints, `POST /api/auth/forgot-password` and
  `POST /api/auth/reset-password`, both public.
- **`SecurityConfig`**: both new endpoints added to `permitAll()`, same pattern as
  `/api/auth/login`/`/register`/`/refresh`/`/google`. No other method or path near them becomes
  public.
- **`ApiGatewayRateLimitFilter`**: a new policy for `POST /api/auth/forgot-password`, same
  mechanism already limiting login/register — a conservative default (5 requests per IP per 60s),
  since this endpoint is the most attractive target for spamming a victim's inbox.
- **`JwtTokenProvider`**: JWTs gain a `sv` claim carrying the user's `session_version` at issuance.
- **`JwtAuthenticationFilter`**: compares `token.sv` against the user's current `session_version` on
  every authenticated request — a mismatch is rejected the same way an expired token is (401).
- **`UserEditDrawer`'s existing admin-forced reset** (backend side): also increments
  `session_version` when it sets a new password directly.

**Frontend — new:**

- `/{locale}/forgot-password`: a full page (same split-editorial pattern as the existing
  unauthenticated `/es/account` login screen), one email field.
- `/{locale}/reset-password?token=...`: new-password + confirmation fields, reads the token from
  the query string.
- A "¿Olvidaste tu contraseña?" link added to `RegisterPopoverForm.tsx`'s login tab and to
  `AdminLoginForm.tsx` (staff reaches the same flow, per the all-roles decision).

## 2. Data flow

**Requesting a reset:**
```
Frontend (/forgot-password) -> POST /api/auth/forgot-password { email }
  -> RequestPasswordResetUseCase
    -> looks up user by email
    -> if found: invalidates any prior unused token, generates a token,
       stores its hash with a 30-minute expiry
    -> PasswordResetNotifier sends the email with the reset link (always,
       regardless of the notification_providers toggle)
  -> responds 200 identically either way ("if that email exists, check your inbox")
```

**Completing a reset:**
```
Customer clicks the email link -> /reset-password?token=xxxx
Frontend -> POST /api/auth/reset-password { token, newPassword }
  -> ResetPasswordUseCase
    -> looks up the token by its hash
    -> validates: exists, unused, not expired
    -> any failure -> generic error "the link is invalid or has expired"
    -> success -> updates password_hash, marks the token used,
       increments session_version
  -> responds 200 -> frontend redirects to login
```

**Effect of `session_version` on every later request:**
```
Any authenticated request -> JwtAuthenticationFilter
  -> decodes JWT, reads claim `sv`
  -> compares against the current user.session_version
  -> mismatch (a token issued before the reset) -> 401, same as an expired token
```

## 3. Security and error handling

- **Token**: 256 bits of randomness (`SecureRandom`); only its hash (SHA-256 is sufficient — this
  is a single-use, 30-minute-lived secret, not a password someone reuses) is persisted.
- **Single use**: `used_at` is set on the first successful reset; a second attempt with the same
  token fails even if it has not expired yet.
- **Requesting a new token invalidates the previous one** for that user — no more than one live
  link at a time.
- **Generic errors always**: "the link is invalid or has expired" covers a missing, used, and
  expired token identically — no external signal distinguishes which one happened.
- **Rate limiting**: covered in section 1 (5 requests / 60s / IP on `forgot-password`).
- **Session invalidation**: covered in the brainstorming decisions above and the data-flow section
  — this is the feature's main security addition beyond the reset mechanism itself.
- **Expired-token cleanup**: not urgent (a tiny table), but a simple periodic delete of long-expired
  rows is cheap to add — left as an implementation-plan detail, not a design fork.

## 4. Testing

- **`RequestPasswordResetUseCase`**: existing email -> token created, hash stored, notifier called;
  non-existent email -> identical public response, nothing created or sent; requesting twice in a
  row -> the first token becomes invalid.
- **`ResetPasswordUseCase`**: valid token -> password changes, `session_version` increments, token
  marked `used_at`; used, expired, or unknown token -> the same generic error in all three cases,
  password untouched.
- **`JwtAuthenticationFilter`**: a token carrying a stale `sv` -> 401, alongside the existing
  expired-token case.
- **Rate limiting**: no test exists today for `ApiGatewayRateLimitFilter` (a pre-existing gap, noted
  but not retroactively fixed here) — the new `forgot-password` policy gets one, the first of its
  kind for this filter.
- **Integration (Testcontainers)**: full flow against a real Postgres — request a reset, read the
  token directly from the repository (no need to simulate email delivery for this), complete the
  reset, confirm a login using the old `sv` fails and a fresh one succeeds.
- **Pre-deploy real verification**: an actual reset request against the full local stack, using the
  fixed test customer, confirming the email arrives and the link works end to end — the same bar
  already required for anything touching email in this project.
- **`SecurityConfig`**: only `POST` on the two new endpoints is public; no other method or nearby
  path becomes public as a side effect.

## Open items for the implementation plan

- Exact copy/wording for the reset email (Spanish + English, matching `NotificationComposer`'s
  existing tone).
- Whether the expired-token cleanup runs as a scheduled job or lazily on each request — an
  implementation choice, not a design one.
- Exact `forgot-password` rate-limit numbers (5/60s proposed here) — confirm against real traffic
  expectations if they turn out too tight or too loose in practice.
