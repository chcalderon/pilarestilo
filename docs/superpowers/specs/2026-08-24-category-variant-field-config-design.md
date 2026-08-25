# Category-configurable variant fields — design

## Problem

Every `ProductVariant` has exactly three fields: two customer-facing attributes
(physically the `color`/`size` columns — permanent, load-bearing names shared
by five codebases) plus stock (never customizable). Today the *label* shown
for the two attribute fields ("Color"/"Talla", "Color"/"Número",
"Material"/"Diseño"...) is picked from a fixed lookup of 7 hardcoded presets
keyed by a `CategoryType` enum (`GENERIC, CLOTHING, SHOES, JEWELRY, ACCESSORY,
COLLECTION, SEASON`) — `frontend/src/lib/variantSchema.ts`'s `SCHEMAS` table,
backed by `categories.category_type` (`V58`) and a per-product override
`products.variant_type` (`V69`).

The owner wants to type the two attribute labels directly, per category,
instead of picking among 7 presets — and optionally constrain each field to a
fixed list of options or a numeric range, enforced by the backend, not just
shown as a UI hint.

## Current state (as investigated)

- `ProductVariant` (`backend/.../product/domain/model/ProductVariant.java`):
  `color`, `size` (both `String`), `stockOnHand`, `stockReserved`. `size` runs
  through `ProductSizeRules.normalizeOrThrow` — a fixed heuristic (apparel
  tokens, numeric shoe-size pattern, or a ≥2-char generic-descriptor
  fallback) that already tolerates non-apparel text but rejects some valid
  short values (e.g. a single letter).
- `Category` (`backend/.../category/domain/model/Category.java`) carries
  `categoryType` (7-value enum, default `GENERIC`). No free-text label
  storage exists anywhere today.
- `frontend/src/lib/variantSchema.ts`'s `SCHEMAS` table maps each
  `CategoryType` to a `{label, inputType, options?}` pair per attribute.
  Every consumer (`ProductForm.tsx`, `ProductVariantSelector.tsx`,
  `CategoryTree.tsx`) is already dynamic off this module — none hardcode
  "Color"/"Talla" — so the label is already decoupled from the DB columns;
  it's just decoupled into a *fixed* 7-shape table, not admin-editable text.
- Real category tree (`V29__restore_seed_categories.sql`): `mujer` (root) has
  seven same-depth children — `invierno`, `verano` (pure groupings),
  `vestidos`, `pantalones`, `zapatos`, `carteras`, `accesorios` (shapes, plus
  `aros` nested one level under `accesorios`). Tree depth cannot disambiguate
  "shape" from "grouping" categories — a product like "Vestido de Verano" is
  tagged with `vestidos` and `verano` at the same depth. What *currently*
  disambiguates is the `CATEGORY_TYPE_PRIORITY` array ranking shape types
  (CLOTHING/SHOES/JEWELRY/ACCESSORY) above grouping types
  (GENERIC/COLLECTION/SEASON), independent of tree position.
- `Product.variantType` (the per-product override) is already dead in the
  persistence round-trip: `ProductRepositoryAdapter.applyToEntity()`/
  `toDomain()` never map it to/from `ProductEntity.variantType`, even though
  the column is JPA-mapped. A `GET` after a `POST`/`PATCH` setting it always
  returns `null`. Removing it is near-zero-risk.
- `services/product-service` is **not** a read-only bystander for this
  feature: it has its own `CategoryEntity`/`ProductDto`/`ProductMapper` that
  independently computes `categoryTypes`/`variantType` for `/api/products*`
  responses, and per the Caddy routing table it — not the monolith — answers
  `GET/HEAD /api/products*` whenever the `microservices` profile is active.
  `services/inventory-service` has zero references to either field and needs
  no changes. `services/order-service` is unaffected (doesn't touch
  categories or the two attribute columns' semantics, only their raw
  snapshot values on `order_items`).
