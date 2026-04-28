# Header Register Popover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the header "Registrarse" button with a portal-rendered inline popover (register/login tabs, no page navigation), with mobile bottom sheet fallback.

**Architecture:** Pure frontend change — no new API endpoints. `RegisterPopoverTrigger.tsx` manages open state; `RegisterPopoverPanel.tsx` is portal-rendered via `document.body`. On mobile (< 640px) the panel becomes a fixed bottom sheet. Reuses `register()` and `login()` from `lib/api.ts`. On success, calls `useAuthStore.getState().setAuth(...)` and closes the popover.

**Tech Stack:** React 18, Zustand, existing `lib/api.ts` auth helpers, Tailwind CSS, existing design tokens.

---

## File Map

| File | Action |
|---|---|
| `frontend/src/components/auth/RegisterPopoverTrigger.tsx` | Create — button + open state manager |
| `frontend/src/components/auth/RegisterPopoverPanel.tsx` | Create — portal-rendered panel with focus trap + outside-click close |
| `frontend/src/components/auth/RegisterPopoverForm.tsx` | Create — register/login tabs with inline form, compact mode |
| `frontend/src/components/Header.astro` or equivalent | Modify — replace existing "Registrarse" button/link with `<RegisterPopoverTrigger client:load />` |

Note: Locate the exact header file before starting. Run `find frontend/src -name "Header*"` to identify it.

---

### Task 1: Locate header file + audit existing auth API helpers

**Files:**
- Read: `frontend/src/` (find header component)
- Read: `frontend/src/lib/api.ts`
- Read: `frontend/src/lib/authStore.ts`

- [ ] **Step 1: Find the header component**

```bash
find frontend/src -name "Header*" -o -name "header*" | grep -v node_modules
```

Note the exact file path — you'll modify it in Task 4.

- [ ] **Step 2: Check existing auth API function signatures**

Open `frontend/src/lib/api.ts` and confirm:
- `registerUser(email, password)` — exists and returns `AuthTokenResponse`
- `loginUser(email, password)` — exists and returns `AuthTokenResponse`

Also open `frontend/src/lib/authStore.ts` and confirm the `setAuth()` method signature (may be `setUser()` or `setAuth()` — use the correct one throughout this plan).

- [ ] **Step 3: No code changes — just record facts for next tasks**

---

### Task 2: RegisterPopoverForm — inline register/login form

**Files:**
- Create: `frontend/src/components/auth/RegisterPopoverForm.tsx`

- [ ] **Step 1: Create the form component**

```tsx
// frontend/src/components/auth/RegisterPopoverForm.tsx
import { useState } from "react";
import { registerUser, loginUser } from "@/lib/api";
import { useAuthStore } from "@/lib/authStore";

type Tab = "register" | "login";

interface Props {
  onSuccess: () => void;
}

export function RegisterPopoverForm({ onSuccess }: Props) {
  const [tab, setTab] = useState<Tab>("register");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const setAuth = useAuthStore(s => s.setAuth);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const data = tab === "register"
        ? await registerUser(email, password)
        : await loginUser(email, password);
      setAuth(data);
      onSuccess();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Error al procesar";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <form onSubmit={handleSubmit} className="flex flex-col gap-3" noValidate>
        <div className="flex flex-col gap-1">
          <label className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)]">
            Email
          </label>
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            disabled={loading}
            className="border border-[var(--pe-border)] px-3 py-2 text-sm bg-[var(--pe-surface)] focus:outline-none focus:border-[var(--pe-foreground)] disabled:opacity-50"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)]">
            Contraseña
          </label>
          <div className="relative">
            <input
              type={showPassword ? "text" : "password"}
              required
              minLength={8}
              autoComplete={tab === "register" ? "new-password" : "current-password"}
              value={password}
              onChange={e => setPassword(e.target.value)}
              disabled={loading}
              className="w-full border border-[var(--pe-border)] px-3 py-2 pr-9 text-sm bg-[var(--pe-surface)] focus:outline-none focus:border-[var(--pe-foreground)] disabled:opacity-50"
            />
            <button
              type="button"
              onClick={() => setShowPassword(v => !v)}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--pe-muted)] hover:text-[var(--pe-foreground)]"
              aria-label={showPassword ? "Ocultar contraseña" : "Mostrar contraseña"}
            >
              {showPassword ? (
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
              ) : (
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              )}
            </button>
          </div>
        </div>

        {error && (
          <p role="alert" aria-live="polite" className="text-xs text-red-500">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={loading}
          className="bg-[#1A1A1A] text-[#F8F4EF] py-2 text-sm tracking-widest uppercase hover:bg-[#B76E79] disabled:opacity-50 transition-colors"
        >
          {loading ? (
            <span className="flex items-center justify-center gap-2">
              <svg className="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
              </svg>
              Procesando
            </span>
          ) : tab === "register" ? "Crear cuenta" : "Iniciar sesión"}
        </button>
      </form>

      <p className="text-xs text-center text-[var(--pe-muted)]">
        {tab === "register" ? (
          <>
            ¿Ya tienes cuenta?{" "}
            <button
              onClick={() => { setTab("login"); setError(null); }}
              className="underline hover:text-[var(--pe-foreground)]"
            >
              Inicia sesión
            </button>
          </>
        ) : (
          <>
            ¿No tienes cuenta?{" "}
            <button
              onClick={() => { setTab("register"); setError(null); }}
              className="underline hover:text-[var(--pe-foreground)]"
            >
              Regístrate
            </button>
          </>
        )}
      </p>
    </div>
  );
}
```

