# Category-Configurable Variant Field Labels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fixed 7-preset `CategoryType` variant-label system with
free-text, per-field-independent label configuration on categories
(text/options-list/numeric-range, each optionally multi-value and/or
custom-value-tolerant), enforced by the backend, not just shown as a UI hint.

**Architecture:** Two new columns on `categories` (`defines_variant_fields`,
`variant_field_config` JSONB) replace `category_type` as the source of variant
labels. A new `ShapeCategoryResolver` picks the (at most one) "shape" category
among a product's assigned categories; a new `CategoryVariantFieldValidator`
enforces each field's configured constraint at product save time only —
never inside the `ProductVariant`/`Product` aggregate, which is reconstructed
on every read and must never fail to load existing data. `product-service`
(the extracted read microservice that actually answers `/api/products*` under
the `microservices` profile) gets the same resolver logic ported in Java,
shipped in the same commit/deploy. The frontend's `variantSchema.ts` stops
being a `CategoryType`-keyed lookup table and instead builds its
`VariantSchema` shape directly from the resolved category config.

**Tech Stack:** Java 25 / Spring Boot 4 (hexagonal), Postgres/Flyway,
TypeScript / Astro 4 / React, Vitest, JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-08-24-category-variant-field-config-design.md`
(read both the base spec and its "Addendum" — the addendum changes the JSON
shape from the base spec's first draft).

## Global Constraints

- Migration ceiling is **V87** — this feature's schema change is
  `V87__category_variant_field_config.sql`. Never edit an already-applied
  migration.
- `categories.category_type` and `products.variant_type` are **not** dropped
  in this plan — only stopped being read/written by application code. Column
  removal is a separate future contract migration (out of scope here).
- JSONB persistence follows the existing pattern verbatim:
  `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")` on a
  `Map<String, Object>` field — see `NotificationEntity.java` for the
  precedent. No new serialization library.
- Category-config validation lives **only** in the write path
  (`CreateProductUseCase`/`UpdateProductUseCase`, via a new
  `CategoryVariantFieldValidator`) — never in `ProductVariant`'s constructor
  or `Product.setVariants()`, both of which run on every read via
  `ProductRepositoryAdapter.toDomain()`.
- `services/product-service` must ship its own copy of the shape-resolution
  logic in the **same commit** as the monolith's product/category changes —
  it independently answers `GET/HEAD /api/products*` under the
  `microservices` Docker Compose profile and has its own `CategoryEntity`/
  `ProductDto`/`ProductMapper`, sharing no code with the monolith.
- `services/inventory-service` and `services/order-service` need **no**
  changes (confirmed: no references to `categoryType`/`variantType`/the two
  attribute columns' semantics).
- `CategoryController`'s write methods (`create`, `update`, `delete`,
  `reorder`) get `@PreAuthorize("hasRole('ADMIN')")`, matching the pattern in
  `NavigationSectionController` — bundled into this work per the owner's
  decision, closing a pre-existing gap this feature raises the stakes on.
- Behavior change, called out explicitly (not silent): `ProductSizeRules`'s
  apparel-token/shoe-number/generic-descriptor heuristic is deleted, replaced
  by a purely structural check (trim, collapse whitespace, non-blank, max
  length). This means values it used to reject (a single letter like `"X"`,
  a double-hyphen like `"L--XL"`) are now structurally valid — whether they
  are *semantically* valid is the new category-config validator's job, not a
  hardcoded apparel opinion. `CreateProductUseCaseTest`'s two tests asserting
  the old rejections must be updated to assert the new acceptance (Task 6).
- No new `app.*` Spring config keys.
- No Redis cache-key-prefix bump needed — `CategoryDto`/`CategoryTreeNode`
  gain fields (additive, backward-compatible for the cache's Jackson
  serializer); the existing `@CacheEvict` on `CreateCategoryUseCase`/
  `UpdateCategoryUseCase` already covers invalidation once this plan extends
  those use cases' parameters.

---

## Task 1: Migration — `categories` gains variant field config columns

**Files:**
- Create: `backend/src/main/resources/db/migration/V87__category_variant_field_config.sql`

**Interfaces:**
- Produces: `categories.defines_variant_fields` (boolean, not null, default
  false), `categories.variant_field_config` (jsonb, nullable) — consumed by
  Task 4's `CategoryEntity`.

- [ ] **Step 1: Write the migration**

```sql
-- V87__category_variant_field_config.sql
-- Replaces the fixed 7-preset CategoryType variant-label system with
-- free-text, per-field-configurable labels. See
-- docs/superpowers/specs/2026-08-24-category-variant-field-config-design.md.
--
-- category_type / products.variant_type are NOT dropped here -- they stop
-- being read once the application code in this feature ships, and are
-- removed in a later, separate contract migration once nothing references
-- them (same expand/contract pattern as V76/V79 for net_amount/tax_amount).

ALTER TABLE categories
    ADD COLUMN defines_variant_fields BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN variant_field_config JSONB NULL;

ALTER TABLE categories
    ADD CONSTRAINT chk_categories_variant_field_config
    CHECK (
        (defines_variant_fields = FALSE AND variant_field_config IS NULL)
        OR (defines_variant_fields = TRUE AND variant_field_config IS NOT NULL)
    );

-- Backfill the 9 seeded categories from their current category_type, so
-- nothing changes visually until an admin edits it.
UPDATE categories SET defines_variant_fields = TRUE, variant_field_config = '{
    "primary":   {"label": "Color", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true},
    "secondary": {"label": "Talla", "inputType": "OPTIONS",
                  "options": ["XS", "S", "M", "L", "XL", "XXL", "XXXL", "UNICO"],
                  "allowMultiple": true, "allowCustom": true}
}'::jsonb
WHERE slug IN ('vestidos', 'pantalones');

UPDATE categories SET defines_variant_fields = TRUE, variant_field_config = '{
    "primary":   {"label": "Color", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true},
    "secondary": {"label": "Numero", "inputType": "RANGE", "min": 34, "max": 43,
                  "allowMultiple": true, "allowCustom": true}
}'::jsonb
WHERE slug = 'zapatos';

UPDATE categories SET defines_variant_fields = TRUE, variant_field_config = '{
    "primary":   {"label": "Material", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true},
    "secondary": {"label": "Diseno", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true}
}'::jsonb
WHERE slug = 'aros';

UPDATE categories SET defines_variant_fields = TRUE, variant_field_config = '{
    "primary":   {"label": "Variante", "inputType": "FREE_TEXT", "allowMultiple": false, "allowCustom": true},
    "secondary": {"label": "Detalle", "inputType": "FREE_TEXT", "allowMultiple": true, "allowCustom": true}
}'::jsonb
WHERE slug IN ('carteras', 'accesorios');

-- mujer, invierno, verano stay defines_variant_fields = FALSE (grouping),
-- variant_field_config NULL -- the default from the ALTER TABLE above.
```

- [ ] **Step 2: Run the migration locally and verify**

Run: `cd backend && mvn -o flyway:info -Dflyway.url=jdbc:postgresql://localhost:5432/pilarestilo -Dflyway.user=pilarestilo -Dflyway.password=pilarestilo`
(or simply start the app against local Postgres — Flyway runs migrations on
boot). Expected: `V87` shows as `Success`, no pending migrations before it.

Then verify the backfill:
```sql
SELECT slug, defines_variant_fields, variant_field_config FROM categories ORDER BY slug;
```
Expected: `vestidos`/`pantalones`/`zapatos`/`aros`/`carteras`/`accesorios`
have `defines_variant_fields = true` with the JSON shown above; `mujer`/
`invierno`/`verano` have `false`/`null`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V87__category_variant_field_config.sql
git commit -m "feat(category): add variant field config columns (V87)"
```

---

## Task 2: `CategoryVariantFieldConfig` value object

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/category/domain/valueobjects/CategoryVariantFieldConfig.java`
- Test: `backend/src/test/java/com/pilarestilo/category/domain/valueobjects/CategoryVariantFieldConfigTest.java`

