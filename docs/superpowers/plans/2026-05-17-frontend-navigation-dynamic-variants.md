# Frontend Navigation and Dynamic Variants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align frontend contracts and implement the first incremental pass of metadata-driven variants and unified storefront navigation.

**Architecture:** Add missing category/product metadata to the frontend boundary, switch storefront navigation to one source of truth, then layer a schema-driven variant adapter over the current `color`/`size` transport so legacy products keep working.

**Tech Stack:** Spring Boot, Astro, React, TypeScript

---

### Task 1: Align category/product metadata contracts

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/category/application/dto/CategoryTreeNode.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/usecases/GetCategoryTreeUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/dto/ProductDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/mappers/ProductMapper.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/persistence/CategoryEntity.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/web/dto/ProductDto.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/web/ProductMapper.java`
- Modify: `frontend/src/lib/api.ts`

- [ ] Extend DTOs additively with category metadata and product category typing.
- [ ] Keep old fields intact.
- [ ] Verify backend/service tests still pass.

### Task 2: Unify storefront navigation source

**Files:**
- Modify: `frontend/src/components/Navbar.astro`
- Modify: `frontend/src/islands/nav/MobileNavOverlay.tsx`
- Modify: `frontend/src/islands/nav/MegaMenuTray.tsx`

- [ ] Remove dual-source navigation dependency from the storefront header.
- [ ] Feed desktop, overlay, and category rail from `navigation/tree`.
- [ ] Restore the mobile category rail as a real storefront surface.

### Task 3: Introduce frontend variant schema adapter

**Files:**
- Create: `frontend/src/lib/variantSchema.ts`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/productVariants.ts`

- [ ] Define schema primitives and centralized legacy bindings.
- [ ] Add category-type-driven migration schemas for apparel, shoes, jewelry, and generic accessories.
- [ ] Keep transport compatibility with backend `color` and `size`.

### Task 4: Refactor admin ProductForm to schema-driven controls

**Files:**
- Modify: `frontend/src/islands/admin/ProductForm.tsx`

- [ ] Replace hardcoded `color + talla` rendering with schema-based fields.
- [ ] Keep boutique form simplicity.
- [ ] Preserve current save payload shape through adapter serialization.

### Task 5: Refactor storefront variant selector and summaries

**Files:**
- Modify: `frontend/src/islands/product/ProductVariantSelector.tsx`
- Modify: `frontend/src/pages/[locale]/products/[id].astro`
- Modify: `frontend/src/components/ProductCard.astro`
- Modify: `frontend/src/islands/admin/ProductTable.tsx`

- [ ] Render labels/options dynamically from schema.
- [ ] Show `Número` for shoes and generic secondary labels for other schemas.
- [ ] Preserve current cart integration.

### Task 6: Expose category metadata controls in admin taxonomy UI

**Files:**
- Modify: `frontend/src/islands/admin/CategoryTree.tsx`

- [ ] Surface `menuVisible`, `categoryType`, and `heroImageUrl`.
- [ ] Keep defaults compatible with existing categories.

### Task 7: Verify incrementally

**Files:**
- Modify only if needed: docs and focused tests

- [ ] Run focused backend tests.
- [ ] Run the extracted product service tests.
- [ ] Validate navigation and product flows locally as far as the environment allows.
