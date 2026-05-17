# Frontend Navigation and Dynamic Variants Design

**Goal:** Unify storefront/admin navigation around one source of truth and migrate the frontend toward metadata-driven product variants without breaking legacy products or the current backend wire contract.

**Scope**

- Storefront navigation:
  - desktop mega menu
  - mobile overlay
  - mobile category rail
- Admin product management:
  - `ProductForm`
  - `ProductTable`
- Storefront product variant UX:
  - PDP selector
  - card/detail summaries
- Frontend/backed contract alignment for:
  - category metadata
  - product category typing
  - navigation tree

**Current Diagnosis**

1. Navigation uses two different sources:
   - `navigation/tree` for desktop mega menu and mobile overlay
   - `categories/tree` for the category rail in `Navbar.astro`
2. The mobile category rail is currently rendered hidden, leaving dead code and split behavior.
3. The frontend trims category metadata even though the backend already exposes:
   - `menuVisible`
   - `categoryType`
   - `heroImageUrl`
4. `ProductForm` and storefront variant UI remain hardwired to `color + size`.
5. The backend product wire contract is still legacy (`color`, `size`, `stock`), so migration must preserve compatibility.

**Architecture Decision**

Use a metadata-driven frontend variant layer with a legacy transport adapter.

- End-state UI model:
  - `CategoryAttributeDefinition`
    - `code`
    - `label`
    - `type`
    - `options`
    - `required`
    - `position`
    - optional presentation flags such as `multi`
- UI-facing variant model:
  - `attributes: Record<string, string | string[]>`
- Legacy transport adapter:
  - still serializes to backend `color` and `size`
  - bindings come from schema metadata, not view-specific `if` statements

**Incremental Migration Strategy**

1. Align category and product contracts:
   - expose `categoryType` and related metadata to the frontend
   - expose product `categoryTypes` to drive schema selection in storefront/admin
2. Unify navigation source:
   - derive all storefront navigation surfaces from `navigation/tree`
3. Introduce frontend schema primitives:
   - schema registry
   - schema inference
   - legacy transport bindings
4. Refactor `ProductForm` to render variant controls from schema metadata
5. Refactor storefront variant selector and summaries to use schema labels and options
6. Preserve backward compatibility by adapting schema values to legacy `color` and `size`

**Navigation Target**

- Single storefront source of truth: `NavigationTreeDto`
- Desktop and mobile must consume the same section tree
- The mobile category rail must use the same navigation structure, not `categories/tree`
- `FEATURED_GRID` and `EDITORIAL` remain supported metadata, even if the first migration keeps a restrained rendering fallback

**Variant Target**

- Clothing:
  - `color`
  - `size`
- Shoes:
  - `color`
  - `number`
- Jewelry:
  - `material`
  - `design`

No slug-based hacks. Any temporary inference must be centralized in schema configuration, keyed by category metadata and wrapped as a migration adapter.

**Risk Controls**

- Do not remove `color`/`size` from backend transport yet
- Keep current products editable
- Keep current PDP/cart flows working
- Add contract changes additively
- Prefer adapter layers over in-place assumptions

**Validation**

- Desktop mega menu opens from `navigation/tree`
- Mobile overlay and mobile category rail show the same categories
- Product admin adapts labels/options by category metadata
- Shoes render `Número`
- Jewelry renders non-apparel fields
- Legacy clothing products continue to work
