# User Management Edit Drawer — Spec

**Date:** 2026-05-02
**Scope:** Admin `/admin/usuarios` — fix Rol button, remove from clientes tab, redesign edit UX with slide-out drawer

---

## 1. Problem Statement

Current `UserManagement.tsx` has four issues:

1. `WorkerAssignmentModal` is imported but **never rendered** — "Rol" button sets state but no modal appears, resulting in a broken/silent no-op.
2. "Rol" button exists in the **clientes** tab — clients have no worker-role concept, button is meaningless there.
3. "Editar" button only edits `fullName` — password reset, status, credit, and worker-role assignment each require separate buttons, leading to 6–7 buttons per row.
4. Inconsistent visual style: some buttons have icons, some don't; column widths are hard-coded; overall feels dense and cluttered.

---

## 2. Goals

- Fix WorkerAssignmentModal (render it correctly, or fold its logic into the drawer).
- Remove "Rol" button entirely from clientes tab.
- Consolidate all user editing into a single **slide-out drawer** (right side).
- Reduce table row to 3–4 compact actions max.
- Luxury boutique aesthetic: soft borders, subtle radius, elegant typography, dark/light mode.

---

## 3. Architecture

### New component: `UserEditDrawer.tsx`

**Location:** `frontend/src/islands/admin/UserEditDrawer.tsx`

A right-side slide panel triggered by clicking "Editar" on any user row. Contains all editable fields and actions for the selected user. Replaces the need for:
- `ActionModal` sub-cases: `editName`, `resetPassword`, `grantCredit`
- Standalone `WorkerAssignmentModal` usage

`WorkerAssignmentModal` has its own `fixed inset-0` backdrop and cannot be safely nested inside the drawer. Its role-assignment logic (API calls + field structure) is **inlined directly** into the drawer's "Rol laboral" section. `WorkerAssignmentModal.tsx` is left in place but no longer used (deprecated — can be deleted in a follow-up).

### Modified component: `UserManagement.tsx`

- Import and render `UserEditDrawer` when `editingUser` state is non-null.
- Add `editingUser` state: `UserDto | null`.
- Remove "Rol" button from clientes column definition.
- Simplify row action buttons for both tabs.
- Remove `WorkerAssignmentModal` import (role logic inlined in drawer).

---

## 4. UserEditDrawer — Sections

### Header
- Circular avatar with user initials (2 chars, `bg-pe-black text-pe-cream` light / `bg-pe-cream text-pe-black` dark)
- Full name + email (readonly)
- Status badge: `Activo` (green dot) or `Bloqueado` (red dot)
- Created-at date in small muted text
- Close button (X icon, top-right)

### Section: Información básica
- `fullName` text input, save on blur or explicit save button
- Email shown as readonly field (no edit — requires separate auth flow)

### Section: Estado
- Toggle switch: Activo / Bloqueado
- Descriptive label: "Bloquear impide el acceso a la cuenta"
- Disabled if editing own account

### Section: Contraseña
- Collapsible/expandable area (chevron toggle)
- Fields: nueva contraseña + confirmar (show/hide toggle)
- Inline validation: min 8 chars, must match
- Submit button inside section

### Section: Crédito *(clientes only)*
- Current balance displayed: `$ X CLP`
- Form: amount input + reason textarea (default "Crédito administrativo")
- Submit: "Otorgar crédito"

### Section: Rol laboral *(trabajadores only)*
- Role selector: SUPERVISOR | ADMINISTRACION | DESPACHADOR | SELLER
- Vigency start date (required)
- Vigency end date (optional)
- Actions: "Asignar rol" + "Revocar" (if role currently assigned)
- Uses existing `/api/admin/workers/{userId}/assign` and `/api/admin/workers/{userId}/revoke`

### Footer — Zona de riesgo
- Divider line
- "Pasar a trabajador" / "Pasar a cliente" secondary button
- "Eliminar usuario" danger button (with confirmation inline, not a separate modal)
- Both disabled if editing own account

---

## 5. Table Row Actions (Simplified)

### Clientes tab

| Button | Icon | Text | Style |
|---|---|---|---|
| Editar | `Pencil` | "Editar" | Primary, icon + text |
| Dar crédito | `Wallet` | "Crédito" | Secondary, icon + text |
| Bloquear / Habilitar | `ShieldOff` / `ShieldCheck` | — | Icon-only, `title` tooltip |
| Eliminar | `Trash2` | — | Icon-only danger, `title` tooltip |

