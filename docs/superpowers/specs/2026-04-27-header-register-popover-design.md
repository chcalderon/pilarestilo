# Header Register Popover — Design Spec

**Date:** 2026-04-27
**Status:** Approved

---

## Overview

Replace the current header "Registrarse" button with a micro-interaction that slides in a compact inline popover — a small form with email + password fields and a toggle to switch to login, all without leaving the page. On mobile the popover becomes a bottom sheet. The goal is to lower friction for new customer registration.

---

## Component: RegisterPopover

**Trigger:** The "Registrarse" button in the site header (visible to non-authenticated users).

**Rendering:** Portal-rendered (same pattern as `NavNotificationBell` dropdown) — appended to `document.body` so it layers correctly over all content.

**Desktop position:** Anchored below and right-aligned to the trigger button. Max width 320px.

**Mobile position:** Fixed bottom sheet, full width, slides up from bottom of screen.

---

## States

### 1. Closed (default)
Header shows "Registrarse" button. No popover.

### 2. Open — Register form
```
[ Email                    ]
[ Contraseña               ]
[ Crear cuenta ]   ← primary CTA

¿Ya tienes cuenta? Inicia sesión
```

Fields:
- `email`: type=email, required
- `password`: type=password, required, min 8 chars, show/hide toggle (Eye icon)
- Submit button: "Crear cuenta"
- Toggle link: "¿Ya tienes cuenta? Inicia sesión" → switches to login form

### 3. Open — Login form (toggled)
```
[ Email                    ]
[ Contraseña               ]
[ Iniciar sesión ]

¿No tienes cuenta? Regístrate
[ ─────── o ─────── ]
[ G  Continuar con Google ]
```

Same fields. Toggle link back to register. Google SSO button (identical to full login page).

### 4. Submitting
Button disabled, spinner inside button. Fields non-interactive.

### 5. Error
Inline error message below the form (red, small). Fields re-enabled.

### 6. Success — registered
Popover closes. Auth store updated (same as full login flow — JWT persisted to `pe-auth`). Page re-renders header to show user menu.

### 7. Success — logged in
Same as above.

---

## Behavior

- **Outside click** closes the popover (same pattern as notification bell — `mousedown` listener on document).
- **Escape key** closes the popover.
- **Focus trap**: while open, Tab cycles through form fields only.
- Popover **does not scroll with page** (fixed/portal position).
- After success, popover fades out (150ms), no navigation — user stays on current page.

---

## Implementation Notes

### Anchor positioning

On open, calculate trigger button `getBoundingClientRect()`. Set popover position:
- Desktop: `top = triggerBottom + 8px`, `right = windowWidth - triggerRight`
- Mobile (< 640px): ignore anchor, use bottom sheet position

### Reuse existing auth API

`RegisterPopover` calls the same `register()` and `login()` functions from `lib/api.ts` already used by `RegisterForm` and `LoginForm` full pages. No new API layer needed.

### Google SSO in popover

On login tab, render `GoogleSignInButton` component (same as full login page). The GSI popup flow works regardless of trigger location.

### Zustand store integration

On success, call `useAuthStore.getState().setUser(...)` with the returned token payload. This triggers reactivity across all components observing auth state (header, cart, etc.).

---

## Component Structure

```
Header.astro / Header.tsx
  └── RegisterPopoverTrigger.tsx     ← button that manages open state
        └── RegisterPopoverPortal.tsx ← portal renders into body
              ├── RegisterPopoverPanel.tsx  ← positioned container + close-on-outside
              │     ├── RegisterForm (inline variant, no page chrome)
              │     └── LoginForm (inline variant, no page chrome)
              └── (mobile: BottomSheetWrapper wraps RegisterPopoverPanel)
```

Inline variants of `RegisterForm` and `LoginForm` receive a prop `compact={true}` that hides page-level titles and adjusts padding. They share the same submit logic.

---

## CSS / Theme

Follows existing design tokens:
- Background: `--pe-surface` (white / dark-mode surface)
- Border: `1px solid var(--pe-border)` with `box-shadow: 0 8px 24px rgba(0,0,0,0.12)`
- Border radius: 0 (brand uses sharp corners)
- Typography: `text-sm`, labels `text-[10px] tracking-widest uppercase`
- Primary button: `bg-[#1A1A1A] text-[#F8F4EF] hover:bg-[#B76E79]`
- Mobile bottom sheet: `rounded-t-2xl` (exception to sharp corners rule — standard UX for sheets)

---

## Accessibility

- `role="dialog"` on popover panel, `aria-modal="true"`
- `aria-labelledby` pointing to form title
- Focus moves to first input on open
- `aria-live="polite"` on error region

---

## Flyway

No DB changes.

---

## Out of Scope (Future)

- "Forgot password" flow inside popover (link to full reset page)
- Social login options beyond Google
- Popover pre-fill from URL params