**Interfaces:**
- Produces: `CategoryVariantFieldConfig(FieldConfig primary, FieldConfig secondary)`,
  `CategoryVariantFieldConfig.FieldConfig(String label, InputType inputType, List<String> options, Integer min, Integer max, boolean allowMultiple, boolean allowCustom)`,
  `CategoryVariantFieldConfig.InputType` enum (`FREE_TEXT, OPTIONS, RANGE`),
  `CategoryVariantFieldConfig.genericFallback()` — consumed by Task 3
  (`ShapeCategoryResolver`), Task 4 (`Category` domain model), Task 7
  (`CategoryVariantFieldValidator`).

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.category.domain.valueobjects;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CategoryVariantFieldConfigTest {

    @Test
    void freeTextField_needsNoOptionsOrRange() {
        var field = new CategoryVariantFieldConfig.FieldConfig(
                "Color", CategoryVariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true);

        assertEquals("Color", field.label());
        assertEquals(CategoryVariantFieldConfig.InputType.FREE_TEXT, field.inputType());
    }

    @Test
    void optionsField_requiresAtLeastOneOption() {
        assertThrows(DomainException.class, () -> new CategoryVariantFieldConfig.FieldConfig(
                "Talla", CategoryVariantFieldConfig.InputType.OPTIONS, List.of(), null, null, true, true));
    }

    @Test
    void rangeField_requiresMinLessThanMax() {
        assertThrows(DomainException.class, () -> new CategoryVariantFieldConfig.FieldConfig(
                "Numero", CategoryVariantFieldConfig.InputType.RANGE, List.of(), 43, 34, true, true));
    }

    @Test
    void rangeField_requiresBothMinAndMax() {
        assertThrows(DomainException.class, () -> new CategoryVariantFieldConfig.FieldConfig(
                "Numero", CategoryVariantFieldConfig.InputType.RANGE, List.of(), 34, null, true, true));
    }

    @Test
    void blankLabel_isRejected() {
        assertThrows(DomainException.class, () -> new CategoryVariantFieldConfig.FieldConfig(
                "  ", CategoryVariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true));
    }

    @Test
    void genericFallback_isBothFieldsFreeTextMultipleAndCustom() {
        CategoryVariantFieldConfig fallback = CategoryVariantFieldConfig.genericFallback();

        assertEquals("Variante", fallback.primary().label());
        assertEquals("Detalle", fallback.secondary().label());
        assertEquals(CategoryVariantFieldConfig.InputType.FREE_TEXT, fallback.primary().inputType());
        assertTrue(fallback.primary().allowMultiple());
        assertTrue(fallback.secondary().allowCustom());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -o test -Dtest=CategoryVariantFieldConfigTest`
Expected: FAIL — compile error, `CategoryVariantFieldConfig` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.pilarestilo.category.domain.valueobjects;

import com.pilarestilo.shared.domain.DomainException;

import java.util.List;

public record CategoryVariantFieldConfig(FieldConfig primary, FieldConfig secondary) {

    public enum InputType { FREE_TEXT, OPTIONS, RANGE }

    public record FieldConfig(
            String label,
            InputType inputType,
            List<String> options,
            Integer min,
            Integer max,
            boolean allowMultiple,
            boolean allowCustom
    ) {
        public FieldConfig {
            if (label == null || label.isBlank()) {
                throw new DomainException("Variant field label cannot be blank");
            }
            label = label.trim();
            options = options == null ? List.of() : List.copyOf(options);
            if (inputType == InputType.OPTIONS && options.isEmpty()) {
                throw new DomainException("Variant field with OPTIONS input type requires at least one option");
            }
            if (inputType == InputType.RANGE) {
                if (min == null || max == null) {
                    throw new DomainException("Variant field with RANGE input type requires both min and max");
                }
                if (min >= max) {
                    throw new DomainException("Variant field RANGE min must be less than max");
                }
            }
        }
    }

    public static CategoryVariantFieldConfig genericFallback() {
        return new CategoryVariantFieldConfig(
                new FieldConfig("Variante", InputType.FREE_TEXT, List.of(), null, null, true, true),
                new FieldConfig("Detalle", InputType.FREE_TEXT, List.of(), null, null, true, true)
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -o test -Dtest=CategoryVariantFieldConfigTest`
Expected: PASS, 6/6.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/category/domain/valueobjects/CategoryVariantFieldConfig.java \
        backend/src/test/java/com/pilarestilo/category/domain/valueobjects/CategoryVariantFieldConfigTest.java
git commit -m "feat(category): add CategoryVariantFieldConfig value object"
```

---

## Task 3: `ShapeCategoryResolver` — the single "≤1 shape category" rule

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/category/domain/model/ShapeCategoryResolver.java`
- Test: `backend/src/test/java/com/pilarestilo/category/domain/model/ShapeCategoryResolverTest.java`

**Interfaces:**
- Consumes: `Category.isDefinesVariantFields()`, `Category.getSlug()` (Task 4).
- Produces: `ShapeCategoryResolver.resolveOne(Collection<Category>): Optional<Category>`
  (throws `DomainException` on 2+ shape categories) — consumed by Task 7
  (`CategoryVariantFieldValidator`) and Task 8 (`ProductRepositoryAdapter`).

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.category.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShapeCategoryResolverTest {

    private Category groupingCategory(String slug) {
        Category c = Category.create(slug, slug, slug, null, 0, null);
        return c;
    }

    private Category shapeCategory(String slug) {
        Category c = Category.create(slug, slug, slug, null, 0, null);
        c.updateVariantFieldConfig(true,
                com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig.genericFallback());
        return c;
    }

    @Test
    void noShapeCategories_resolvesToEmpty() {
        Optional<Category> result = ShapeCategoryResolver.resolveOne(
                List.of(groupingCategory("mujer"), groupingCategory("verano")));

        assertTrue(result.isEmpty());
    }

    @Test
    void oneShapeCategory_resolvesToIt() {
        Category zapatos = shapeCategory("zapatos");

        Optional<Category> result = ShapeCategoryResolver.resolveOne(
                List.of(groupingCategory("mujer"), zapatos));

        assertEquals(zapatos, result.orElseThrow());
    }

    @Test
    void twoShapeCategories_throwsNamingBoth() {
        Category zapatos = shapeCategory("zapatos");
        Category carteras = shapeCategory("carteras");

        DomainException ex = assertThrows(DomainException.class,
                () -> ShapeCategoryResolver.resolveOne(List.of(zapatos, carteras)));

        assertTrue(ex.getMessage().contains("zapatos"));
        assertTrue(ex.getMessage().contains("carteras"));
    }

    @Test
    void emptyCollection_resolvesToEmpty() {
        assertTrue(ShapeCategoryResolver.resolveOne(List.of()).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -o test -Dtest=ShapeCategoryResolverTest`
Expected: FAIL — `ShapeCategoryResolver` does not exist, and
`Category.updateVariantFieldConfig` does not exist yet (Task 4 defines it —
this test is written now but will not compile until Task 4 lands; note this
explicitly and do Task 4 immediately before running this test for real, or
write both in the same working session before the first "verify it fails"
run. The two are sequenced 3-before-4 in this plan only because the resolver
is the more focused unit; if strict red-green-refactor per task matters more
than task order here, swap to doing Task 4 first).

- [ ] **Step 3: Write the implementation**

```java
package com.pilarestilo.category.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ShapeCategoryResolver {

    private ShapeCategoryResolver() {}

    /**
     * The one category (if any) among {@code categories} that defines variant fields.
     * Throws when a product ends up tagged with two or more shape categories at once --
     * a data-quality error the admin must fix by picking one, not something to silently
     * disambiguate.
     */
    public static Optional<Category> resolveOne(Collection<Category> categories) {
        List<Category> shapeCategories = categories.stream()
                .filter(Category::isDefinesVariantFields)
                .toList();
        if (shapeCategories.isEmpty()) {
            return Optional.empty();
        }
        if (shapeCategories.size() > 1) {
            String slugs = shapeCategories.stream().map(Category::getSlug).collect(Collectors.joining(", "));
            throw new DomainException(
                    "Product cannot belong to more than one variant-defining category at once: " + slugs);
        }
        return Optional.of(shapeCategories.getFirst());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -o test -Dtest=ShapeCategoryResolverTest`
Expected: PASS, 4/4 (after Task 4 has also landed).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/category/domain/model/ShapeCategoryResolver.java \
        backend/src/test/java/com/pilarestilo/category/domain/model/ShapeCategoryResolverTest.java
git commit -m "feat(category): add ShapeCategoryResolver (at-most-one-shape rule)"
```

---

## Task 4: `Category` domain model + persistence (entity, port, adapter)

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/category/domain/model/Category.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/domain/ports/CategoryRepository.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/persistence/entities/CategoryEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/persistence/repositories/CategoryRepositoryAdapter.java`
- Test: `backend/src/test/java/com/pilarestilo/category/domain/model/CategoryVariantFieldConfigMutatorTest.java`

**Interfaces:**
- Produces: `Category.isDefinesVariantFields(): boolean`,
  `Category.getVariantFieldConfig(): CategoryVariantFieldConfig` (nullable),
  `Category.updateVariantFieldConfig(boolean, CategoryVariantFieldConfig): void`,
  `Category.setDefinesVariantFields(boolean)` / `Category.setVariantFieldConfig(CategoryVariantFieldConfig)`
  (persistence-rehydration setters, mirroring the existing `setCategoryType`
  pattern), `CategoryRepository.findAllByIds(Collection<UUID>): List<Category>`
  — consumed by Task 3 (already), Task 5 (use cases/DTOs), Task 7 (validator).

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.category.domain.model;

import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryVariantFieldConfigMutatorTest {

    @Test
    void category_defaultsToNotDefiningVariantFields() {
        Category c = Category.create("mujer", "Mujer", "Women", null, 0, null);

        assertFalse(c.isDefinesVariantFields());
        assertNull(c.getVariantFieldConfig());
    }

    @Test
    void updateVariantFieldConfig_setsBothFields() {
        Category c = Category.create("zapatos", "Zapatos", "Shoes", null, 0, null);
        CategoryVariantFieldConfig config = CategoryVariantFieldConfig.genericFallback();

        c.updateVariantFieldConfig(true, config);

        assertTrue(c.isDefinesVariantFields());
        assertEquals(config, c.getVariantFieldConfig());
    }

    @Test
    void updateVariantFieldConfig_falseClearsConfigEvenIfProvided() {
        Category c = Category.create("mujer", "Mujer", "Women", null, 0, null);

        c.updateVariantFieldConfig(false, CategoryVariantFieldConfig.genericFallback());

        assertFalse(c.isDefinesVariantFields());
        assertNull(c.getVariantFieldConfig());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -o test -Dtest=CategoryVariantFieldConfigMutatorTest`
Expected: FAIL — compile error, no such methods on `Category`.

- [ ] **Step 3: Modify `Category.java`**

Add the import and field near the existing `categoryType` field
(`Category.java:31`), and the mutator near `updateMenuMetadata`
(`Category.java:68-72`):

```java
// add to imports
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;

// add fields, alongside the existing categoryType field
private boolean definesVariantFields = false;
private CategoryVariantFieldConfig variantFieldConfig;
```

```java
// add after updateMenuMetadata (Category.java:72)
public void updateVariantFieldConfig(boolean definesVariantFields, CategoryVariantFieldConfig variantFieldConfig) {
    this.definesVariantFields = definesVariantFields;
    this.variantFieldConfig = definesVariantFields ? variantFieldConfig : null;
}
```

```java
// add alongside the existing getCategoryType()/getHeroImageUrl() getters
public boolean isDefinesVariantFields() { return definesVariantFields; }
public CategoryVariantFieldConfig getVariantFieldConfig() { return variantFieldConfig; }
```

```java
// add alongside the existing setCategoryType()/setHeroImageUrl() persistence setters
public void setDefinesVariantFields(boolean definesVariantFields) { this.definesVariantFields = definesVariantFields; }
public void setVariantFieldConfig(CategoryVariantFieldConfig variantFieldConfig) { this.variantFieldConfig = variantFieldConfig; }
```

- [ ] **Step 4: Modify `CategoryRepository.java`** — add the port method

```java
// add import
import java.util.Collection;

// add to the interface, alongside findAll()/findChildren()
List<Category> findAllByIds(Collection<UUID> ids);
```

- [ ] **Step 5: Modify `CategoryEntity.java`** — add JSONB-backed columns

```java
// add imports
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

// add fields, alongside categoryType/heroImageUrl
@Column(name = "defines_variant_fields", nullable = false)
private boolean definesVariantFields = false;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "variant_field_config", columnDefinition = "jsonb")
private Map<String, Object> variantFieldConfig;

// add getters/setters, matching the existing style
public boolean isDefinesVariantFields() { return definesVariantFields; }
public void setDefinesVariantFields(boolean definesVariantFields) { this.definesVariantFields = definesVariantFields; }
public Map<String, Object> getVariantFieldConfig() { return variantFieldConfig; }
public void setVariantFieldConfig(Map<String, Object> variantFieldConfig) { this.variantFieldConfig = variantFieldConfig; }
```

- [ ] **Step 6: Modify `CategoryRepositoryAdapter.java`** — implement
`findAllByIds` and the Map ↔ value-object conversion

```java
// add imports
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import java.util.Collection;
import java.util.LinkedHashMap;

// add to the interface implementation, alongside findChildren()
@Override
public List<Category> findAllByIds(Collection<UUID> ids) {
    return jpa.findAllById(ids).stream().map(this::toDomain).toList();
}
```

Add the conversion helpers (private methods at the bottom of the class) and
wire them into `toEntity`/`toDomain`:

```java
private static Map<String, Object> toRawConfig(CategoryVariantFieldConfig config) {
    if (config == null) return null;
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("primary", toRawField(config.primary()));
    raw.put("secondary", toRawField(config.secondary()));
    return raw;
}

private static Map<String, Object> toRawField(CategoryVariantFieldConfig.FieldConfig field) {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("label", field.label());
    raw.put("inputType", field.inputType().name());
    raw.put("options", field.options());
    raw.put("min", field.min());
    raw.put("max", field.max());
    raw.put("allowMultiple", field.allowMultiple());
    raw.put("allowCustom", field.allowCustom());
    return raw;
}

@SuppressWarnings("unchecked")
private static CategoryVariantFieldConfig fromRawConfig(Map<String, Object> raw) {
    if (raw == null) return null;
    return new CategoryVariantFieldConfig(
            fromRawField((Map<String, Object>) raw.get("primary")),
            fromRawField((Map<String, Object>) raw.get("secondary")));
}

@SuppressWarnings("unchecked")
private static CategoryVariantFieldConfig.FieldConfig fromRawField(Map<String, Object> raw) {
    List<String> options = raw.get("options") == null
            ? List.of()
            : ((List<Object>) raw.get("options")).stream().map(String::valueOf).toList();
    return new CategoryVariantFieldConfig.FieldConfig(
            (String) raw.get("label"),
            CategoryVariantFieldConfig.InputType.valueOf((String) raw.get("inputType")),
            options,
            raw.get("min") == null ? null : ((Number) raw.get("min")).intValue(),
            raw.get("max") == null ? null : ((Number) raw.get("max")).intValue(),
            Boolean.TRUE.equals(raw.get("allowMultiple")),
            Boolean.TRUE.equals(raw.get("allowCustom")));
}
```

In `toEntity()` (`CategoryRepositoryAdapter.java:71-87`), add after
`e.setHeroImageUrl(...)`:
```java
e.setDefinesVariantFields(c.isDefinesVariantFields());
e.setVariantFieldConfig(toRawConfig(c.getVariantFieldConfig()));
```

In `toDomain()` (`CategoryRepositoryAdapter.java:89-102`), add after
`c.setHeroImageUrl(...)`:
```java
c.updateVariantFieldConfig(e.isDefinesVariantFields(), fromRawConfig(e.getVariantFieldConfig()));
```
(replacing the bare `c.setCategoryType(...)`-style setter here with the
mutator keeps the false→null invariant enforced on every read too, not only
on writes going through the use cases.)

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && mvn -o test -Dtest=CategoryVariantFieldConfigMutatorTest,ShapeCategoryResolverTest`
Expected: PASS, 3/3 and 4/4.

- [ ] **Step 8: Run the full backend unit suite to check nothing broke**

Run: `cd backend && mvn -o test`
Expected: BUILD SUCCESS (the JSONB round-trip itself is only exercised by
`mvn verify`'s Testcontainers-backed integration tests, checked in Task 9's
end-to-end pass — a plain `mvn test` here just confirms nothing else broke
compiling against the changed `Category`/`CategoryRepository` surface).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/category/domain/model/Category.java \
        backend/src/main/java/com/pilarestilo/category/domain/ports/CategoryRepository.java \
        backend/src/main/java/com/pilarestilo/category/infrastructure/persistence/entities/CategoryEntity.java \
        backend/src/main/java/com/pilarestilo/category/infrastructure/persistence/repositories/CategoryRepositoryAdapter.java \
        backend/src/test/java/com/pilarestilo/category/domain/model/CategoryVariantFieldConfigMutatorTest.java
git commit -m "feat(category): persist variant field config (domain + JPA + adapter)"
```

---

## Task 5: Category DTOs, use cases, requests, controller (+ RBAC fix)

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/category/application/dto/CategoryDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/dto/CategoryTreeNode.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/usecases/GetCategoryTreeUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/usecases/CreateCategoryUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/usecases/UpdateCategoryUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/CategoryVariantFieldRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/CreateCategoryRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/UpdateCategoryRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/web/controllers/CategoryController.java`
- Test: `backend/src/test/java/com/pilarestilo/category/application/usecases/CreateCategoryUseCaseVariantFieldTest.java`

**Interfaces:**
- Consumes: `Category.updateVariantFieldConfig`, `CategoryVariantFieldConfig`
  (Tasks 2, 4).
- Produces: `CategoryDto`/`CategoryTreeNode` with `definesVariantFields`/
  `variantFieldConfig` fields — consumed by Task 10 (frontend `api.ts`).

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.infrastructure.web.requests.CategoryVariantFieldRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCategoryUseCaseVariantFieldTest {

    @Mock
    CategoryRepository categoryRepository;

    @Test
    void create_withVariantFieldConfig_persistsIt() {
        CreateCategoryUseCase useCase = new CreateCategoryUseCase(categoryRepository);
        when(categoryRepository.existsBySlug("zapatos")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        var primary = new CategoryVariantFieldRequest("Color", "FREE_TEXT", List.of(), null, null, false, true);
        var secondary = new CategoryVariantFieldRequest("Numero", "RANGE", List.of(), 34, 43, true, true);

        CategoryDto dto = useCase.execute(
                "zapatos", "Zapatos", "Shoes", null, 5, null,
                true, false, true, "GENERIC", null,
                true, primary, secondary
        );

        assertTrue(dto.definesVariantFields());
        assertNotNull(dto.variantFieldConfig());
    }

    @Test
    void create_withoutDefiningVariantFields_leavesConfigNull() {
        CreateCategoryUseCase useCase = new CreateCategoryUseCase(categoryRepository);
        when(categoryRepository.existsBySlug("mujer")).thenReturn(false);
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        when(categoryRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute("mujer", "Mujer", "Women", null, 0, null,
                true, false, true, "GENERIC", null,
                false, null, null);

        assertFalse(captor.getValue().isDefinesVariantFields());
        assertNull(captor.getValue().getVariantFieldConfig());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -o test -Dtest=CreateCategoryUseCaseVariantFieldTest`
Expected: FAIL — `CategoryVariantFieldRequest` doesn't exist, `execute(...)`
has no such overload, `CategoryDto.definesVariantFields()`/`variantFieldConfig()`
don't exist.

- [ ] **Step 3: Create `CategoryVariantFieldRequest.java`**

```java
package com.pilarestilo.category.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CategoryVariantFieldRequest(
        @NotBlank String label,
        @NotNull @Pattern(regexp = "FREE_TEXT|OPTIONS|RANGE", message = "invalid inputType") String inputType,
        List<String> options,
        Integer min,
        Integer max,
        boolean allowMultiple,
        boolean allowCustom
) {}
```

- [ ] **Step 4: Modify `CategoryDto.java`**

```java
// add to the record's component list, after heroImageUrl
boolean definesVariantFields,
CategoryVariantFieldConfigDto variantFieldConfig
```

Add a small nested-style companion DTO in the same file (below the record,
mirroring `ProductDto`'s nested-record style):

```java
package com.pilarestilo.category.application.dto;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;

import java.util.List;
import java.util.UUID;

public record CategoryDto(
        UUID id, String slug, String nameEs, String nameEn, UUID parentId, int sortOrder,
        boolean active, boolean featured, String imageUrl, boolean menuVisible,
        String categoryType, String heroImageUrl,
        boolean definesVariantFields, CategoryVariantFieldConfigDto variantFieldConfig
) {
    public static CategoryDto from(Category c) {
        return new CategoryDto(
                c.getId(), c.getSlug(), c.getNameEs(), c.getNameEn(),
                c.getParentId(), c.getSortOrder(), c.isActive(), c.isFeatured(), c.getImageUrl(),
                c.isMenuVisible(),
                c.getCategoryType() != null ? c.getCategoryType().name() : "GENERIC",
                c.getHeroImageUrl(),
                c.isDefinesVariantFields(),
                CategoryVariantFieldConfigDto.from(c.getVariantFieldConfig())
        );
    }

    public record CategoryVariantFieldConfigDto(FieldDto primary, FieldDto secondary) {
        static CategoryVariantFieldConfigDto from(CategoryVariantFieldConfig config) {
            if (config == null) return null;
            return new CategoryVariantFieldConfigDto(FieldDto.from(config.primary()), FieldDto.from(config.secondary()));
        }

        public record FieldDto(String label, String inputType, List<String> options, Integer min, Integer max,
                                boolean allowMultiple, boolean allowCustom) {
            static FieldDto from(CategoryVariantFieldConfig.FieldConfig field) {
                return new FieldDto(field.label(), field.inputType().name(), field.options(),
                        field.min(), field.max(), field.allowMultiple(), field.allowCustom());
            }
        }
    }
}
```

- [ ] **Step 5: Modify `CategoryTreeNode.java`** — same two new fields, and
thread them through `GetCategoryTreeUseCase.toNode()` (`GetCategoryTreeUseCase.java:47-54`)

```java
public record CategoryTreeNode(
        UUID id, UUID parentId, String slug, String nameEs, String nameEn, int sortOrder,
        boolean active, boolean featured, String imageUrl, boolean menuVisible,
        String categoryType, String heroImageUrl,
        boolean definesVariantFields, CategoryDto.CategoryVariantFieldConfigDto variantFieldConfig,
        List<CategoryTreeNode> children
) {}
```

```java
// GetCategoryTreeUseCase.toNode(), add two more constructor args before `children`
return new CategoryTreeNode(
        c.getId(), c.getParentId(), c.getSlug(), c.getNameEs(), c.getNameEn(),
        c.getSortOrder(), c.isActive(), c.isFeatured(), c.getImageUrl(),
        c.isMenuVisible(),
        c.getCategoryType() != null ? c.getCategoryType().name() : "GENERIC",
        c.getHeroImageUrl(),
        c.isDefinesVariantFields(),
        CategoryDto.CategoryVariantFieldConfigDto.from(c.getVariantFieldConfig()),
        children
);
```

- [ ] **Step 6: Modify `CreateCategoryUseCase.java`/`UpdateCategoryUseCase.java`**

Add a private mapper and extend `execute(...)`'s parameter list in both
classes identically:

```java
// add import to both classes
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.category.infrastructure.web.requests.CategoryVariantFieldRequest;

// add to execute(...)'s signature, after heroImageUrl, in both classes
boolean definesVariantFields, CategoryVariantFieldRequest primary, CategoryVariantFieldRequest secondary

// add before c.updateMenuMetadata(...)/before c.updateVariantFieldConfig(...) call
c.updateVariantFieldConfig(definesVariantFields, toVariantFieldConfig(definesVariantFields, primary, secondary));

// add as a private method in both classes
private CategoryVariantFieldConfig toVariantFieldConfig(
        boolean definesVariantFields, CategoryVariantFieldRequest primary, CategoryVariantFieldRequest secondary) {
    if (!definesVariantFields) return null;
    if (primary == null || secondary == null) {
        throw new DomainException("primary and secondary field config are required when defining variant fields");
    }
    return new CategoryVariantFieldConfig(toFieldConfig(primary), toFieldConfig(secondary));
}

private CategoryVariantFieldConfig.FieldConfig toFieldConfig(CategoryVariantFieldRequest req) {
    return new CategoryVariantFieldConfig.FieldConfig(
            req.label(),
            CategoryVariantFieldConfig.InputType.valueOf(req.inputType()),
            req.options(), req.min(), req.max(), req.allowMultiple(), req.allowCustom());
}
```

(`@SuppressWarnings("java:S107")` is already present on both `execute`
methods — the parameter count grows further, which is the existing,
accepted pattern for these two use cases per their own comment: "One
parameter per field the category form actually submits.")

- [ ] **Step 7: Modify `CreateCategoryRequest.java`/`UpdateCategoryRequest.java`**

```java
// add to both records, after heroImageUrl
boolean definesVariantFields,
@Valid CategoryVariantFieldRequest primary,
@Valid CategoryVariantFieldRequest secondary
```
(add `import jakarta.validation.Valid;` and
`import com.pilarestilo.category.infrastructure.web.requests.CategoryVariantFieldRequest;`
— the latter is redundant if already in the same package, drop if so.)

- [ ] **Step 8: Modify `CategoryController.java`** — thread the new request
fields through, and add `@PreAuthorize`

```java
// add import
import org.springframework.security.access.prepost.PreAuthorize;

// add above create(), update(), delete(), and the reorder() method
@PreAuthorize("hasRole('ADMIN')")
```

```java
// create(): extend the createCategory.execute(...) call
CategoryDto dto = createCategory.execute(
        req.slug(), req.nameEs(), req.nameEn(),
        req.parentId(), req.sortOrder(), req.imageUrl(),
        req.active(), req.featured(), req.menuVisible(), req.categoryType(), req.heroImageUrl(),
        req.definesVariantFields(), req.primary(), req.secondary()
);
```

```java
// update(): extend the updateCategory.execute(...) call
return updateCategory.execute(
        id, req.slug(), req.nameEs(), req.nameEn(),
        req.parentId(), req.sortOrder(), req.active(), req.featured(), req.imageUrl(),
        req.menuVisible(), req.categoryType(), req.heroImageUrl(),
        req.definesVariantFields(), req.primary(), req.secondary()
);
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd backend && mvn -o test -Dtest=CreateCategoryUseCaseVariantFieldTest`
Expected: PASS, 2/2.

- [ ] **Step 10: Run the full backend unit suite**

Run: `cd backend && mvn -o test`
Expected: BUILD SUCCESS. If any existing test constructs `CreateCategoryRequest`/
`UpdateCategoryRequest`/calls `CreateCategoryUseCase.execute(...)`/
`UpdateCategoryUseCase.execute(...)` positionally without the three new
trailing params (`definesVariantFields, primary, secondary`), fix those call
sites now (grep
`grep -rn "createCategory.execute\|new CreateCategoryRequest\|new UpdateCategoryRequest" backend/src/test`).

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/category/application/dto/CategoryDto.java \
        backend/src/main/java/com/pilarestilo/category/application/dto/CategoryTreeNode.java \
        backend/src/main/java/com/pilarestilo/category/application/usecases/GetCategoryTreeUseCase.java \
        backend/src/main/java/com/pilarestilo/category/application/usecases/CreateCategoryUseCase.java \
        backend/src/main/java/com/pilarestilo/category/application/usecases/UpdateCategoryUseCase.java \
        backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/CategoryVariantFieldRequest.java \
        backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/CreateCategoryRequest.java \
        backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/UpdateCategoryRequest.java \
        backend/src/main/java/com/pilarestilo/category/infrastructure/web/controllers/CategoryController.java \
        backend/src/test/java/com/pilarestilo/category/application/usecases/CreateCategoryUseCaseVariantFieldTest.java
git commit -m "feat(category): expose variant field config via API, gate writes to ADMIN"
```

---

## Task 6: Reduce `ProductSizeRules` to a structural-only check

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/product/domain/model/ProductSizeRules.java`
- Modify: `backend/src/test/java/com/pilarestilo/product/application/CreateProductUseCaseTest.java`
- Test: `backend/src/test/java/com/pilarestilo/product/domain/model/ProductSizeRulesTest.java` (new — this class had no direct test before; it was only exercised indirectly through `CreateProductUseCaseTest`)

**Interfaces:**
- Produces: `ProductSizeRules.normalizeOrThrow(String): String` — same
  signature, new (looser) behavior — consumed by `ProductVariant`'s
  constructor (unchanged call site).

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductSizeRulesTest {

    @Test
    void trimsAndCollapsesInternalWhitespace() {
        assertEquals("L XL", callNormalize("  L    XL  "));
    }

    @Test
    void rejectsBlank() {
        assertThrows(DomainException.class, () -> callNormalize("   "));
    }

    @Test
    void rejectsNull() {
        assertThrows(DomainException.class, () -> callNormalize(null));
    }

    @Test
    void acceptsASingleLetter_noLongerRejectedAsTooShort() {
        assertEquals("X", callNormalize("X"));
    }

    @Test
    void acceptsADoubleHyphen_noLongerAnApparelFormatError() {
        assertEquals("L--XL", callNormalize("L--XL"));
    }

    @Test
    void rejectsOverMaxLength() {
        String tooLong = "A".repeat(41);
        assertThrows(DomainException.class, () -> callNormalize(tooLong));
    }

    @Test
    void acceptsExactlyMaxLength() {
        String exact = "A".repeat(40);
        assertEquals(exact, callNormalize(exact));
    }

    // ProductSizeRules is package-private; call it through the one public entry
    // point that already exists in this package, ProductVariant, to avoid
    // widening its visibility just for tests.
    private static String callNormalize(String raw) {
        return new ProductVariant("Color", raw, 0).getSize();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -o test -Dtest=ProductSizeRulesTest`
Expected: FAIL — `acceptsASingleLetter_noLongerRejectedAsTooShort` and
`acceptsADoubleHyphen_noLongerAnApparelFormatError` fail against the
current apparel-heuristic implementation (both currently throw); the
whitespace-collapse and max-length tests fail because that behavior doesn't
exist yet either.

- [ ] **Step 3: Rewrite `ProductSizeRules.java`**

```java
package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;

/**
 * Purely structural: trim, collapse internal whitespace, reject blank, cap length.
 *
 * <p>What a value <em>means</em> -- whether "S" is a valid size for this product's
 * category, whether "34" is in range -- is the category's variant field config,
 * enforced at write time by {@code CategoryVariantFieldValidator}, not this class.
 * This class only guards against garbage making it into storage at all: it runs on
 * every read, via {@code ProductVariant}'s constructor, so it must never become
 * stricter in a way that could reject a variant that was valid when it was saved.
 */
final class ProductSizeRules {

    private static final int MAX_LENGTH = 40;

    private ProductSizeRules() {}

    static String normalizeOrThrow(String rawSize) {
        if (rawSize == null) {
            throw new DomainException("Invalid product variant size: null");
        }
        String normalized = rawSize.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new DomainException("Invalid product variant size: exceeds " + MAX_LENGTH + " characters");
        }
        return normalized;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -o test -Dtest=ProductSizeRulesTest`
Expected: PASS, 7/7.

- [ ] **Step 5: Update `CreateProductUseCaseTest`'s now-intentionally-changed assertions**

In `backend/src/test/java/com/pilarestilo/product/application/CreateProductUseCaseTest.java`,
replace the two tests that assert the old, now-deleted apparel-format
rejections:

```java
// DELETE this test (behavior intentionally changed -- see ProductSizeRulesTest instead)
@Test
void rejects_invalid_size_token_x() { ... }

// REPLACE WITH:
@Test
void accepts_a_single_letter_size_structural_check_only_now() {
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    ProductDto dto = useCase.execute(
            "Abrigo", "desc", BigDecimal.valueOf(120000), "CLP", null, null,
            "http://img", "NEW", "Pilar", 0, true, null,
            List.of(new ProductVariantInput("Camel", "X", 1))
    );

    assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("X")));
}
```

```java
// DELETE this test (behavior intentionally changed)
@Test
void rejects_invalid_composite_format_with_double_dash() { ... }

// REPLACE WITH:
@Test
void accepts_a_double_hyphen_structural_check_only_now() {
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    ProductDto dto = useCase.execute(
            "Abrigo", "desc", BigDecimal.valueOf(120000), "CLP", null, null,
            "http://img", "NEW", "Pilar", 0, true, null,
            List.of(new ProductVariantInput("Camel", "L--XL", 1))
    );

    assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("L--XL")));
}
```

Also revisit `supports_composite_sizes_and_normalizes_to_uppercase_hyphen`
(`CreateProductUseCaseTest.java:85-114`): it asserts `"xl"` normalizes to
`"XL"` and `"l-xl"` to `"L-XL"`. That uppercasing was part of the deleted
apparel heuristic and is gone. Rename and rewrite it:

```java
// REPLACE supports_composite_sizes_and_normalizes_to_uppercase_hyphen WITH:
@Test
void stores_sizes_exactly_as_submitted_after_trimming() {
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    ProductDto dto = useCase.execute(
            "Abrigo", "desc", BigDecimal.valueOf(120000), "CLP", null, null,
            "http://img", "NEW", "Pilar", 0, true, null,
            List.of(
                    new ProductVariantInput("Camel", "xl", 1),
                    new ProductVariantInput("Camel", "l-xl", 2),
                    new ProductVariantInput("Negro", "  s-m-l  ", 3)
            )
    );

    assertEquals(6, dto.stock());
    assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("xl")));
    assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("l-xl")));
    assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("s-m-l")));
}
```

- [ ] **Step 6: Run the full product test module**

Run: `cd backend && mvn -o test -Dtest=CreateProductUseCaseTest,ProductSizeRulesTest`
Expected: PASS, all green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/product/domain/model/ProductSizeRules.java \
        backend/src/test/java/com/pilarestilo/product/domain/model/ProductSizeRulesTest.java \
        backend/src/test/java/com/pilarestilo/product/application/CreateProductUseCaseTest.java
git commit -m "refactor(product): shrink ProductSizeRules to a structural-only check

Semantic validation (is this size valid for THIS product's category) moves
to CategoryVariantFieldValidator (write-time only). This class runs on every
read via ProductVariant's constructor and must never reject previously-valid
data, so it now only guards blank/oversized garbage -- not what a value means."
```

---

## Task 7: `CategoryVariantFieldValidator` — write-time semantic validation

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/product/application/CategoryVariantFieldValidator.java`
- Test: `backend/src/test/java/com/pilarestilo/product/application/CategoryVariantFieldValidatorTest.java`

**Interfaces:**
- Consumes: `CategoryRepository.findAllByIds`, `ShapeCategoryResolver.resolveOne`,
  `Category.getVariantFieldConfig`, `CategoryVariantFieldConfig.genericFallback`
  (Tasks 2-4).
- Produces: `CategoryVariantFieldValidator.resolveConfig(Set<UUID> categoryIds): CategoryVariantFieldConfig`,
  `CategoryVariantFieldValidator.validate(CategoryVariantFieldConfig config, List<ProductVariantInput> variants): void`
  (throws `DomainException`) — consumed by Task 8
  (`CreateProductUseCase`/`UpdateProductUseCase`).

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.product.application;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryVariantFieldValidatorTest {

    @Mock
    CategoryRepository categoryRepository;

    private final CategoryVariantFieldConfig.FieldConfig freeText =
            new CategoryVariantFieldConfig.FieldConfig("Color", CategoryVariantFieldConfig.InputType.FREE_TEXT,
                    List.of(), null, null, false, true);
    private final CategoryVariantFieldConfig.FieldConfig sizeOptions =
            new CategoryVariantFieldConfig.FieldConfig("Talla", CategoryVariantFieldConfig.InputType.OPTIONS,
                    List.of("S", "M", "L"), null, null, true, false);
    private final CategoryVariantFieldConfig.FieldConfig shoeRange =
            new CategoryVariantFieldConfig.FieldConfig("Numero", CategoryVariantFieldConfig.InputType.RANGE,
                    List.of(), 34, 43, true, false);

    @Test
    void resolveConfig_noCategoryIds_returnsGenericFallback() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);

        CategoryVariantFieldConfig config = validator.resolveConfig(Set.of());

        assertEquals(CategoryVariantFieldConfig.genericFallback(), config);
    }

    @Test
    void resolveConfig_oneShapeCategory_returnsItsConfig() {
        Category zapatos = shapeCategory("zapatos", new CategoryVariantFieldConfig(freeText, shoeRange));
        UUID id = UUID.randomUUID();
        when(categoryRepository.findAllByIds(Set.of(id))).thenReturn(List.of(zapatos));
        var validator = new CategoryVariantFieldValidator(categoryRepository);

        CategoryVariantFieldConfig config = validator.resolveConfig(Set.of(id));

        assertEquals(shoeRange, config.secondary());
    }

    @Test
    void validate_freeTextField_rejectsOnlyBlank() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, freeText);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "Cualquiera", 1))));
        assertThrows(DomainException.class,
                () -> validator.validate(config, List.of(new ProductVariantInput("Negro", "", 1))));
    }

    @Test
    void validate_optionsField_rejectsValueNotInList_whenCustomNotAllowed() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, sizeOptions);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "M", 1))));
        assertThrows(DomainException.class,
                () -> validator.validate(config, List.of(new ProductVariantInput("Negro", "XXL", 1))));
    }

    @Test
    void validate_optionsField_multiValue_splitsOnHyphenAndValidatesEachToken() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, sizeOptions);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "S-M-L", 1))));
        assertThrows(DomainException.class,
                () -> validator.validate(config, List.of(new ProductVariantInput("Negro", "S-XXL", 1))));
    }

    @Test
    void validate_optionsField_multiValue_rejectsDuplicateToken() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, sizeOptions);

        assertThrows(DomainException.class,
                () -> validator.validate(config, List.of(new ProductVariantInput("Negro", "S-S", 1))));
    }

    @Test
    void validate_singleValueField_doesNotSplitOnHyphen() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var singleValueFreeText = new CategoryVariantFieldConfig.FieldConfig(
                "Color", CategoryVariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true);
        var config = new CategoryVariantFieldConfig(singleValueFreeText, freeText);

        // "Azul-Marino" is one color, not two tokens, because primary.allowMultiple() is false.
        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Azul-Marino", "Cualquiera", 1))));
    }

    @Test
    void validate_rangeField_acceptsWithinBoundsAndRejectsOutside() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var config = new CategoryVariantFieldConfig(freeText, shoeRange);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Blanco", "38", 1))));
        assertThrows(DomainException.class,
                () -> validator.validate(config, List.of(new ProductVariantInput("Blanco", "50", 1))));
        assertThrows(DomainException.class,
                () -> validator.validate(config, List.of(new ProductVariantInput("Blanco", "not-a-number", 1))));
    }

    @Test
    void validate_optionsField_allowsCustomValueWhenConfigured() {
        var validator = new CategoryVariantFieldValidator(categoryRepository);
        var customAllowed = new CategoryVariantFieldConfig.FieldConfig(
                "Talla", CategoryVariantFieldConfig.InputType.OPTIONS, List.of("S", "M", "L"), null, null, true, true);
        var config = new CategoryVariantFieldConfig(freeText, customAllowed);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "XXL-a-medida", 1))));
    }

    private static Category shapeCategory(String slug, CategoryVariantFieldConfig config) {
        Category c = Category.create(slug, slug, slug, null, 0, null);
        c.updateVariantFieldConfig(true, config);
        return c;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -o test -Dtest=CategoryVariantFieldValidatorTest`
Expected: FAIL — `CategoryVariantFieldValidator` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.pilarestilo.product.application;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.model.ShapeCategoryResolver;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the one shape category (if any) among a product's assigned categories and
 * validates submitted variant values against its field config -- write time only. See
 * ProductSizeRules for why this must never run on read.
 */
@Component
public class CategoryVariantFieldValidator {

    private final CategoryRepository categoryRepository;

    public CategoryVariantFieldValidator(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public CategoryVariantFieldConfig resolveConfig(Set<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return CategoryVariantFieldConfig.genericFallback();
        }
        List<Category> categories = categoryRepository.findAllByIds(categoryIds);
        return ShapeCategoryResolver.resolveOne(categories)
                .map(Category::getVariantFieldConfig)
                .orElseGet(CategoryVariantFieldConfig::genericFallback);
    }

    public void validate(CategoryVariantFieldConfig config, List<ProductVariantInput> variants) {
        if (variants == null) return;
        for (ProductVariantInput variant : variants) {
            validateField(config.primary(), variant.color());
            validateField(config.secondary(), variant.size());
        }
    }

    private void validateField(CategoryVariantFieldConfig.FieldConfig field, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isBlank()) {
            throw new DomainException(field.label() + " cannot be blank");
        }
        List<String> tokens = field.allowMultiple() ? List.of(value.split("-")) : List.of(value);
        Set<String> seen = new HashSet<>();
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isBlank()) {
                throw new DomainException(field.label() + ": empty value in combined field");
            }
            if (!seen.add(token.toLowerCase())) {
                throw new DomainException(field.label() + ": duplicated value " + token);
            }
            validateToken(field, token);
        }
    }

    private void validateToken(CategoryVariantFieldConfig.FieldConfig field, String token) {
        switch (field.inputType()) {
            case FREE_TEXT -> { /* non-blank already checked above */ }
            case OPTIONS -> {
                boolean inList = field.options().stream().anyMatch(option -> option.equalsIgnoreCase(token));
                if (!inList && !field.allowCustom()) {
                    throw new DomainException(field.label() + ": " + token + " is not one of " + field.options());
                }
            }
            case RANGE -> {
                Integer number = parseIntOrNull(token);
                boolean inRange = number != null && number >= field.min() && number <= field.max();
                if (!inRange && !field.allowCustom()) {
                    throw new DomainException(field.label() + ": " + token + " is not between "
                            + field.min() + " and " + field.max());
                }
            }
        }
    }

    private static Integer parseIntOrNull(String token) {
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -o test -Dtest=CategoryVariantFieldValidatorTest`
Expected: PASS, 10/10.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/product/application/CategoryVariantFieldValidator.java \
        backend/src/test/java/com/pilarestilo/product/application/CategoryVariantFieldValidatorTest.java
git commit -m "feat(product): add CategoryVariantFieldValidator (write-time only)"
```

---

## Task 8: Wire the validator into product writes; remove `variantType`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/product/domain/model/Product.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/entities/ProductEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/dto/ProductDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/mappers/ProductMapper.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/usecases/CreateProductUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/usecases/UpdateProductUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/CreateProductRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/UpdateProductRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/web/controllers/ProductController.java`
- Test: `backend/src/test/java/com/pilarestilo/product/application/CreateProductUseCaseVariantValidationTest.java`

**Interfaces:**
- Consumes: `CategoryVariantFieldValidator.resolveConfig`/`.validate` (Task 7),
  `ShapeCategoryResolver.resolveOne` (Task 3).
- Produces: `ProductDto.variantFieldConfig` (replacing `variantType`),
  `Product.getVariantFieldConfig()`/`setResolvedVariantFieldConfig(...)` —
  consumed by Task 14 (frontend `ProductVariantSelector.tsx`).

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.product.application;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.usecases.CreateProductUseCase;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseVariantValidationTest {

    @Mock ProductRepository productRepository;
    @Mock DomainEventPublisher eventPublisher;
    @Mock CategoryRepository categoryRepository;

    @Test
    void rejects_variantValue_notAllowedByShapeCategoryConfig() {
        UUID shoesId = UUID.randomUUID();
        Category zapatos = Category.create("zapatos", "Zapatos", "Shoes", null, 0, null);
        zapatos.updateVariantFieldConfig(true, new CategoryVariantFieldConfig(
                new CategoryVariantFieldConfig.FieldConfig("Color", CategoryVariantFieldConfig.InputType.FREE_TEXT,
                        List.of(), null, null, false, true),
                new CategoryVariantFieldConfig.FieldConfig("Numero", CategoryVariantFieldConfig.InputType.RANGE,
                        List.of(), 34, 43, true, false)));
        when(categoryRepository.findAllByIds(Set.of(shoesId))).thenReturn(List.of(zapatos));

        CategoryVariantFieldValidator validator = new CategoryVariantFieldValidator(categoryRepository);
        CreateProductUseCase useCase = new CreateProductUseCase(productRepository, eventPublisher, validator);

        assertThrows(DomainException.class, () -> useCase.execute(
                "Zapato", "desc", BigDecimal.valueOf(50000), "CLP", null, null,
                "http://img", "NEW", "Marca", 0, true, Set.of(shoesId),
                List.of(new ProductVariantInput("Blanco", "50", 1))
        ));
    }

    @Test
    void accepts_variantValue_withinShapeCategoryConfig() {
        UUID shoesId = UUID.randomUUID();
        Category zapatos = Category.create("zapatos", "Zapatos", "Shoes", null, 0, null);
        zapatos.updateVariantFieldConfig(true, new CategoryVariantFieldConfig(
                new CategoryVariantFieldConfig.FieldConfig("Color", CategoryVariantFieldConfig.InputType.FREE_TEXT,
                        List.of(), null, null, false, true),
                new CategoryVariantFieldConfig.FieldConfig("Numero", CategoryVariantFieldConfig.InputType.RANGE,
                        List.of(), 34, 43, true, false)));
        when(categoryRepository.findAllByIds(Set.of(shoesId))).thenReturn(List.of(zapatos));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryVariantFieldValidator validator = new CategoryVariantFieldValidator(categoryRepository);
        CreateProductUseCase useCase = new CreateProductUseCase(productRepository, eventPublisher, validator);

        ProductDto dto = useCase.execute(
                "Zapato", "desc", BigDecimal.valueOf(50000), "CLP", null, null,
                "http://img", "NEW", "Marca", 0, true, Set.of(shoesId),
                List.of(new ProductVariantInput("Blanco", "38", 1))
        );

        assertNotNull(dto.id());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -o test -Dtest=CreateProductUseCaseVariantValidationTest`
Expected: FAIL — `CreateProductUseCase` has no 3-arg constructor yet.

- [ ] **Step 3: Modify `Product.java`**

Remove the `variantType` field entirely (`Product.java:3` import,
`Product.java:49` field, `Product.java:194-195` getter/setter) and add a
resolved, read-only field for the config the repository adapter computes on
load:

```java
// DELETE the import
import com.pilarestilo.category.domain.enums.CategoryType;

// DELETE the field (line 41-49) and its getter/setter (lines 194-195)

// ADD, alongside categoryTypes
private com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig variantFieldConfig;

// ADD, alongside getCategoryTypes()/setCategoryTypes()
public com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig getVariantFieldConfig() {
    return variantFieldConfig;
}
public void setVariantFieldConfig(com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig variantFieldConfig) {
    this.variantFieldConfig = variantFieldConfig;
}
```

- [ ] **Step 4: Modify `ProductEntity.java`** — remove the dead `variantType`
column mapping

```java
// DELETE the import
import com.pilarestilo.category.domain.enums.CategoryType;

// DELETE the field and its getter/setter (ProductEntity.java:76-78, 108-109)
```
(The `variant_type` column stays in the database, unread, until the future
contract migration — see Global Constraints.)

- [ ] **Step 5: Modify `ProductRepositoryAdapter.java`** — resolve the winning
shape category's config on every read

Add a constructor dependency on `CategoryRepository` (already present as a
field-free helper via `categoryJpaRepository`? check: this adapter already
injects `CategoryJpaRepository` directly for the `entity.getCategories()`
join, not the domain `CategoryRepository` port — for resolving
`ShapeCategoryResolver`, reuse the categories already loaded on
`entity.getCategories()` rather than adding a second repository dependency):

In `toDomain()` (`ProductRepositoryAdapter.java:330-347`), replace the
`categoryTypes` block with:

```java
if (entity.getCategories() != null && !entity.getCategories().isEmpty()) {
    Set<UUID> ids = entity.getCategories().stream().map(CategoryEntity::getId).collect(Collectors.toSet());
    List<String> slugs = entity.getCategories().stream().map(CategoryEntity::getSlug).sorted().toList();
    product.setCategoryIds(ids);
    product.setCategorySlugs(slugs);

    List<Category> categories = entity.getCategories().stream().map(this::toDomainCategoryForResolution).toList();
    CategoryVariantFieldConfig resolved = ShapeCategoryResolver.resolveOne(categories)
            .map(Category::getVariantFieldConfig)
            .orElseGet(CategoryVariantFieldConfig::genericFallback);
    product.setVariantFieldConfig(resolved);
}
```

Add the small local conversion helper (this adapter already has its own
`CategoryEntity` in scope via the `@ManyToMany`, so build just enough of a
`Category` for the resolver — it only reads `isDefinesVariantFields()`,
`getSlug()`, `getVariantFieldConfig()`):

```java
private Category toDomainCategoryForResolution(CategoryEntity e) {
    Category c = Category.create(e.getSlug(), e.getNameEs(), e.getNameEn(), e.getParentId(), e.getSortOrder(), e.getImageUrl());
    c.updateVariantFieldConfig(e.isDefinesVariantFields(), fromRawConfig(e.getVariantFieldConfig()));
    return c;
}
```
(`fromRawConfig` is the same private helper added to `CategoryRepositoryAdapter`
in Task 4 — since it's `private` there, duplicate a copy here, or promote it
to a small package-visible static utility if duplication bothers the
reviewer; either is fine, this plan picks duplication to avoid coupling the
`product` and `category` infrastructure packages any tighter than they
already are via the existing `CategoryEntity` import.)

Add the needed imports: `com.pilarestilo.category.domain.model.Category`,
`com.pilarestilo.category.domain.model.ShapeCategoryResolver`,
`com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig`,
`java.util.LinkedHashMap`, `java.util.Map`.

DELETE the old `variantType` block if the adapter previously read/wrote it
(confirmed in investigation it does not — `entity.getVariantType()`/
`setVariantType()` were never called, so there is nothing to delete here
beyond the `categoryTypes` computation being replaced above).

- [ ] **Step 6: Modify `ProductDto.java`/`ProductMapper.java`**

```java
// ProductDto.java: replace
String variantType,
// with
ProductVariantFieldConfigDto variantFieldConfig,
```
Add the DTO record (reuse the same shape as `CategoryDto.CategoryVariantFieldConfigDto`
but as its own type to avoid a cross-module DTO dependency):
```java
public record ProductVariantFieldConfigDto(FieldDto primary, FieldDto secondary) {
    public record FieldDto(String label, String inputType, java.util.List<String> options,
                            Integer min, Integer max, boolean allowMultiple, boolean allowCustom) {}
}
```

```java
// ProductMapper.java: replace
product.getVariantType() == null ? null : product.getVariantType().name(),
// with
toConfigDto(product.getVariantFieldConfig()),
```
Add the mapping helper in `ProductMapper`:
```java
private static ProductDto.ProductVariantFieldConfigDto toConfigDto(
        com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig config) {
    if (config == null) return null;
    return new ProductDto.ProductVariantFieldConfigDto(toFieldDto(config.primary()), toFieldDto(config.secondary()));
}
private static ProductDto.ProductVariantFieldConfigDto.FieldDto toFieldDto(
        com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig.FieldConfig field) {
    return new ProductDto.ProductVariantFieldConfigDto.FieldDto(field.label(), field.inputType().name(),
            field.options(), field.min(), field.max(), field.allowMultiple(), field.allowCustom());
}
```

- [ ] **Step 7: Modify `CreateProductUseCase.java`/`UpdateProductUseCase.java`**

Add the `CategoryVariantFieldValidator` dependency, drop `variantType`/
`parseVariantType` entirely, and call the validator before `setVariants`:

```java
// constructor: add the new dependency
private final CategoryVariantFieldValidator variantFieldValidator;

public CreateProductUseCase(ProductRepository productRepository, DomainEventPublisher eventPublisher,
                             CategoryVariantFieldValidator variantFieldValidator) {
    this.productRepository = productRepository;
    this.eventPublisher = eventPublisher;
    this.variantFieldValidator = variantFieldValidator;
}
```

Collapse the three `execute(...)` overloads to two (drop the `variantType`
parameter and its trailing overload entirely):
```java
@SuppressWarnings({"java:S6809", "java:S107"})
@Transactional
public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                           BigDecimal listPriceAmount, String listPriceCurrency,
                           String imageUrl, String condition, String brand, int stock,
                           Boolean active, Set<UUID> categoryIds) {
    return execute(name, description, priceAmount, priceCurrency, listPriceAmount,
            listPriceCurrency, imageUrl, condition, brand, stock, active, categoryIds, null);
}

@SuppressWarnings("java:S107")
@Transactional
public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                           BigDecimal listPriceAmount, String listPriceCurrency,
                           String imageUrl, String condition, String brand, int stock,
                           Boolean active, Set<UUID> categoryIds,
                           List<ProductVariantInput> variants) {
    Money price = Money.of(priceAmount, priceCurrency == null || priceCurrency.isBlank()
            ? Money.DEFAULT_CURRENCY : priceCurrency);
    Money listPrice = null;
    if (listPriceAmount != null) {
        String resolvedListCurrency = listPriceCurrency == null || listPriceCurrency.isBlank()
                ? price.currency() : listPriceCurrency;
        listPrice = Money.of(listPriceAmount, resolvedListCurrency);
    }
    ProductCondition productCondition = ProductCondition.valueOf(condition);

    Product product = Product.create(name, description, price, imageUrl, productCondition, brand, stock, listPrice);
    if (active != null) {
        product.setActive(active);
    }
    if (categoryIds != null && !categoryIds.isEmpty()) {
        product.setCategoryIds(categoryIds);
    }
    if (variants != null) {
        CategoryVariantFieldConfig config = variantFieldValidator.resolveConfig(product.getCategoryIds());
        variantFieldValidator.validate(config, variants);
        product.setVariants(variants.stream().map(this::toVariant).toList());
    }
    Product saved = productRepository.save(product);

    eventPublisher.publish(new ProductCreated(saved.getId(), saved.getName()));

    return ProductMapper.toDto(saved);
}

private ProductVariant toVariant(ProductVariantInput input) {
    try {
        return new ProductVariant(input.color(), input.size(), input.stock());
    } catch (DomainException _) {
        throw new DomainException("Invalid product variant size: " + input.size());
    }
}
```

Delete `parseVariantType(...)` and its `CategoryType` import entirely.
Apply the mirror-image change to `UpdateProductUseCase.java` (same
constructor addition, same overload collapse, same
`resolveConfig`/`validate` call before `product.setVariants(...)`, same
deletion of `parseVariantType`).

- [ ] **Step 8: Modify `CreateProductRequest.java`/`UpdateProductRequest.java`/`ProductController.java`**

```java
// both request records: DELETE
/** Null or blank leaves it derived from the categories, as it was before V69. */
String variantType,
```

```java
// ProductController.create(): DELETE the trailing request.variantType() argument
ProductDto dto = createProductUseCase.execute(
        request.name(), request.description(), request.priceAmount(), request.priceCurrency(),
        request.listPriceAmount(), request.listPriceCurrency(),
        request.imageUrl(), request.condition(), request.brand(), request.stock(),
        request.active(), request.categoryIds(), toVariantInputs(request.variants())
);
```

```java
// ProductController.update(): same deletion
return updateProductUseCase.execute(
        id, request.name(), request.description(), request.priceAmount(), request.priceCurrency(),
        request.listPriceAmount(), request.listPriceCurrency(),
        request.imageUrl(), request.condition(), request.brand(),
        request.stock(), request.active(), request.categoryIds(), toVariantInputs(request.variants())
);
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd backend && mvn -o test -Dtest=CreateProductUseCaseVariantValidationTest,CreateProductUseCaseTest,ProductSizeRulesTest,CategoryVariantFieldValidatorTest`
Expected: PASS, all green.

- [ ] **Step 10: Run the full backend unit suite**

Run: `cd backend && mvn -o test`
Expected: BUILD SUCCESS. Fix any remaining compile error from a test or
call site still passing the now-removed `variantType` argument (grep
`grep -rn "\.execute(.*variantType\|new ProductVariantInput.*variantType" backend/src`).

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/product/domain/model/Product.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/entities/ProductEntity.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductRepositoryAdapter.java \
        backend/src/main/java/com/pilarestilo/product/application/dto/ProductDto.java \
        backend/src/main/java/com/pilarestilo/product/application/mappers/ProductMapper.java \
        backend/src/main/java/com/pilarestilo/product/application/usecases/CreateProductUseCase.java \
        backend/src/main/java/com/pilarestilo/product/application/usecases/UpdateProductUseCase.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/CreateProductRequest.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/UpdateProductRequest.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/web/controllers/ProductController.java \
        backend/src/test/java/com/pilarestilo/product/application/CreateProductUseCaseVariantValidationTest.java
git commit -m "feat(product): enforce category variant field config at write time; remove dead variantType override"
```

---

## Task 9: `product-service` — port the same resolution logic

**Files:**
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/persistence/CategoryEntity.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/persistence/ProductEntity.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/web/dto/ProductDto.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/web/ProductMapper.java`
- Modify: `services/product-service/src/test/java/com/pilarestilo/productservice/web/ProductMapperTest.java`

**Interfaces:**
- Produces: the same `variantFieldConfig` JSON shape as the monolith's
  `ProductDto.ProductVariantFieldConfigDto` — the frontend must not be able
  to tell which backend answered.

- [ ] **Step 1: Write the failing test**

Add to `ProductMapperTest.java`, following its existing `ReflectionTestUtils`
fixture-building style exactly:

```java
@Test
void maps_variantFieldConfig_fromTheOneShapeCategoryAmongAssigned() {
    ProductEntity entity = new ProductEntity();
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(entity, "name", "Zapato");
    ReflectionTestUtils.setField(entity, "description", "desc");
    ReflectionTestUtils.setField(entity, "priceAmount", new BigDecimal("50000.00"));
    ReflectionTestUtils.setField(entity, "priceCurrency", "CLP");
    ReflectionTestUtils.setField(entity, "imageUrl", "https://img");
    ReflectionTestUtils.setField(entity, "condition", "NUEVO");
    ReflectionTestUtils.setField(entity, "brand", "Marca");
    ReflectionTestUtils.setField(entity, "stock", 1);
    ReflectionTestUtils.setField(entity, "active", true);
    ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
    ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
    ReflectionTestUtils.setField(entity, "avgRating", BigDecimal.ZERO);
    ReflectionTestUtils.setField(entity, "reviewCount", 0);
    ReflectionTestUtils.setField(entity, "shippingOriginZone", "LOCAL");
    ReflectionTestUtils.setField(entity, "sizeStocks", List.of());
    ReflectionTestUtils.setField(entity, "variants", List.of());

    CategoryEntity mujer = new CategoryEntity();
    ReflectionTestUtils.setField(mujer, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(mujer, "slug", "mujer");
    ReflectionTestUtils.setField(mujer, "categoryType", "GENERIC");
    ReflectionTestUtils.setField(mujer, "definesVariantFields", false);
    ReflectionTestUtils.setField(mujer, "variantFieldConfig", null);

    CategoryEntity zapatos = new CategoryEntity();
    ReflectionTestUtils.setField(zapatos, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(zapatos, "slug", "zapatos");
    ReflectionTestUtils.setField(zapatos, "categoryType", "SHOES");
    ReflectionTestUtils.setField(zapatos, "definesVariantFields", true);
    ReflectionTestUtils.setField(zapatos, "variantFieldConfig", Map.of(
            "primary", Map.of("label", "Color", "inputType", "FREE_TEXT", "options", List.of(),
                    "allowMultiple", false, "allowCustom", true),
            "secondary", Map.of("label", "Numero", "inputType", "RANGE", "options", List.of(),
                    "min", 34, "max", 43, "allowMultiple", true, "allowCustom", true)
    ));
    ReflectionTestUtils.setField(entity, "categories", Set.of(mujer, zapatos));

    ProductDto dto = ProductMapper.toDto(entity);

    assertEquals("Numero", dto.variantFieldConfig().secondary().label());
    assertEquals("RANGE", dto.variantFieldConfig().secondary().inputType());
    assertEquals(34, dto.variantFieldConfig().secondary().min());
    assertEquals(43, dto.variantFieldConfig().secondary().max());
}

@Test
void maps_variantFieldConfig_fallsBackToGenericWhenNoShapeCategory() {
    ProductEntity entity = new ProductEntity();
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(entity, "name", "Panuelo");
    ReflectionTestUtils.setField(entity, "description", "desc");
    ReflectionTestUtils.setField(entity, "priceAmount", new BigDecimal("10000.00"));
    ReflectionTestUtils.setField(entity, "priceCurrency", "CLP");
    ReflectionTestUtils.setField(entity, "imageUrl", "https://img");
    ReflectionTestUtils.setField(entity, "condition", "NUEVO");
    ReflectionTestUtils.setField(entity, "brand", "Marca");
    ReflectionTestUtils.setField(entity, "stock", 1);
    ReflectionTestUtils.setField(entity, "active", true);
    ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
    ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
    ReflectionTestUtils.setField(entity, "avgRating", BigDecimal.ZERO);
    ReflectionTestUtils.setField(entity, "reviewCount", 0);
    ReflectionTestUtils.setField(entity, "shippingOriginZone", "LOCAL");
    ReflectionTestUtils.setField(entity, "sizeStocks", List.of());
    ReflectionTestUtils.setField(entity, "variants", List.of());

    CategoryEntity mujer = new CategoryEntity();
    ReflectionTestUtils.setField(mujer, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(mujer, "slug", "mujer");
    ReflectionTestUtils.setField(mujer, "categoryType", "GENERIC");
    ReflectionTestUtils.setField(mujer, "definesVariantFields", false);
    ReflectionTestUtils.setField(mujer, "variantFieldConfig", null);
    ReflectionTestUtils.setField(entity, "categories", Set.of(mujer));

    ProductDto dto = ProductMapper.toDto(entity);

    assertEquals("Variante", dto.variantFieldConfig().primary().label());
    assertEquals("Detalle", dto.variantFieldConfig().secondary().label());
    assertEquals("FREE_TEXT", dto.variantFieldConfig().primary().inputType());
}
```
Add imports to the test file: `java.util.Map`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/product-service && mvn -o test -Dtest=ProductMapperTest`
Expected: FAIL — `CategoryEntity.definesVariantFields`/`variantFieldConfig`
fields don't exist, `ProductDto.variantFieldConfig()` doesn't exist.

- [ ] **Step 3: Modify `CategoryEntity.java`** — add the two columns

```java
// add imports
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

// add fields, alongside categoryType
@Column(name = "defines_variant_fields", nullable = false)
private boolean definesVariantFields;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "variant_field_config", columnDefinition = "jsonb")
private Map<String, Object> variantFieldConfig;

// add getters, matching the existing getter-only style of this class
// (this module never writes categories, only reads them, so no setters needed)
public boolean isDefinesVariantFields() {
    return definesVariantFields;
}

public Map<String, Object> getVariantFieldConfig() {
    return variantFieldConfig;
}
```

- [ ] **Step 4: Modify `ProductEntity.java`** — remove the dead `variantType`
field (mirrors the monolith's finding: this module's `ProductMapper` reads
`entity.getVariantType()` today, but once the mapper is rewritten in Step 6
nothing will call it)

```java
// DELETE field (line 83) and its getter/setter (lines 181-187)
private String variantType;
public String getVariantType() { return variantType; }
public void setVariantType(String variantType) { this.variantType = variantType; }
```

- [ ] **Step 5: Modify `ProductDto.java`**

```java
// DELETE
List<String> categoryTypes,
/** Null when unstated; the storefront then derives it from categoryTypes. */
String variantType,
// REPLACE WITH
ProductVariantFieldConfigDto variantFieldConfig,
```
Add the nested DTO (same shape as the monolith's
`ProductDto.ProductVariantFieldConfigDto`, Task 8 Step 6 — must match
byte-for-byte since the frontend consumes whichever backend answers):
```java
public record ProductVariantFieldConfigDto(FieldDto primary, FieldDto secondary) {
    public record FieldDto(String label, String inputType, List<String> options,
                            Integer min, Integer max, boolean allowMultiple, boolean allowCustom) {}
}
```
(`categorySlugs` stays — only `categoryTypes`/`variantType` are removed.)

- [ ] **Step 6: Modify `ProductMapper.java`**

```java
package com.pilarestilo.productservice.web;

import com.pilarestilo.productservice.persistence.CategoryEntity;
import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.web.dto.ProductDto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ProductMapper {

    private ProductMapper() {}

    static ProductDto toDto(ProductEntity entity) {
        List<ProductDto.SizeStockDto> sizeStocks = entity.getSizeStocks().stream()
                .map(s -> new ProductDto.SizeStockDto(s.getSize(), s.getStock()))
                .toList();

        List<ProductDto.VariantDto> variants = entity.getVariants().stream()
                .map(v -> new ProductDto.VariantDto(
                        v.getColor(), v.getSize(), v.getStockOnHand(),
                        v.getStockOnHand(), v.getStockReserved(), v.available()))
                .toList();

        List<String> categorySlugs = entity.getCategories().stream()
                .map(CategoryEntity::getSlug)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new ProductDto(
                entity.getId(), entity.getName(), entity.getDescription(),
                entity.getPriceAmount(), entity.getPriceCurrency(),
                entity.getListPriceAmount(), entity.getListPriceCurrency(),
                entity.getImageUrl(), entity.getCondition(), entity.getBrand(),
                entity.getStock(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getAvgRating(), entity.getReviewCount(), entity.getShippingOriginZone(),
                sizeStocks, categorySlugs,
                resolveVariantFieldConfig(entity.getCategories()),
                variants
        );
    }

    /**
     * The same "at most one shape category" rule as the monolith's
     * ShapeCategoryResolver, reimplemented here because this is a separate
     * deployable sharing no code with it -- see product/domain/model/ShapeCategoryResolver.java
     * in the monolith for the canonical version this must stay behaviorally
     * identical to.
     */
    private static ProductDto.ProductVariantFieldConfigDto resolveVariantFieldConfig(java.util.Set<CategoryEntity> categories) {
        List<CategoryEntity> shapeCategories = categories.stream().filter(CategoryEntity::isDefinesVariantFields).toList();
        if (shapeCategories.isEmpty()) {
            return genericFallback();
        }
        // product-service only reads; it never creates/updates products, so an
        // already-invalid 2+-shape-category product (which the monolith's write
        // path now rejects going forward) is read here defensively rather than
        // thrown on -- picking the first is a display-only tie-break for data
        // that predates this feature, not a new rule.
        Optional<CategoryEntity> resolved = shapeCategories.stream().findFirst();
        return toConfigDto(resolved.map(CategoryEntity::getVariantFieldConfig).orElse(null));
    }

    private static ProductDto.ProductVariantFieldConfigDto genericFallback() {
        var field = new ProductDto.ProductVariantFieldConfigDto.FieldDto(
                "Variante", "FREE_TEXT", List.of(), null, null, true, true);
        var detail = new ProductDto.ProductVariantFieldConfigDto.FieldDto(
                "Detalle", "FREE_TEXT", List.of(), null, null, true, true);
        return new ProductDto.ProductVariantFieldConfigDto(field, detail);
    }

    @SuppressWarnings("unchecked")
    private static ProductDto.ProductVariantFieldConfigDto toConfigDto(Map<String, Object> raw) {
        if (raw == null) return genericFallback();
        return new ProductDto.ProductVariantFieldConfigDto(
                toFieldDto((Map<String, Object>) raw.get("primary")),
                toFieldDto((Map<String, Object>) raw.get("secondary")));
    }

    @SuppressWarnings("unchecked")
    private static ProductDto.ProductVariantFieldConfigDto.FieldDto toFieldDto(Map<String, Object> raw) {
        List<String> options = raw.get("options") == null
                ? List.of()
                : ((List<Object>) raw.get("options")).stream().map(String::valueOf).toList();
        return new ProductDto.ProductVariantFieldConfigDto.FieldDto(
                (String) raw.get("label"),
                (String) raw.get("inputType"),
                options,
                raw.get("min") == null ? null : ((Number) raw.get("min")).intValue(),
                raw.get("max") == null ? null : ((Number) raw.get("max")).intValue(),
                Boolean.TRUE.equals(raw.get("allowMultiple")),
                Boolean.TRUE.equals(raw.get("allowCustom")));
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd services/product-service && mvn -o test -Dtest=ProductMapperTest`
Expected: PASS, 3/3 (the two new tests plus the existing
`maps_entity_to_dto_with_sorted_categories_and_variants`, updated per Step 8
below since it currently asserts on the now-deleted `categoryTypes` field).

- [ ] **Step 8: Fix the pre-existing test's now-invalid assertions**

In `maps_entity_to_dto_with_sorted_categories_and_variants`, the two
`CategoryEntity` fixtures (`catA`/`catB`) need
`ReflectionTestUtils.setField(cat, "definesVariantFields", false);` and
`ReflectionTestUtils.setField(cat, "variantFieldConfig", null);` added (both
categories are grouping in that test's original setup, so they map to the
generic fallback). Replace the assertion
`assertEquals(List.of("ACCESSORY", "SHOES"), dto.categoryTypes());` with:
```java
assertEquals("Variante", dto.variantFieldConfig().primary().label());
```

- [ ] **Step 9: Run the full product-service suite**

Run: `cd services/product-service && mvn -o test`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add services/product-service/src/main/java/com/pilarestilo/productservice/persistence/CategoryEntity.java \
        services/product-service/src/main/java/com/pilarestilo/productservice/web/dto/ProductDto.java \
        services/product-service/src/main/java/com/pilarestilo/productservice/web/ProductMapper.java \
        services/product-service/src/test/java/com/pilarestilo/productservice/web/ProductMapperTest.java
git commit -m "feat(product-service): resolve variant field config, matching the monolith's contract

Ships in the same commit as the monolith's product/category changes:
product-service, not the monolith, answers GET/HEAD /api/products* under
the microservices Caddy profile, so it must independently produce the
same resolved-config shape or the storefront variant selector and the
admin form would disagree depending on which backend answered."
```

---

## Task 10: Frontend types (`api.ts`)

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Produces: `CategoryVariantFieldConfigDto`, `CategoryVariantFieldDto`,
  updated `CategoryDto`/`CategoryTreeNode`/`ProductDto`/
  `CreateCategoryRequest`/`UpdateCategoryRequest` — consumed by Task 11
  (`variantSchema.ts`).

- [ ] **Step 1: Add the new shared types** (near `CategoryType`, `api.ts:2185`)

```typescript
export type VariantFieldInputType = 'FREE_TEXT' | 'OPTIONS' | 'RANGE';

export interface CategoryVariantFieldDto {
  label: string;
  inputType: VariantFieldInputType;
  options: string[];
  min: number | null;
  max: number | null;
  allowMultiple: boolean;
  allowCustom: boolean;
}

export interface CategoryVariantFieldConfigDto {
  primary: CategoryVariantFieldDto;
  secondary: CategoryVariantFieldDto;
}
```

- [ ] **Step 2: Extend `CategoryDto`/`CategoryTreeNode`** (`api.ts:2194-2211`)

```typescript
export interface CategoryDto {
  // ...existing fields unchanged...
  definesVariantFields: boolean;
  variantFieldConfig: CategoryVariantFieldConfigDto | null;
}
```
(`CategoryTreeNode extends CategoryDto` needs no separate edit.)

- [ ] **Step 3: Extend `CreateCategoryRequest`** (`api.ts:2240-2252`) and add
an `UpdateCategoryRequest`-equivalent type if one exists separately (the
current code reuses `Partial<CreateCategoryRequest & {...}>` for update per
`updateCategory`'s signature at `api.ts:2380` — extend the base interface,
the `Partial<>` wrapper picks the new fields up automatically)

```typescript
export interface CreateCategoryRequest {
  // ...existing fields unchanged...
  definesVariantFields?: boolean;
  primary?: CategoryVariantFieldDto;
  secondary?: CategoryVariantFieldDto;
}
```

- [ ] **Step 4: Update `ProductDto`** (`api.ts:64-85`)

```typescript
export interface ProductDto {
  // ...existing fields unchanged, except:
  // DELETE: /** Stated by the admin... */ variantType?: CategoryType | null;
  variantFieldConfig?: CategoryVariantFieldConfigDto | null;
}
```

- [ ] **Step 5: Update `CreateProductRequest`** (`api.ts:601-615`) — delete
`variantType?: CategoryType;` (no replacement; it is derived server-side now,
never submitted)

- [ ] **Step 6: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: Errors at every remaining consumer of the deleted `variantType`/
`categoryType`-typed fields — these are exactly the call sites Tasks 11-14
fix. Confirm the error list matches `variantSchema.ts`, `CategoryTree.tsx`,
`ProductForm.tsx`, `ProductVariantSelector.tsx`, and
`[locale]/products/[id].astro` — no other files. If something else errors,
investigate before proceeding.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): add CategoryVariantFieldConfigDto types, drop variantType"
```

(This commit intentionally leaves the frontend non-compiling until Task 11
lands — acceptable mid-plan, not mid-deploy: this whole plan ships as one
PR/merge, per the Global Constraints' same-commit-and-deploy rule for the
backend/product-service pairing; the frontend's internal task sequence
within that same PR is free to pass through a temporarily-broken `tsc`
between tasks.)

---

## Task 11: Rewrite `variantSchema.ts` — config-driven, not enum-driven

**Files:**
- Modify: `frontend/src/lib/variantSchema.ts`
- Test: `frontend/src/lib/__tests__/variantSchema.test.ts` (new — this
  module has no direct test file today; characterize the KEPT behavior
  first, against the current enum-driven implementation, before rewriting)

**Interfaces:**
- Consumes: `CategoryVariantFieldConfigDto`, `CategoryDto` (Task 10).
- Produces: `buildVariantSchema(config: CategoryVariantFieldConfigDto | null): VariantSchema`,
  `resolveVariantFieldConfig(params: {categoryIds: string[]; categories: CategoryDto[]}): CategoryVariantFieldConfigDto`,
  `allowedCategoryIdsFor(categories: CategoryDto[], selectedShapeCategoryId: string | null): Set<string>`
  — all other exports below are **unchanged in signature**, only their
  internals change from enum lookup to config-driven — consumed by Tasks 12,
  13, 14.

- [ ] **Step 1: Write characterization tests against the CURRENT implementation**

```typescript
import { describe, expect, it } from 'vitest';
import {
  getPrimaryAttribute,
  getSecondaryAttribute,
  createEmptyVariantSelections,
  normalizeAttributeValues,
  selectionsToLegacyVariant,
  legacyVariantToSelections,
  toVariantAttributeRecord,
  summarizeVariantAttributeValues,
  getVariantSchema,
} from '../variantSchema';

/**
 * Characterization tests written before rewriting this module from a fixed
 * CategoryType-keyed lookup table to a config-driven one (Sonar-adjacent
 * work is not the reason here -- correctness of the multi-value/custom-value
 * composition logic, which the rewrite must not regress, is). These run
 * against the CURRENT (enum-driven) implementation first.
 */
describe('variantSchema: multi-value composition (must survive the config-driven rewrite)', () => {
  it('composes multiple selected clothing sizes into one hyphen-joined stored value', () => {
    const schema = getVariantSchema('CLOTHING');
    const secondary = getSecondaryAttribute(schema);
    const selections = { [secondary.code]: ['S', 'M', 'L'] };
    const variant = selectionsToLegacyVariant(
      { [getPrimaryAttribute(schema).code]: ['Negro'], ...selections },
      3,
      schema,
    );
    expect(variant.size).toBe('S-M-L');
  });

  it('parses a stored hyphen-joined value back into separate selections', () => {
    const schema = getVariantSchema('CLOTHING');
    const secondary = getSecondaryAttribute(schema);
    const selections = legacyVariantToSelections(
      { color: 'Negro', size: 'S-M-L', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      schema,
    );
    expect(selections[secondary.code].sort()).toEqual(['L', 'M', 'S']);
  });

  it('accepts a custom value alongside the fixed options list', () => {
    const schema = getVariantSchema('CLOTHING');
    const secondary = getSecondaryAttribute(schema);
    const normalized = normalizeAttributeValues(secondary, ['Talla especial']);
    expect(normalized).toEqual(['Talla especial']);
  });

  it('defaults to UNICO for the generic pair when nothing is selected', () => {
    const schema = getVariantSchema('GENERIC');
    const selections = createEmptyVariantSelections(schema);
    const secondary = getSecondaryAttribute(schema);
    expect(selections[secondary.code]).toEqual(['UNICO']);
  });

  it('summarizes multiple variants secondary values, deduped and sorted', () => {
    const schema = getVariantSchema('CLOTHING');
    const secondary = getSecondaryAttribute(schema);
    const summary = summarizeVariantAttributeValues(
      [
        { color: 'Negro', size: 'M', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
        { color: 'Rojo', size: 'S-L', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      ],
      schema,
      secondary.code,
    );
    expect(summary).toBe('S / M / L');
  });

  it('round-trips a single-value field (color) without splitting on hyphen', () => {
    const schema = getVariantSchema('CLOTHING');
    const record = toVariantAttributeRecord(
      { color: 'Azul-Marino', size: 'M', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      schema,
    );
    expect(record[getPrimaryAttribute(schema).code]).toBe('Azul-Marino');
  });
});
```

- [ ] **Step 2: Run test to verify it passes against the CURRENT implementation**

Run: `cd frontend && npx vitest run src/lib/__tests__/variantSchema.test.ts`
Expected: PASS, 6/6 — this locks in the behavior the rewrite must preserve
(this is the one departure from strict red-green-refactor in this plan:
these are characterization tests of *existing* behavior, so "red" is not
expected here — see the project's established pattern of writing
characterization suites before a from-enum-to-config style rewrite,
verified green against the original code first).

- [ ] **Step 3: Rewrite `variantSchema.ts`**

Delete: `CategoryType`-import-based `SCHEMAS` table, `variantAndDetail()`,
`CATEGORY_TYPE_PRIORITY`, `pickCategoryType()`, `buildCategoryDepthMap()`,
`resolvePreferredCategoryType()`, `getVariantSchema(categoryType)`,
`GROUPING_TYPES`, `SELECTABLE_VARIANT_TYPES`, `getProductVariantSchema()`,
`allowedCategoryIds()`, `listSelectableVariantSchemas()`,
`GROUPING_VARIANT_TYPES`, `SHAPE_VARIANT_TYPES`, `describeVariantType()`,
`variantFieldsOf()`, `isSelectableVariantType()`.

Keep unchanged (same signature, same body — these operate on the generic
`VariantSchema`/`CategoryAttributeDefinition` shape, not on `CategoryType`):
`optionList`, `normalizeToken`, `buildOptionIndex`, `sortAttributeValues`,
`getPrimaryAttribute`, `getSecondaryAttribute`, `getAttributeValue`,
`getAttributeValues`, `createEmptyVariantSelections`,
`normalizeAttributeValues`, `composeStoredAttributeValue`,
`parseStoredAttributeValue`, `legacyVariantToSelections`,
`selectionsToLegacyVariant`, `toVariantAttributeRecord`,
`summarizeVariantAttributeValues`, all the exported interfaces/types.

Add:

```typescript
import type { CategoryDto, CategoryVariantFieldConfigDto, CategoryVariantFieldDto, ProductVariantDto } from './api';

// ... (keep VariantAttributeOption, CategoryAttributeDefinition, VariantSchema,
//      VariantAttributeSelections, VariantAttributeRecord interfaces as-is,
//      dropping `key: CategoryType` from VariantSchema -- replace with `key: string`,
//      a stable synthetic id used only for the rebind-on-schema-change check in
//      ProductForm.tsx, e.g. the category id or 'GENERIC')

const GENERIC_FALLBACK: CategoryVariantFieldConfigDto = {
  primary: { label: 'Variante', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true },
  secondary: { label: 'Detalle', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true },
};

function fieldToAttribute(
  field: CategoryVariantFieldDto,
  code: string,
  legacyField: 'color' | 'size',
): CategoryAttributeDefinition {
  const options = field.inputType === 'OPTIONS'
    ? optionList(field.options)
    : field.inputType === 'RANGE' && field.min != null && field.max != null
      ? optionList(Array.from({ length: field.max - field.min + 1 }, (_, i) => String(field.min! + i)))
      : [];
  return {
    code,
    label: field.label,
    type: field.inputType === 'FREE_TEXT' ? 'text' : 'choice',
    options,
    required: true,
    position: code === 'primary' ? 0 : 1,
    allowMultiple: field.allowMultiple,
    allowCustom: field.allowCustom || field.inputType === 'FREE_TEXT',
    legacyField,
    summaryJoiner: field.allowMultiple ? '-' : undefined,
  };
}

/** Builds the two-attribute schema a category's (or the generic fallback's) config describes. */
export function buildVariantSchema(config: CategoryVariantFieldConfigDto | null, key = 'GENERIC'): VariantSchema {
  const resolved = config ?? GENERIC_FALLBACK;
  return {
    key,
    noun: 'Variante',
    title: `${resolved.primary.label} + ${resolved.secondary.label} + stock`,
    attributes: [
      fieldToAttribute(resolved.primary, 'primary', 'color'),
      fieldToAttribute(resolved.secondary, 'secondary', 'size'),
    ],
  };
}

/** The one shape category (definesVariantFields) among the given ids, or null. */
function findShapeCategory(categoryIds: string[], categories: CategoryDto[]): CategoryDto | null {
  const byId = new Map(categories.map((c) => [c.id, c]));
  const shapeCategories = categoryIds
    .map((id) => byId.get(id))
    .filter((c): c is CategoryDto => Boolean(c) && c!.definesVariantFields);
  return shapeCategories[0] ?? null;
}

/** Resolves the variant field config a set of selected category ids implies. */
export function resolveVariantFieldConfig(params: {
  categoryIds: string[];
  categories: CategoryDto[];
}): CategoryVariantFieldConfigDto {
  const shape = findShapeCategory(params.categoryIds, params.categories);
  return shape?.variantFieldConfig ?? GENERIC_FALLBACK;
}

/**
 * The categories selectable alongside the currently-resolved shape category: any
 * grouping category, the current shape category itself, or a category with a
 * qualifying descendant -- same tree-walk `allowedCategoryIds` used, adapted from
 * "matches this CategoryType" to "is this specific shape category (or a grouping)".
 */
export function allowedCategoryIdsFor(categories: CategoryDto[], selectedShapeCategoryId: string | null): Set<string> {
  const allowed = new Set<string>();
  const childrenOf = new Map<string, CategoryDto[]>();
  for (const category of categories) {
    const key = category.parentId ?? '';
    const list = childrenOf.get(key);
    if (list) list.push(category);
    else childrenOf.set(key, [category]);
  }

  const qualifiesAlone = (category: CategoryDto): boolean =>
    !category.definesVariantFields || category.id === selectedShapeCategoryId;

  const visit = (category: CategoryDto): boolean => {
    let anyDescendantAllowed = false;
    for (const child of childrenOf.get(category.id) ?? []) {
      if (visit(child)) anyDescendantAllowed = true;
    }
    const ok = qualifiesAlone(category) || anyDescendantAllowed;
    if (ok) allowed.add(category.id);
    return ok;
  };

  for (const root of childrenOf.get('') ?? []) visit(root);
  for (const category of categories) {
    if (!allowed.has(category.id) && qualifiesAlone(category)) allowed.add(category.id);
  }
  return allowed;
}
```

- [ ] **Step 4: Run the characterization test against the REWRITTEN implementation**

The characterization tests from Step 1 use `getVariantSchema('CLOTHING')`,
which no longer exists. Update them to build an equivalent config-driven
fixture instead:

```typescript
// replace every `getVariantSchema('CLOTHING')` call in the test file with:
const CLOTHING_CONFIG: CategoryVariantFieldConfigDto = {
  primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
  secondary: {
    label: 'Talla', inputType: 'OPTIONS',
    options: ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'UNICO'],
    min: null, max: null, allowMultiple: true, allowCustom: true,
  },
};
const schema = buildVariantSchema(CLOTHING_CONFIG);
// and replace every `getVariantSchema('GENERIC')` call with:
const schema = buildVariantSchema(null);
```
(Import `buildVariantSchema` and `type { CategoryVariantFieldConfigDto }`
instead of `getVariantSchema`.)

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/lib/__tests__/variantSchema.test.ts`
Expected: PASS, 6/6 — same behavior, now against the config-driven
implementation.

- [ ] **Step 6: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: errors now only in `CategoryTree.tsx`, `ProductForm.tsx`,
`ProductVariantSelector.tsx`, `[id].astro` (fixed in Tasks 12-14).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/variantSchema.ts frontend/src/lib/__tests__/variantSchema.test.ts
git commit -m "refactor(frontend): variantSchema.ts builds from category config, not CategoryType enum

Multi-value composition (hyphen-joined combined values) and custom-value
tolerance are preserved -- characterized against the original enum-driven
implementation first, verified unchanged against the rewrite."
```

---

## Task 12: `CategoryTree.tsx` admin editor

**Files:**
- Modify: `frontend/src/islands/admin/CategoryTree.tsx`
- Test: `frontend/src/islands/admin/__tests__/CategoryTree.test.tsx` (new)

**Interfaces:**
- Consumes: `buildVariantSchema`, `CategoryVariantFieldConfigDto`,
  `CategoryVariantFieldDto` (Task 11).

- [ ] **Step 1: Write characterization tests against the CURRENT implementation**

```typescript
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import CategoryTree from '../CategoryTree';
import type { CategoryTreeNode } from '../../../lib/api';

const getCategoryTree = vi.fn();
const createCategory = vi.fn();
const updateCategory = vi.fn();
const deleteCategory = vi.fn();
const reorderCategories = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getCategoryTree: (...args: unknown[]) => getCategoryTree(...args),
    createCategory: (...args: unknown[]) => createCategory(...args),
    updateCategory: (...args: unknown[]) => updateCategory(...args),
    deleteCategory: (...args: unknown[]) => deleteCategory(...args),
    reorderCategories: (...args: unknown[]) => reorderCategories(...args),
  };
});

function node(overrides: Partial<CategoryTreeNode> = {}): CategoryTreeNode {
  return {
    id: 'cat-1', parentId: null, slug: 'zapatos', nameEs: 'Zapatos', nameEn: 'Shoes',
    sortOrder: 0, active: true, featured: false, imageUrl: undefined, menuVisible: true,
    categoryType: 'SHOES', heroImageUrl: undefined,
    definesVariantFields: true,
    variantFieldConfig: {
      primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
      secondary: { label: 'Numero', inputType: 'RANGE', options: [], min: 34, max: 43, allowMultiple: true, allowCustom: true },
    },
    children: [],
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  getCategoryTree.mockResolvedValue([node()]);
});

describe('CategoryTree: variant field config editor', () => {
  it('shows the resolved field labels for a shape category', async () => {
    render(<CategoryTree />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.getByDisplayValue('Color')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Numero')).toBeInTheDocument();
  });

  it('hides the field editors when "defines variant fields" is off', async () => {
    getCategoryTree.mockResolvedValue([node({ definesVariantFields: false, variantFieldConfig: null })]);
    render(<CategoryTree />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.queryByLabelText(/etiqueta.*campo 1/i)).not.toBeInTheDocument();
  });

  it('shows a min/max range editor when the secondary field is RANGE', async () => {
    render(<CategoryTree />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.getByDisplayValue('34')).toBeInTheDocument();
    expect(screen.getByDisplayValue('43')).toBeInTheDocument();
  });

  it('submits the edited config on save', async () => {
    updateCategory.mockResolvedValue(node());
    render(<CategoryTree />);
    await screen.findByText('Zapatos');
    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    const labelInputs = screen.getAllByDisplayValue('Color');
    await userEvent.clear(labelInputs[0]);
    await userEvent.type(labelInputs[0], 'Tono');
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(updateCategory).toHaveBeenCalledWith('cat-1', expect.objectContaining({
      definesVariantFields: true,
      primary: expect.objectContaining({ label: 'Tono' }),
    }), expect.any(String));
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/CategoryTree.test.tsx`
Expected: FAIL — `getByDisplayValue('Color')` etc. don't exist against the
current `categoryType`-select-only form.

- [ ] **Step 3: Replace the `categoryType` select block** (`CategoryTree.tsx:105-126`)

Update imports (`CategoryTree.tsx:17-23`):
```typescript
import { buildVariantSchema } from '../../lib/variantSchema';
import type { CategoryVariantFieldConfigDto, CategoryVariantFieldDto, VariantFieldInputType } from '../../lib/api';
```
Delete: `describeVariantType, getVariantSchema, variantFieldsOf,
GROUPING_VARIANT_TYPES, SHAPE_VARIANT_TYPES` and the `CATEGORY_TYPE_GROUPS`
constant (`CategoryTree.tsx:44-47`).

Update `EditForm`/`EMPTY_FORM`/`fromDto` (`CategoryTree.tsx:26-56`).
**Important:** `categoryType` stays in the form and is still sent back on
save, unchanged — it has no UI control any more (the picker is gone), but it
must round-trip as inert pass-through data. The backend's
`CreateCategoryRequest`/`UpdateCategoryRequest` still validates/stores it
(Task 5 intentionally left that field and its `@Pattern` constraint alone —
the column is not dropped this plan, per Global Constraints), and
`CreateCategoryUseCase`/`UpdateCategoryUseCase` default a missing value to
`GENERIC`. If the frontend stopped sending it, every save through the new
form would silently reset an existing category's `categoryType` to
`GENERIC`, destroying data the column is deliberately being kept around to
preserve:

```typescript
type EditForm = {
  slug: string; nameEs: string; nameEn: string;
  parentId: string; sortOrder: string; imageUrl: string; active: boolean; featured: boolean;
  menuVisible: boolean; heroImageUrl: string;
  /** No UI control any more -- carried through unchanged so saving doesn't reset it. */
  categoryType: CategoryDto['categoryType'];
  definesVariantFields: boolean;
  primary: CategoryVariantFieldDto;
  secondary: CategoryVariantFieldDto;
};

const EMPTY_FIELD: CategoryVariantFieldDto = {
  label: '', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true,
};

const EMPTY_FORM: EditForm = {
  slug: '', nameEs: '', nameEn: '', parentId: '', sortOrder: '0', imageUrl: '', active: true, featured: false,
  menuVisible: true, heroImageUrl: '', categoryType: 'GENERIC',
  definesVariantFields: false, primary: EMPTY_FIELD, secondary: EMPTY_FIELD,
};

function fromDto(dto: CategoryDto): EditForm {
  return {
    slug: dto.slug, nameEs: dto.nameEs, nameEn: dto.nameEn,
    parentId: dto.parentId ?? '', sortOrder: String(dto.sortOrder),
    imageUrl: dto.imageUrl ?? '', active: dto.active, featured: dto.featured,
    menuVisible: dto.menuVisible, heroImageUrl: dto.heroImageUrl ?? '',
    categoryType: dto.categoryType,
    definesVariantFields: dto.definesVariantFields,
    primary: dto.variantFieldConfig?.primary ?? EMPTY_FIELD,
    secondary: dto.variantFieldConfig?.secondary ?? EMPTY_FIELD,
  };
}
```

Replace the "Tipo de producto" block (`CategoryTree.tsx:105-126`) with a
toggle plus two field editors. Add a small local sub-component in the same
file:

```typescript
function VariantFieldEditor({
  fieldNumber, field, onChange,
}: { readonly fieldNumber: 1 | 2; readonly field: CategoryVariantFieldDto; readonly onChange: (next: CategoryVariantFieldDto) => void }) {
  return (
    <div className="flex flex-col gap-1 border border-pe-black/10 p-2">
      <label className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-muted">
        Etiqueta campo {fieldNumber}
      </label>
      <input className={INPUT_CLASS} value={field.label}
        onChange={(e) => onChange({ ...field, label: e.target.value })} placeholder={fieldNumber === 1 ? 'Color' : 'Talla'} />
      <select className={INPUT_CLASS} value={field.inputType}
        onChange={(e) => onChange({ ...field, inputType: e.target.value as VariantFieldInputType })}>
        <option value="FREE_TEXT">Texto libre</option>
        <option value="OPTIONS">Lista de opciones</option>
        <option value="RANGE">Rango numérico</option>
      </select>
      {field.inputType === 'OPTIONS' && (
        <input className={INPUT_CLASS} value={field.options.join(', ')}
          onChange={(e) => onChange({ ...field, options: e.target.value.split(',').map((v) => v.trim()).filter(Boolean) })}
          placeholder="XS, S, M, L, XL" />
      )}
      {field.inputType === 'RANGE' && (
        <div className="flex gap-2">
          <input type="number" className={INPUT_CLASS} value={field.min ?? ''}
            onChange={(e) => onChange({ ...field, min: e.target.value === '' ? null : Number(e.target.value) })} placeholder="Min" />
          <input type="number" className={INPUT_CLASS} value={field.max ?? ''}
            onChange={(e) => onChange({ ...field, max: e.target.value === '' ? null : Number(e.target.value) })} placeholder="Max" />
        </div>
      )}
      <label className="inline-flex items-center gap-1.5 font-sans text-[0.68rem] text-pe-charcoal">
        <input type="checkbox" checked={field.allowMultiple}
          onChange={(e) => onChange({ ...field, allowMultiple: e.target.checked })} />
        Permitir combinar varios valores en una variante
      </label>
      {field.inputType !== 'FREE_TEXT' && (
        <label className="inline-flex items-center gap-1.5 font-sans text-[0.68rem] text-pe-charcoal">
          <input type="checkbox" checked={field.allowCustom}
            onChange={(e) => onChange({ ...field, allowCustom: e.target.checked })} />
          Permitir un valor fuera de la lista
        </label>
      )}
    </div>
  );
}
```

Replace the old select block with:
```tsx
<div className="sm:col-span-3 flex flex-col gap-2">
  <label className="inline-flex items-center gap-1.5 font-sans text-[0.68rem] text-pe-charcoal">
    <input type="checkbox" checked={form.definesVariantFields}
      onChange={(e) => setForm((f) => ({ ...f, definesVariantFields: e.target.checked }))} />
    Esta categoría define campos de variante
  </label>
  {form.definesVariantFields && (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
      <VariantFieldEditor fieldNumber={1} field={form.primary} onChange={(next) => setForm((f) => ({ ...f, primary: next }))} />
      <VariantFieldEditor fieldNumber={2} field={form.secondary} onChange={(next) => setForm((f) => ({ ...f, secondary: next }))} />
    </div>
  )}
</div>
```

- [ ] **Step 4: Replace the tree-row badge** (`CategoryTree.tsx:273-278`)

```tsx
{node.definesVariantFields && node.variantFieldConfig && (
  <span
    className="font-sans text-[0.58rem] uppercase tracking-[0.12em] text-pe-muted bg-pe-cream px-1.5 py-0.5"
    title={`${node.variantFieldConfig.primary.label} / ${node.variantFieldConfig.secondary.label}`}
  >
    {node.variantFieldConfig.primary.label} / {node.variantFieldConfig.secondary.label}
  </span>
)}
```

- [ ] **Step 5: Update `handleSaveEdit`/`handleCreate`** (`CategoryTree.tsx:439-479`)

```typescript
// both calls: keep `categoryType: form.categoryType,` as-is (inert pass-through,
// see the fromDto note above) and add, after it:
definesVariantFields: form.definesVariantFields,
primary: form.definesVariantFields ? form.primary : undefined,
secondary: form.definesVariantFields ? form.secondary : undefined,
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/CategoryTree.test.tsx`
Expected: PASS, 4/4.

- [ ] **Step 7: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: errors remain only in `ProductForm.tsx`, `ProductVariantSelector.tsx`,
`[id].astro`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/islands/admin/CategoryTree.tsx frontend/src/islands/admin/__tests__/CategoryTree.test.tsx
git commit -m "feat(admin): replace category-type picker with free-text variant field editor"
```

---

## Task 13: `ProductForm.tsx` — consume config-derived schema, drop the per-product override picker

**Files:**
- Modify: `frontend/src/islands/admin/ProductForm.tsx`
- Test: `frontend/src/islands/admin/__tests__/ProductForm.test.tsx` (extend
  if it exists — check first; if not, create with characterization tests
  against the current behavior before touching the file, per this session's
  established discipline)

**Interfaces:**
- Consumes: `buildVariantSchema`, `resolveVariantFieldConfig`,
  `allowedCategoryIdsFor` (Task 11).

- [ ] **Step 1: Check for an existing test file and its current pass state**

Run: `ls frontend/src/islands/admin/__tests__/ProductForm.test.tsx 2>&1`
If it exists, run it now and read it fully before changing anything —
its current assertions about `variantType`/the "Tipo de variante" picker
must be accounted for (removed or rewritten) in Step 5 below, not left
silently broken. If it does not exist, write a characterization suite
covering at minimum: rendering the variant row editor for a product in a
SHOES-equivalent category (label "Numero", range 34-43), rendering it for a
product in no shape category (generic "Variante"/"Detalle" fallback), and
adding/removing a variant row — run it against the CURRENT implementation
first and confirm it passes before the rewrite.

- [ ] **Step 2: Update imports** (`ProductForm.tsx:1-35`)

```typescript
import {
  assignHeroModelFromProduct, createProduct, updateProduct, getCategories, getSystemSettings,
  inferSingleProductAi, transformSingleProductAiImage,
  type ProductDto, type CreateProductRequest, type CategoryDto, type ProductVariantDto,
} from '../../lib/api';
import ImageDropzone from './ImageDropzone';
import {
  createEmptyVariantSelections, getAttributeValue, getAttributeValues,
  getPrimaryAttribute, getSecondaryAttribute, buildVariantSchema, resolveVariantFieldConfig,
  allowedCategoryIdsFor, legacyVariantToSelections, normalizeAttributeValues,
  selectionsToLegacyVariant,
  type CategoryAttributeDefinition, type VariantAttributeSelections, type VariantSchema,
} from '../../lib/variantSchema';
```
(Deletes `CategoryType`, `allowedCategoryIds`, `getVariantSchema`,
`isSelectableVariantType`, `listSelectableVariantSchemas`,
`resolvePreferredCategoryType`.)

- [ ] **Step 3: Delete the "Tipo de variante" `<select>` block entirely**
(`ProductForm.tsx:1152-1189`, the whole `<div>` containing
`id="product-variant-type"` through its trailing `</div>` and helper `<p>`)
— no per-product override exists any more.

- [ ] **Step 4: Replace the memoized schema resolution** (`ProductForm.tsx:459-486`)

```typescript
const resolvedConfig = useMemo(
  () => resolveVariantFieldConfig({ categoryIds: selectedCatIds, categories }),
  [selectedCatIds, categories],
);
/*
 * Computed once for the whole tree rather than per node: the rule walks
 * descendants, so asking each node in isolation would rewalk the same
 * branches on every render.
 */
const resolvedShapeCategoryId = useMemo(() => {
  const byId = new Map(categories.map((c) => [c.id, c]));
  return selectedCatIds.find((id) => byId.get(id)?.definesVariantFields) ?? null;
}, [selectedCatIds, categories]);
const allowedCatIds = useMemo(
  () => allowedCategoryIdsFor(categories, resolvedShapeCategoryId),
  [categories, resolvedShapeCategoryId]
);
const variantSchema = useMemo(
  () => buildVariantSchema(resolvedConfig, resolvedShapeCategoryId ?? 'GENERIC'),
  [resolvedConfig, resolvedShapeCategoryId]
);
const primaryAttribute = useMemo(() => getPrimaryAttribute(variantSchema), [variantSchema]);
const secondaryAttribute = useMemo(() => getSecondaryAttribute(variantSchema), [variantSchema]);
```
(Deletes `resolvedCategoryType` and `effectiveVariantType` entirely — no
replacement needed elsewhere, since every remaining consumer already reads
`variantSchema`/`primaryAttribute`/`secondaryAttribute`/`allowedCatIds`, not
the removed intermediate values.)

- [ ] **Step 5: Remove `variantType` from `form` state, `EMPTY_FORM`, and
every load/save path**

```typescript
// EMPTY_FORM (ProductForm.tsx:229): DELETE the line
variantType: '' as CategoryType | '',

// the two useEffect blocks seeding `nextForm`/`snapshotForm` from an existing
// product (ProductForm.tsx:599, 667): DELETE the line
variantType: (product.variantType ?? '') as CategoryType | '',

// the submit handler building the request payload (ProductForm.tsx:834): DELETE the line
variantType: form.variantType || undefined,
```

- [ ] **Step 6: Fix the `CategoryTreeItem` prop** (`ProductForm.tsx:81-100`,
`151-155`, `189-201`)

```typescript
// change the prop type and the two call sites from `variantType: CategoryType`
// to a plain label used only for the locked-tooltip text:
readonly lockedHint: string;
// ...
title={locked ? `No aplica a ${lockedHint}` : undefined}
// ...
lockedHint={variantSchema.title}
```
(`variantSchema.title` is already the human-readable "Color + Talla + stock"
string `buildVariantSchema` produces — reusing it here avoids inventing a
new noun-per-shape string now that there is no fixed enum of shapes to name.)

- [ ] **Step 7: Run the test suite**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/ProductForm.test.tsx`
Expected: PASS — fix any assertion that referenced the deleted "Tipo de
variante" picker or `variantType` field before considering this done.

- [ ] **Step 8: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: errors remain only in `ProductVariantSelector.tsx`, `[id].astro`.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/islands/admin/ProductForm.tsx frontend/src/islands/admin/__tests__/ProductForm.test.tsx
git commit -m "feat(admin): ProductForm consumes config-derived variant schema, drops per-product override picker"
```

---

## Task 14: `ProductVariantSelector.tsx` + product page prop

**Files:**
- Modify: `frontend/src/islands/product/ProductVariantSelector.tsx`
- Modify: `frontend/src/pages/[locale]/products/[id].astro`
- Test: `frontend/src/islands/product/__tests__/ProductVariantSelector.test.tsx`
  (check first whether one exists; extend or create with characterization
  tests against current behavior before rewriting, matching this session's
  established discipline)

**Interfaces:**
- Consumes: `buildVariantSchema` (Task 11), `ProductDto.variantFieldConfig`
  (Task 8/10).

- [ ] **Step 1: Check for an existing test and characterize current behavior**

Run: `ls frontend/src/islands/product/__tests__/ProductVariantSelector.test.tsx 2>&1`
If absent, write and verify-green (against the CURRENT implementation) at
least: rendering primary/secondary labels for a CLOTHING-equivalent
`categoryTypes` prop, and selecting a variant enabling "Add to cart".

- [ ] **Step 2: Update the prop and schema resolution**

```typescript
// imports
import type { CategoryVariantFieldConfigDto, ProductVariantDto } from '../../lib/api';
import { buildVariantSchema, getPrimaryAttribute, getSecondaryAttribute,
  summarizeVariantAttributeValues, toVariantAttributeRecord } from '../../lib/variantSchema';

// Props interface: replace
readonly categoryTypes?: CategoryType[];
// with
readonly variantFieldConfig?: CategoryVariantFieldConfigDto | null;

// component: replace
const schema = useMemo(() => getProductVariantSchema({ categoryTypes }), [categoryTypes]);
// with
const schema = useMemo(() => buildVariantSchema(variantFieldConfig ?? null), [variantFieldConfig]);
```
(Update the destructured prop name in the function signature from
`categoryTypes` to `variantFieldConfig` to match.)

- [ ] **Step 3: Update the page passing this prop**

```astro
<!-- [id].astro:179, replace -->
categoryTypes={product.categoryTypes}
<!-- with -->
variantFieldConfig={product.variantFieldConfig}
```

- [ ] **Step 4: Run the test suite**

Run: `cd frontend && npx vitest run src/islands/product/__tests__/ProductVariantSelector.test.tsx`
Expected: PASS (after adjusting fixtures from `categoryTypes: ['CLOTHING']`
to an equivalent `variantFieldConfig` object, same shape as Task 11's
`CLOTHING_CONFIG` fixture).

- [ ] **Step 5: Type-check the whole frontend**

Run: `cd frontend && npx tsc --noEmit`
Expected: clean, zero errors.

- [ ] **Step 6: Run the full frontend test suite**

Run: `cd frontend && npx vitest run`
Expected: all green — this is the first point in the plan where every
frontend file compiles and every existing test (not just the ones touched
by this feature) is verified together.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/islands/product/ProductVariantSelector.tsx \
        "frontend/src/pages/[locale]/products/[id].astro" \
        frontend/src/islands/product/__tests__/ProductVariantSelector.test.tsx
git commit -m "feat(storefront): ProductVariantSelector consumes resolved variantFieldConfig"
```

---

## Task 15: End-to-end verification against the real Docker stack

**Files:** none (verification only)

- [ ] **Step 1: Run the full backend suite (unit + integration)**

Run: `cd backend && mvn -o clean verify`
Expected: BUILD SUCCESS — this is what actually exercises the JSONB
round-trip via Testcontainers, not the `mvn test` runs in earlier tasks.
Stop every Docker Compose profile first (Testcontainers fights a running
stack) per this repo's established rule.

- [ ] **Step 2: Run the product-service suite**

Run: `cd services/product-service && mvn -o clean verify`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Rebuild and bring up the full stack, including `microservices`**

Run:
```bash
cd infra && docker compose --env-file .env \
  --profile kafka --profile cache --profile microservices \
  --profile observability --profile tracing up -d --build
```
Confirm every container reports healthy: `docker ps`.

- [ ] **Step 4: Manual verification against the live stack**

- Log in to `/admin` as ADMIN. Open a category (e.g. "Zapatos"), toggle
  "esta categoría define campos de variante", set custom labels
  ("Tono"/"Talla numérica"), configure the secondary field as RANGE
  34-43, save. Confirm the category tree badge updates to show the new
  labels.
- Open the product form for a product in that category. Confirm the
  variant row editor shows "Tono"/"Talla numérica" and a dropdown built
  from the 34-43 range. Attempt to save a variant with size "50" — confirm
  the backend rejects it with a clear error (not a generic 500).
- Save a variant with size "38" — confirm it saves.
- Visit that product's storefront page. Confirm the variant selector shows
  "Tono"/"Talla numérica" as well (proving `product-service`'s resolved
  config matches the monolith's).
- Attempt to also assign the same product to a second shape category (e.g.
  "Carteras") in the admin form; save; confirm the ≤1-shape-category
  rejection surfaces as a clear admin-facing error, not a crash.
- Log in as a non-ADMIN authenticated user (e.g. SELLER) and confirm
  `PATCH /api/categories/{id}` now returns 403 (the RBAC fix).

- [ ] **Step 5: Fresh Sonar scan (both `pilar-estilo-backend` and
`PilarEstilo frontend` projects)**

Run the two `sonar-scan.sh`-equivalent commands (or the full
`scripts/quality/sonar-scan.sh` if it covers both) and confirm the quality
gate is `OK` with `new_violations: 0` on both projects.

- [ ] **Step 6: Report to the user**

Summarize: what shipped, the deliberate behavior changes (ProductSizeRules
loosening, RBAC fix, per-product override removal), and the manual
verification results above — before this branch merges to `develop`/`master`
following this repo's normal push/CI/deploy discipline.