**Removed from clientes:** "Rol", "Pasar a trabajador" (moved to drawer footer), "Reset pass" (moved to drawer).

### Trabajadores tab

| Button | Icon | Text | Style |
|---|---|---|---|
| Editar | `Pencil` | "Editar" | Primary, icon + text |
| Bloquear / Habilitar | `ShieldOff` / `ShieldCheck` | — | Icon-only, `title` tooltip |
| Eliminar | `Trash2` | — | Icon-only danger, `title` tooltip |

**Removed from trabajadores:** "Rol" (moved inside drawer), "Reset pass" (moved to drawer), "Pasar a cliente" (moved to drawer footer).

---

## 6. Visual Design

### Drawer container
- Width: `w-full max-w-[480px]`
- Position: `fixed inset-y-0 right-0 z-50`
- Transition: `translate-x-0` / `translate-x-full` with `transition-transform duration-300 ease-in-out`
- Background: `bg-[#FDFAF7]` light / `bg-[#141414]` dark
- Shadow: `shadow-2xl` (left-side shadow only, no hard border on the panel edge)
- Backdrop: `fixed inset-0 bg-black/30 backdrop-blur-[2px]`

### Section cards
- Background: `bg-white/60 dark:bg-white/5`
- Border: `border border-pe-black/8 dark:border-white/8`
- Radius: `rounded-md` (6px)
- Padding: `p-4`
- Section header: `text-[10px] tracking-widest uppercase text-pe-charcoal/50`

### Inputs
- Border: `border border-pe-black/12 dark:border-white/12`
- Radius: `rounded-sm` (2px — brand style, slightly softer than square)
- Focus: `outline-none ring-1 ring-pe-black/20 dark:ring-white/20`
- Background: transparent

### Buttons
- Primary: `bg-pe-black text-pe-cream dark:bg-pe-cream dark:text-pe-black` — `rounded-sm` — hover `bg-pe-charcoal`
- Secondary: `border border-pe-black/15 text-pe-charcoal` — `rounded-sm`
- Danger: `border border-red-200/60 text-red-500 hover:bg-red-50/50` — `rounded-sm`
- Icon-only row buttons: `p-2 rounded-sm hover:bg-pe-black/5 dark:hover:bg-white/8` with `title` attribute

### Avatar
- `w-12 h-12 rounded-full` with initials
- `bg-pe-black text-pe-cream` light / `bg-pe-cream text-pe-black` dark

### Separators
- `border-t border-pe-black/6 dark:border-white/6`

---

## 7. State Management

```typescript
// In UserManagement.tsx
const [editingUser, setEditingUser] = useState<UserDto | null>(null);

// Open drawer
setEditingUser(row);

// Close drawer
setEditingUser(null);

// After save: refresh data, keep drawer open for multi-edit
void refreshData();
```

Drawer manages its own local form state internally. On `onSaved`, parent refreshes the list.

---

## 8. API Surface (No New Endpoints)

All existing endpoints are reused:

| Action | Endpoint |
|---|---|
| Update name / status / role | `PATCH /api/users/{id}` |
| Reset password | `PATCH /api/users/{id}/password` |
| Get credit balance | `GET /api/customers/{id}/credit` |
| Grant credit | `POST /api/customers/{id}/credit/grant` |
| Assign worker role | `POST /api/admin/workers/{userId}/assign` |
| Revoke worker role | `DELETE /api/admin/workers/{userId}/revoke` |

---

## 9. Constraints & Edge Cases

- **Own account:** disable status toggle, role change, delete. Show muted message "No puedes modificar tu propia cuenta."
- **Busy state:** single `saving` flag per section — disable that section's submit while in-flight.
- **Errors:** inline below the relevant section, not a global toast.
- **Success:** subtle inline confirmation ("Guardado ✓") that fades after 2s — no drawer close on save (user may want to keep editing).
- **Drawer scroll:** `overflow-y-auto` — content may exceed viewport height.
- **Mobile:** drawer takes `w-full` on screens < `sm` breakpoint.

---

## 10. Files Changed

| File | Change |
|---|---|
| `frontend/src/islands/admin/UserEditDrawer.tsx` | **New** — ~350 lines |
| `frontend/src/islands/admin/UserManagement.tsx` | **Modified** — simplify buttons, add drawer state, render `<UserEditDrawer>` |
| `frontend/src/islands/admin/WorkerAssignmentModal.tsx` | **Deprecated** — no longer rendered anywhere; role logic inlined in drawer |