- [ ] **Step 2: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -30
```
Expected: 0 errors on this file.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/auth/RegisterPopoverForm.tsx
git commit -m "feat(popover): add RegisterPopoverForm inline register/login component"
```

---

### Task 3: RegisterPopoverPanel — portal container with positioning + focus trap

**Files:**
- Create: `frontend/src/components/auth/RegisterPopoverPanel.tsx`

- [ ] **Step 1: Create the panel component**

```tsx
// frontend/src/components/auth/RegisterPopoverPanel.tsx
import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { RegisterPopoverForm } from "./RegisterPopoverForm";

interface AnchorRect {
  bottom: number;
  right: number;
}

interface Props {
  anchor: AnchorRect;
  onClose: () => void;
}

export function RegisterPopoverPanel({ anchor, onClose }: Props) {
  const panelRef = useRef<HTMLDivElement>(null);
  const isMobile = typeof window !== "undefined" && window.innerWidth < 640;

  // Close on outside mousedown
  useEffect(() => {
    function handleMouseDown(e: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        onClose();
      }
    }
    document.addEventListener("mousedown", handleMouseDown);
    return () => document.removeEventListener("mousedown", handleMouseDown);
  }, [onClose]);

  // Close on Escape
  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [onClose]);

  // Focus first input on mount
  useEffect(() => {
    const first = panelRef.current?.querySelector<HTMLElement>("input, button");
    first?.focus();
  }, []);

  // Focus trap
  useEffect(() => {
    function trapFocus(e: KeyboardEvent) {
      if (e.key !== "Tab" || !panelRef.current) return;
      const focusable = Array.from(
        panelRef.current.querySelectorAll<HTMLElement>(
          'input, button, [href], select, textarea, [tabindex]:not([tabindex="-1"])'
        )
      ).filter(el => !el.hasAttribute("disabled"));
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
    document.addEventListener("keydown", trapFocus);
    return () => document.removeEventListener("keydown", trapFocus);
  }, []);

  const desktopStyle: React.CSSProperties = {
    position: "fixed",
    top: anchor.bottom + 8,
    right: window.innerWidth - anchor.right,
    width: 320,
    zIndex: 9999,
  };

  const mobileStyle: React.CSSProperties = {
    position: "fixed",
    bottom: 0,
    left: 0,
    right: 0,
    zIndex: 9999,
  };

  const panel = (
    <div
      ref={panelRef}
      role="dialog"
      aria-modal="true"
      aria-label={isMobile ? "Crear cuenta o iniciar sesión" : undefined}
      style={isMobile ? mobileStyle : desktopStyle}
      className={
        isMobile
          ? "bg-[var(--pe-surface)] border-t border-[var(--pe-border)] rounded-t-2xl p-6 shadow-xl"
          : "bg-[var(--pe-surface)] border border-[var(--pe-border)] p-5 shadow-[0_8px_24px_rgba(0,0,0,0.12)]"
      }
    >
      <RegisterPopoverForm onSuccess={onClose} />
    </div>
  );

  return createPortal(panel, document.body);
}
```

