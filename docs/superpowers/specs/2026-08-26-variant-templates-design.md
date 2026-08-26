# Variant Templates: Decoupling Product Variants from Categories

## Context

The category-derived variant field config shipped in the previous cycle
(migrations V87/V88, `ShapeCategoryResolver`, `CategoryVariantFieldValidator`,
`allowedCategoryIdsFor`) tied a product's variant field labels to which
category it belonged to, with an "at most one shape category per product"
rule enforced at write time and mirrored as a UI lock in the category
picker.

In practice this coupling turned out to be the wrong axis: a category is a
navigation/merchandising concept (where a product shows up), while a
variant's shape (what fields it needs — Color/Talla, Tono/Numero,
Material/Diseno) is a property of the product itself, independent of how
it is filed. The first real category (`accesorios`) that needed to be both
a container and hold its own shape-defining child (`aros`) required a
data-fix migration (V88) and revealed that the tension would recur every
time the catalogue grows a category that is both a parent and a shape.

This spec replaces the category-derived system with an independent,
admin-managed catalogue of **variant templates**, assigned directly to a
product, with zero dependency on the product's categories.

## Goal

A product picks its variant field shape (labels, input type, options or
range, multi-value/custom-value flags) from a reusable, admin-editable
catalogue — completely decoupled from which categories it belongs to.

## What stays, what goes

**Reused as-is:** the field-config shape itself (label, inputType
FREE_TEXT/OPTIONS/RANGE, options, min, max, allowMultiple, allowCustom)
and its write-time validation semantics (range/options/multi-value/custom
enforcement, duplicate-token rejection) — this logic is correct and
already tested; only where it reads its config from changes.

**Removed from the category system:**
- `ShapeCategoryResolver` (the "at most one shape category" rule) — deleted
  entirely. A product now has at most one variant template because it is a
  single nullable foreign key, not a set with a cardinality rule to enforce.
- `CategoryVariantFieldValidator` — replaced by an equivalent validator
  scoped to `variant_template_id` instead of the product's categories.
- The "esta categoria define campos de variante" editor in `CategoryTree.tsx`,
  and `CategoryVariantFieldRequest`/the `definesVariantFields`/`primary`/
  `secondary` fields on `CreateCategoryRequest`/`UpdateCategoryRequest`/
  `CategoryDto`.
- `allowedCategoryIdsFor` and the shape-locking behavior in `ProductForm`'s
  category tree — categories become a plain, unrestricted multi-select
  again.