- `CategoryController` has no `@PreAuthorize` on any write method today —
  every mutating category endpoint falls through to `.anyRequest()
  .authenticated()` in `SecurityConfig`, so any authenticated user (not just
  ADMIN) can create/update/delete categories, despite a `categories.update`
  permission already existing in the RBAC seed. Pre-existing gap, bundled
  into this change because the change raises its blast radius.

## Decisions

1. **Full replacement.** The 7-preset `CategoryType` enum stops being the
   source of variant labels. `categories.category_type` and
   `products.variant_type` become dead weight, dropped in a later *contract*
   migration once no code reads them (mirrors this repo's existing
   expand/contract pattern, e.g. `V76`/`V79` for `net_amount`/`tax_amount`).
   Not touched in the migration this feature ships with.
2. **Storage:** two new columns on `categories`:
   `defines_variant_fields BOOLEAN NOT NULL DEFAULT FALSE` and
   `variant_field_config JSONB NULL`, shaped:
   ```json
   {
     "primary":   { "label": "Color", "inputType": "FREE_TEXT",
                    "allowMultiple": false, "allowCustom": true },
     "secondary": { "label": "Talla", "inputType": "OPTIONS",
                    "options": ["XS","S","M","L","XL","XXL","XXXL","UNICO"],
                    "allowMultiple": true, "allowCustom": true }
   }
   ```
   or, for a numeric range: `"inputType": "RANGE", "min": 34, "max": 43`.
   Each of the two fields configures its input type independently.

   **Addendum (found reading `variantSchema.ts` in full at plan-writing time, not
   caught by the earlier design pass):** today's CLOTHING "Talla" field is not a
   single-value picker — the admin can combine several values into *one* variant
   row (e.g. a single row good for "S-M-L", stored as one hyphen-joined string in
   the `size` column) and can type a value outside the fixed list alongside picking
   from it. The owner asked to **keep this capability**, not simplify it away. So
   each field config carries two more flags: `allowMultiple` (the admin may select/
   combine several values into one row, joined with `-`) and `allowCustom`
   (a value outside `options`/outside the `min`-`max` range is still accepted).
   Both are meaningful only for `OPTIONS`/`RANGE`; a `FREE_TEXT` field is always
   effectively "custom", and `allowMultiple` on a `FREE_TEXT` field lets several
   free values be combined the same way ACCESSORY's generic pair already does today
   (`variantAndDetail`'s `defaultValues`/composite joining). One simplification
   *is* made deliberately: today's special case that "UNICO" cannot combine with a
   concrete size is CLOTHING-specific apparel idiom, not a generic rule — it is
   **not** preserved; multi-value validation is a plain duplicate-token check,
   nothing more.
3. **Shape vs. grouping split**, formalizing what the priority array already
   did implicitly: only categories with `defines_variant_fields = true`
   ("shape" categories — Zapatos, Vestidos, Aros, Carteras...) carry field
   config. Pure "grouping" categories (Mujer, Verano, Invierno, collections)
   never do, regardless of tree depth.
4. **At most one shape category per product.** Validated at product
   save time: 2+ assigned categories with `defines_variant_fields = true` →
   reject with a clear error naming the conflicting categories. Zero shape
   categories → fall back to a fixed generic pair (`"Variante"`/`"Detalle"`,
   both `FREE_TEXT`) — same fallback behavior `variantSchema.ts` already has
   for `GENERIC`/`COLLECTION`/`SEASON` today.
5. **No per-product override.** `products.variant_type` is removed from the
   write path entirely (already inert on read, per the finding above);
   variant field config is 100% category-derived.
6. **Backend-enforced validation**, not just a UI hint: when a category's
   field is `OPTIONS` or `RANGE`, the backend rejects a variant
   create/update whose `color`/`size` value isn't in the allowed
   set/range.
7. **`CategoryController` gets `@PreAuthorize("hasRole('ADMIN')")`** on its
   write methods, closing the pre-existing gap, bundled into this change.

## Backend architecture

**Validation does not live in `ProductVariant`/`Product` (the aggregate).**
`ProductVariant`'s constructor currently re-runs `ProductSizeRules
.normalizeOrThrow` on *every reconstruction from the database* —
`ProductRepositoryAdapter.toDomain()` rebuilds every variant on every
`findById`/`findAll`/`search`, not only on admin submission. If
category-config validation lived in the aggregate, editing a category's
`variant_field_config` to drop an option a stored variant already uses would
turn *loading* that product into a runtime error. So:

- `ProductSizeRules` shrinks to a purely structural check (trim, collapse
  whitespace, non-blank, sane max length) applied uniformly to `color` and
  `size` — it stops encoding any opinion about what a valid value *means*.
- A new `CategoryVariantFieldValidator` (`product/application/`,
  `@Component`) is the single place semantic validation lives. It runs only
  at write time, from `CreateProductUseCase`/`UpdateProductUseCase`:
  resolves the product's assigned categories via a new
  `CategoryRepository.findAllByIds(...)`, resolves the one shape category
  (or none) via a new `ShapeCategoryResolver.resolveOne(...)` (the single
  implementation of the "≤1 shape category" rule — same "one implementation
  per rule" discipline this codebase already holds itself to), and validates
  submitted `color`/`size` values against that category's
  `CategoryVariantFieldConfig` (or the generic fallback).
- This matches how cross-module reads already happen at the use-case layer
  in this codebase (`GetNavigationTreeUseCase` injecting `CategoryRepository`
  directly, `CreateOrderUseCase` injecting `ProductRepository` directly) —
  not through a domain service spanning two aggregates, not through the
  aggregate itself.
- New value object `category/domain/valueobjects/CategoryVariantFieldConfig`
  (validating record, mirroring `Brand.java`'s style): nested `FieldConfig`
  record (`label`, `inputType`, `options`, `min`, `max`, `allowMultiple`,
  `allowCustom`), `InputType` enum (`FREE_TEXT`, `OPTIONS`, `RANGE`), static
  `genericFallback()` (both fields `FREE_TEXT`, `allowMultiple = true`,
  `allowCustom = true` — matches today's ACCESSORY/GENERIC/COLLECTION/SEASON
  behavior).
- The validator splits a submitted value on `-` only when `allowMultiple` is
  true for that field (never for a single-value field, so a naturally
  hyphenated free-text value like a color name is never mis-split); each
  resulting token is checked against `OPTIONS`/`RANGE` unless `allowCustom`
  lets it through; `allowMultiple` also rejects a duplicate token within the
  same value, same as today's dedup check — with no "UNICO can't combine"
  special case, per the addendum above.
- JSONB persistence follows the existing pattern used by
  `NotificationEntity`/`PublicationSnapshotEntity`: `@JdbcTypeCode
  (SqlTypes.JSON)` on a `Map<String, Object>` field, with manual Map ↔
  value-object conversion in `CategoryRepositoryAdapter` — no new
  serialization mechanism introduced.

## Cross-codebase sync requirement

`services/product-service` must ship the equivalent shape-resolution logic
(its own `CategoryEntity` gains the two columns; its `ProductMapper`
reimplements "resolve the one shape category, else generic fallback" in
Java, since it shares no code with the monolith) **in the same commit and
deploy** as the monolith's product/category changes — the same
`microservices`-profile Caddy routing that makes `order-service` a
same-deploy dependency for order writes makes `product-service` a
same-deploy dependency for this feature's *reads*. Its JSON response shape
for the resolved variant-field config must match the monolith's byte-for-byte,
since the frontend doesn't know which backend answered.
`services/inventory-service` needs no changes (confirmed zero references to
either field).

## Frontend

- `frontend/src/lib/api.ts`: `CategoryDto`/`CategoryTreeNode` gain
  `definesVariantFields`/`variantFieldConfig`; `ProductDto` drops
  `variantType`, gains the resolved `variantFieldConfig`.
- `frontend/src/lib/variantSchema.ts`: the `SCHEMAS` table and every
  `CategoryType`-keyed helper (`CATEGORY_TYPE_PRIORITY`,
  `GROUPING_VARIANT_TYPES`/`SHAPE_VARIANT_TYPES`, `describeVariantType`,
  `variantFieldsOf`, `isSelectableVariantType`, `listSelectableVariantSchemas`,
  `allowedCategoryIds`, `resolvePreferredCategoryType`, `pickCategoryType`,
  `buildCategoryDepthMap`) are removed, replaced by functions building a
  `VariantSchema` directly from a `CategoryVariantFieldConfigDto` (or the
  generic fallback).
- `frontend/src/islands/admin/CategoryTree.tsx`: the `categoryType`
  `<select>` is replaced by a toggle ("esta categoría define campos de
  variante") plus, when on, two independent field editors — label text
  input, input-type radio (texto libre / lista de opciones / rango
  numérico), the matching conditional editor (comma-separated options list,
  or min/max number inputs), and two checkboxes (`allowMultiple`
  "permitir combinar varios valores en una variante", `allowCustom`
  "permitir un valor fuera de la lista") — both default-checked to match
  today's behavior for a category migrated from CLOTHING/SHOES.
- `frontend/src/islands/admin/ProductForm.tsx`: variant-row rendering,
  validation, and the category-picker restriction logic move from
  enum-derived schema lookup to config-derived schema, sourced from the
  already-fetched category list.
- `frontend/src/islands/product/ProductVariantSelector.tsx`: prop changes
  from `categoryTypes?: CategoryType[]` to a resolved
  `variantFieldConfig?: VariantFieldConfigDto` (this component only ever
  consumed a denormalized prop, never fetched categories itself — the
  backend-resolved config is what feeds it).

## Migration plan

- `V87__category_variant_field_config.sql` (expand): adds the two columns;
  backfills `defines_variant_fields`/`variant_field_config` for the 9
  existing categories from their *current* `category_type`, preserving
  today's exact labels/options (e.g. `zapatos` → shape, `{primary: Color/
  FREE_TEXT, secondary: Número/RANGE 34-43}`) so nothing changes visually
  until an admin edits it. `mujer`/`invierno`/`verano` → grouping (`defines_
  variant_fields = false`).
- A later, separate contract migration drops `categories.category_type` and
  `products.variant_type` once no code path reads them — not part of this
  feature's initial ship.

## Testing approach

- Backend: TDD for `CategoryVariantFieldConfig` (value object validation),
  `ShapeCategoryResolver` (0/1/2+ shape category cases), and
  `CategoryVariantFieldValidator` (FREE_TEXT/OPTIONS/RANGE acceptance and
  rejection, generic fallback). Characterization tests for
  `CreateProductUseCase`/`UpdateProductUseCase` covering the ≤1-shape-category
  rejection and the removal of `variantType` plumbing.
- `product-service`: update `ProductMapperTest` fixtures/assertions for the
  new resolved-config shape; contract-match test against the monolith's
  response shape if a convenient seam exists.
- Frontend: characterization tests (written first, run against current
  behavior) for `variantSchema.ts`'s replacement functions,
  `CategoryTree.tsx`'s new config editor, and `ProductForm.tsx`'s
  config-derived variant validation — following this session's established
  tests-first discipline for every file touched.
- End-to-end: verified against the full local Docker Compose stack
  (`--profile microservices` included, so `product-service`'s parity is
  actually exercised) before merge — create/edit a category's variant
  config, create a product against it, confirm the storefront variant
  selector and the admin form both reflect it, confirm the ≤1-shape-category
  rejection fires in the admin UI.

## Out of scope / explicitly deferred

- Per-product override of variant field config (removed, not replaced).
- Generalizing beyond exactly 2 customizable fields + fixed stock (not
  requested; the JSON shape is a fixed `{primary, secondary}` object, not an
  array, on purpose).
- Dropping `category_type`/`variant_type` columns (deferred to a follow-up
  contract migration).
- Any change to `services/order-service` or `services/inventory-service`
  (confirmed unaffected).