- [ ] **Step 2: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -30
```
Expected: 0 errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/auth/RegisterPopoverPanel.tsx
git commit -m "feat(popover): add RegisterPopoverPanel portal with focus trap and outside-click close"
```

---

### Task 4: RegisterPopoverTrigger + wire into Header

**Files:**
- Create: `frontend/src/components/auth/RegisterPopoverTrigger.tsx`
- Modify: header file (identified in Task 1)

- [ ] **Step 1: Create the trigger component**

```tsx
// frontend/src/components/auth/RegisterPopoverTrigger.tsx
import { useState, useRef, useCallback } from "react";
import { RegisterPopoverPanel } from "./RegisterPopoverPanel";

export function RegisterPopoverTrigger() {
  const [open, setOpen] = useState(false);
  const [anchor, setAnchor] = useState({ bottom: 0, right: 0 });
  const buttonRef = useRef<HTMLButtonElement>(null);

  const handleOpen = useCallback(() => {
    if (!buttonRef.current) return;
    const rect = buttonRef.current.getBoundingClientRect();
    setAnchor({ bottom: rect.bottom, right: rect.right });
    setOpen(true);
  }, []);

  return (
    <>
      <button
        ref={buttonRef}
        onClick={handleOpen}
        className="text-sm tracking-widest uppercase hover:text-[#B76E79] transition-colors"
      >
        Registrarse
      </button>

      {open && (
        <RegisterPopoverPanel
          anchor={anchor}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}
```

- [ ] **Step 2: Find and update the header file**

The header file was identified in Task 1. Open it. Find the existing "Registrarse" button or link (look for text "Registrarse" or the route `/register`).

Replace it with the island:

**If the header is `.astro`** — add the import and use the island:
```astro
---
import { RegisterPopoverTrigger } from "@/components/auth/RegisterPopoverTrigger";
// ... other imports
---
<!-- Replace the existing Registrarse element with: -->
<RegisterPopoverTrigger client:load />
```

**If the header is `.tsx`** — import and render directly:
```tsx
import { RegisterPopoverTrigger } from "@/components/auth/RegisterPopoverTrigger";
// Replace the existing button/link with:
<RegisterPopoverTrigger />
```

Remove any existing `<a href="/register">Registrarse</a>` or similar element.

- [ ] **Step 3: Verify TypeScript**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -30
```
Expected: 0 errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/auth/RegisterPopoverTrigger.tsx
git add frontend/src/  # (the modified header file)
git commit -m "feat(popover): add RegisterPopoverTrigger and wire into site header"
```

---

### Task 5: Manual smoke test + build verification

- [ ] **Step 1: Start the dev server**

```bash
cd frontend && npm run dev
```

- [ ] **Step 2: Smoke test (manual)**

Open the site in a browser. Verify:

1. "Registrarse" button visible in header when not logged in.
2. Clicking opens the popover (desktop: anchored below button; mobile: bottom sheet).
3. Typing email + password and submitting "Crear cuenta" registers successfully — header updates to show user menu.
4. Toggle to login tab works; existing credentials log in successfully.
5. Clicking outside the popover closes it.
6. Pressing `Escape` closes it.
7. Tab key cycles within popover fields only (does not reach page content behind).
8. After success: popover closes, no page navigation.
9. Error state: wrong credentials shows inline error, fields re-enabled.

- [ ] **Step 3: Frontend build**

```bash
cd frontend && npm run build
```
Expected: 0 errors, 0 warnings about the new components.

- [ ] **Step 4: Run all backend tests (regression check)**

```
./mvnw test -pl backend -q
```
Expected: all tests PASS — this plan has no backend changes.

- [ ] **Step 5: Commit any fixes**

```bash
git add -p
git commit -m "fix(popover): address smoke test and build issues"
```