**Left in place, stopped being read/written (expand/contract, matching
this repo's established pattern for `products.variant_type` since V69):**
`categories.defines_variant_fields` and `categories.variant_field_config`
columns and their CHECK constraint. No data migration for existing
category configs — per product decision, products start with no template
assigned (falling back to the generic Variante/Detalle pair) and get
reassigned by hand. A later, separate contract migration drops the columns
once nothing references them, same as the still-pending `variant_type`
cleanup.

## Data model

New module `varianttemplate` (hexagonal, same shape as every other
domain module):

```
backend/src/main/java/com/pilarestilo/varianttemplate/
  domain/
    model/VariantTemplate.java          -- id, name, primary, secondary
    valueobjects/VariantFieldConfig.java -- FieldConfig(label, inputType, options, min, max, allowMultiple, allowCustom) + InputType enum
    ports/VariantTemplateRepository.java
  application/
    usecases/{Create,Update,Delete,Get,List}VariantTemplateUseCase.java
    dto/VariantTemplateDto.java
  infrastructure/
    persistence/entities/VariantTemplateEntity.java
    persistence/repositories/VariantTemplateRepositoryAdapter.java
    web/controllers/VariantTemplateController.java (/api/variant-templates)
    web/requests/{Create,Update}VariantTemplateRequest.java
```

`VariantFieldConfig` is the same shape as today's `CategoryVariantFieldConfig`
(label, inputType, options, min, max, allowMultiple, allowCustom on each of
`primary`/`secondary`), just renamed and re-homed to this module since it is
no longer a category concept. JSONB persistence follows the exact pattern
already proven for the category version: a `Map<String, Object>` column,
manual conversion helpers in the adapter (no generic serializer).

**Migrations** (current ceiling V88):
- `V89__variant_templates.sql`: creates `variant_templates` (id, name
  NOT NULL, `field_config JSONB NOT NULL` holding `{"primary": {...},
  "secondary": {...}}` — the exact same shape `categories.variant_field_config`
  already used, just always-present here instead of conditional on a
  boolean flag, since a template's only reason to exist is to carry a
  config).
- `V90__products_variant_template_id.sql`: `products.variant_template_id
  UUID NULL REFERENCES variant_templates(id)`. No backfill (starts null on
  every product, per decision).

Deleting a template that is referenced by any product is rejected (mirrors
the existing `CategoryRepository.hasAssociatedProducts` pattern used for
category deletion) — never a silent cascade to null.

## Backend: write-time enforcement

`VariantTemplateValidator` (application layer, same write-time-only
principle as its category-scoped predecessor — product reads must never
re-validate on load, only a category/template *edit* could otherwise turn
loading an existing product into a runtime error):

```java
public VariantFieldConfig resolveConfig(UUID variantTemplateId) {
    if (variantTemplateId == null) return VariantFieldConfig.genericFallback();
    return variantTemplateRepository.findById(variantTemplateId)
        .map(VariantTemplate::getConfig)
        .orElseThrow(...); // a stale/deleted template id is a data error, not a silent fallback
}
public void validate(VariantFieldConfig config, List<ProductVariantInput> variants) { /* unchanged logic */ }
```

Called from `CreateProductUseCase`/`UpdateProductUseCase` exactly where
`CategoryVariantFieldValidator` is called today, resolving from
`variantTemplateId` instead of `categoryIds`.

`Product` domain model: drop nothing added by the previous cycle except
retarget it — replace `variantFieldConfig` (resolved from categories) with
a `variantTemplateId` field the product actually owns, plus a resolved
`variantFieldConfig` for reads, populated by `ProductRepositoryAdapter`
from the referenced template (falling back to generic when null) — same
read-resolution shape as today, different source table.

`CategoryController`/category use cases: drop the `definesVariantFields`/
`primary`/`secondary` request fields and DTO fields; `categoryType` keeps
round-tripping as inert pass-through exactly as it already does.

`VariantTemplateController`: `@PreAuthorize("hasRole('ADMIN')")` on all
write methods, same as `CategoryController`. New permission catalog entries
`variant_templates.read` / `variant_templates.manage`, granted to ADMIN —
same shape as the `returns.read`/`returns.manage` pair added in V81, seeded
via a new migration following the V62-V64 permission-catalog pattern.

## Frontend

**New admin page** `/admin/tipos-variante` (new sidebar nav item, own
island component `VariantTemplateTable.tsx` or similar) — CRUD table
listing templates, using the same field-editor component the category
tree used to embed (`VariantFieldEditor`, extracted to a shared component
so it is not duplicated between the old category-tree location and the
new page).

**`CategoryTree.tsx`**: the "esta categoria define campos de variante"
block is deleted entirely, reverting to what it looked like before this
whole feature touched it (slug/name/order/hero/image/active/featured/menu
fields only). `allowedCategoryIdsFor`-based locking in `ProductForm`'s
category picker is deleted; category selection returns to an unrestricted
multi-select.

**`ProductForm.tsx`**: a "Tipo de Variante" `<select>` returns, populated
from `GET /api/variant-templates` (not a fixed enum) — selecting one drives
`buildVariantSchema`/the variant row editor exactly as the resolved
category config did; leaving it unset uses the generic Variante/Detalle
fallback. The payload gains `variantTemplateId` back
(`CreateProductRequest`/`UpdateProductRequest`), replacing `categoryIds`
as the variant-schema signal there (categoryIds stays, for categorization
only).

**`variantSchema.ts`**: `resolveVariantFieldConfig`/`allowedCategoryIdsFor`
(the category-scoped versions) are deleted; `buildVariantSchema` (config →
schema) is unchanged and reused as-is, since it already takes a plain
`CategoryVariantFieldConfigDto`-shaped object — renamed to a neutral
`VariantFieldConfigDto` to match the backend's renamed value object.

**Storefront** (`ProductVariantSelector.tsx`, `ProductCard.astro`,
`[id].astro`): unchanged in shape — they already consume a resolved
`variantFieldConfig` object on `ProductDto`; only its resolution source
changes server-side (from category lookup to template lookup), which is
invisible to these consumers.

## product-service parity

Same cross-repo-boundary rule this repo already documents for
`order-service` (CLAUDE.md: "Two codebases write the `orders` table") and
already applied once this cycle for the category-derived config:
`product-service` independently resolves `variant_template_id` against its
own `VariantTemplateEntity` (a read-only mirror of the same table, no
shared code, matching its existing `CategoryEntity` mirror) so
`GET/HEAD /api/products*` returns byte-identical `variantFieldConfig`
JSON to the monolith. Ships in the same commit set as the monolith change,
never separately.

## Testing approach

Same TDD discipline as the shipped category-derived version: unit tests
for `VariantTemplate`/`VariantFieldConfig` (domain), `VariantTemplateValidator`
(range/options/multi-value/custom-value — the existing
`CategoryVariantFieldValidatorTest` cases port over almost verbatim, only
the setup changes from category fixtures to template fixtures),
`VariantTemplateRepositoryAdapter` (JSONB round-trip), `CreateProductUseCase`/
`UpdateProductUseCase` (write-time rejection with the ledgered
`@InjectMocks` addition this repo hit last time), `product-service`
`ProductMapperTest` parity tests, and frontend characterization tests for
the deleted category-locking behavior removal, the new `ProductForm`
dropdown, and the new CMS CRUD page — mirroring the test files already
built for the category-tree editor (`CategoryTree.test.tsx`'s shape,
retargeted at the new `VariantTemplateTable` component).

End-to-end verification against the real Docker stack (Playwright +
direct API) repeats the same checklist proven last cycle: create a
template, assign it to a product, confirm the storefront and admin form
both reflect it via `product-service` and the monolith respectively,
confirm write-time rejection of an out-of-range/invalid value with a clear
error, confirm deleting an in-use template is refused.

## Global Constraints

- Migrations: current ceiling V88; this plan adds V89 (`variant_templates`
  table) and V90 (`products.variant_template_id`). Never edit V87/V88.
- `services/product-service` must ship matching resolver logic in the same
  commit set as any monolith change to product/variant-template read
  shape — same rule already established for `order-service` and applied
  once this cycle for the category-derived config.
- Write-time-only validation: `VariantTemplateValidator` runs only in
  `CreateProductUseCase`/`UpdateProductUseCase`, never inside the `Product`
  aggregate or on any read path — editing a template after products use it
  must never turn loading those products into a runtime error.
- `categories.defines_variant_fields`/`variant_field_config` columns and
  their CHECK constraint are left in the schema (expand/contract), stopped
  being read/written by application code; a future, separate contract
  migration removes them once nothing references them.
- RBAC: `VariantTemplateController` write methods require ADMIN, matching
  `CategoryController`'s existing gate.
