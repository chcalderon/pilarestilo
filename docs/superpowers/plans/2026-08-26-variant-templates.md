# Variant Templates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the category-derived variant field config (`ShapeCategoryResolver`,
`CategoryVariantFieldValidator`, `allowedCategoryIdsFor`) with an independent, admin-managed
`VariantTemplate` catalogue assigned directly to a product via `variant_template_id`, with zero
dependency on the product's categories.

**Architecture:** New hexagonal module `varianttemplate` (domain/application/infrastructure, same
shape as every other module) owns the template catalogue and its own CRUD admin page. The
`product` module gains a `variant_template_id` FK and reads/validates against it instead of
walking categories. The `category` module loses its variant-field-defining role entirely
(`defines_variant_fields`/`variant_field_config` columns stay in the DB, unread). `product-service`
independently mirrors the read-side resolution so its `GET/HEAD /api/products*` responses stay
byte-identical to the monolith's.

**Tech Stack:** Java 25 + Spring Boot 4.0.7 (hexagonal backend), Astro 4 + React admin islands,
Flyway migrations, JSONB persistence via `@JdbcTypeCode(SqlTypes.JSON)`.

**Spec:** `docs/superpowers/specs/2026-08-26-variant-templates-design.md`

## Global Constraints

- Migrations: current ceiling V88; this plan adds V89 (`variant_templates` table) and V90
  (`products.variant_template_id`). Never edit V87/V88.
- `services/product-service` must ship matching resolver logic in the same commit set as any
  monolith change to product/variant-template read shape — same rule CLAUDE.md documents for
  `order-service`, already applied once this cycle for the category-derived config.
- Write-time-only validation: `VariantTemplateValidator` runs only in `CreateProductUseCase`/
  `UpdateProductUseCase`, never inside the `Product`/`VariantTemplate` aggregates or on any read
  path — editing a template after products use it must never turn loading those products into a
  runtime error.
- `categories.defines_variant_fields`/`variant_field_config` columns and their CHECK constraint
  stay in the schema (expand/contract), stopped being read/written by application code; a future,
  separate contract migration removes them once nothing references them (same as the still-pending
  `products.variant_type` cleanup since V69).
- No data migration for existing category variant configs: every product starts with
  `variant_template_id = NULL` (generic Variante/Detalle fallback) and gets reassigned by hand —
  this was an explicit product decision, not an oversight.
- RBAC ruling (deviates from the spec's literal "new permission catalog entries" text — recorded
  here as the binding decision): `VariantTemplateController` requires `hasRole('ADMIN')` on **every**
  method, including reads. Unlike categories (which need public reads for storefront navigation),
  nothing outside the admin CMS ever reads the template catalogue directly — customers only ever
  see a product's already-resolved `variantFieldConfig`, a separate field on `ProductDto`. No new
  `permissions`/`role_permission_grants` migration is added.
- Ruling: `Product.setVariantTemplateId(...)` is called unconditionally in both
  `CreateProductUseCase` and `UpdateProductUseCase` (never guarded by a null-check the way
  `categoryIds` is). `categoryIds`' null-means-"leave untouched" guard exists to support callers
  that omit the field on partial updates; `variantTemplateId` is a brand-new single-value field
  with no such caller, and the frontend always sends it (a real id or `undefined` → `null`), so
  unconditional assignment is the simplest correct behavior.
- Test-tier ruling: this codebase does not give every CRUD use case or repository adapter its own
  dedicated test — `DeleteCategoryUseCase`, `ListCategoriesUseCase`, `CategoryRepositoryAdapter`,
  and `CategoryController` all ship with zero dedicated test files today, verified only through
  end-to-end Docker checks. This plan writes focused unit tests for the new module's actual logic
  (value object validation, domain model invariants, the delete guard, the write-time validator)
  and skips dedicated tests for pure pass-through CRUD/controller/adapter code, consistent with
  the codebase's own bar — Task 17's end-to-end checklist is what actually proves the wiring.

---

## Task 1: Migrations V89 and V90

**Files:**
- Create: `backend/src/main/resources/db/migration/V89__variant_templates.sql`
- Create: `backend/src/main/resources/db/migration/V90__products_variant_template_id.sql`

**Interfaces:**
- Produces: table `variant_templates(id UUID PK, name VARCHAR(120) NOT NULL, field_config JSONB
  NOT NULL, created_at TIMESTAMPTZ NOT NULL)`; column `products.variant_template_id UUID NULL
  REFERENCES variant_templates(id)`. Every later task's JPA entities map onto these exact names.

- [ ] **Step 1: Write V89**

```sql
-- V89__variant_templates.sql
-- Independent, admin-managed catalogue of variant field shapes (label, inputType,
-- options/min/max, allowMultiple/allowCustom for a primary+secondary pair), assigned
-- directly to a product -- see docs/superpowers/specs/2026-08-26-variant-templates-design.md.
-- Replaces the category-derived config from V87/V88, which stays in the categories table
-- unread (expand/contract) rather than being dropped here.

CREATE TABLE variant_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    field_config JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Write V90**

```sql
-- V90__products_variant_template_id.sql
-- No backfill: every existing product starts with variant_template_id = NULL (generic
-- Variante/Detalle fallback) and is reassigned by hand -- explicit product decision, not
-- an oversight. See V89 for the referenced table.

ALTER TABLE products
    ADD COLUMN variant_template_id UUID NULL REFERENCES variant_templates(id);
```

- [ ] **Step 3: Verify Flyway picks up both migrations**

Run: `cd backend && mvn -Dtest=none -DfailIfNoTests=false test-compile` then start the app once
against the Dockerized Postgres (or run `mvn spring-boot:run -Dspring-boot.run.profiles=local` and
Ctrl-C once it logs `Successfully applied 2 migrations` for `variant_templates` / schema version
90) to confirm both apply cleanly with no checksum conflicts. This is a schema-only step; no Java
test exists yet to exercise the new table.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V89__variant_templates.sql backend/src/main/resources/db/migration/V90__products_variant_template_id.sql
git commit -m "feat: add variant_templates table and products.variant_template_id FK"
```

---

## Task 2: VariantFieldConfig value object

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/domain/valueobjects/VariantFieldConfig.java`
- Test: `backend/src/test/java/com/pilarestilo/varianttemplate/domain/valueobjects/VariantFieldConfigTest.java`

**Interfaces:**
- Produces: `VariantFieldConfig(FieldConfig primary, FieldConfig secondary)` record;
  `VariantFieldConfig.FieldConfig(String label, InputType inputType, List<String> options,
  Integer min, Integer max, boolean allowMultiple, boolean allowCustom)`; `InputType` enum
  `{FREE_TEXT, OPTIONS, RANGE}`; `VariantFieldConfig.genericFallback()` static factory. Every
  later task that touches variant shape (domain model, validator, DTOs, repository adapter)
  consumes this exact type from `com.pilarestilo.varianttemplate.domain.valueobjects`.

This is a straight port of `com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig`
(already shipped, already tested) into the new module under a name with no "Category" prefix,
since it is no longer a category concept. The validation semantics are unchanged.

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.varianttemplate.domain.valueobjects;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VariantFieldConfigTest {

    @Test
    void freeTextField_needsNoOptionsOrRange() {
        var field = new VariantFieldConfig.FieldConfig(
                "Color", VariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true);

        assertEquals("Color", field.label());
        assertEquals(VariantFieldConfig.InputType.FREE_TEXT, field.inputType());
    }

    @Test
    void optionsField_requiresAtLeastOneOption() {
        assertThrows(DomainException.class, () -> new VariantFieldConfig.FieldConfig(
                "Talla", VariantFieldConfig.InputType.OPTIONS, List.of(), null, null, true, true));
    }

    @Test
    void rangeField_requiresMinLessThanMax() {
        assertThrows(DomainException.class, () -> new VariantFieldConfig.FieldConfig(
                "Numero", VariantFieldConfig.InputType.RANGE, List.of(), 43, 34, true, true));
    }

    @Test
    void rangeField_requiresBothMinAndMax() {
        assertThrows(DomainException.class, () -> new VariantFieldConfig.FieldConfig(
                "Numero", VariantFieldConfig.InputType.RANGE, List.of(), 34, null, true, true));
    }

    @Test
    void blankLabel_isRejected() {
        assertThrows(DomainException.class, () -> new VariantFieldConfig.FieldConfig(
                "  ", VariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true));
    }

    @Test
    void genericFallback_isBothFieldsFreeTextMultipleAndCustom() {
        VariantFieldConfig fallback = VariantFieldConfig.genericFallback();

        assertEquals("Variante", fallback.primary().label());
        assertEquals("Detalle", fallback.secondary().label());
        assertEquals(VariantFieldConfig.InputType.FREE_TEXT, fallback.primary().inputType());
        assertTrue(fallback.primary().allowMultiple());
        assertTrue(fallback.secondary().allowCustom());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=VariantFieldConfigTest`
Expected: FAIL to compile — `VariantFieldConfig` does not exist yet.

- [ ] **Step 3: Write the value object**

```java
package com.pilarestilo.varianttemplate.domain.valueobjects;

import com.pilarestilo.shared.domain.DomainException;

import java.util.List;

public record VariantFieldConfig(FieldConfig primary, FieldConfig secondary) {

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

    public static VariantFieldConfig genericFallback() {
        return new VariantFieldConfig(
                new FieldConfig("Variante", InputType.FREE_TEXT, List.of(), null, null, true, true),
                new FieldConfig("Detalle", InputType.FREE_TEXT, List.of(), null, null, true, true)
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=VariantFieldConfigTest`
Expected: PASS, 6/6.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/varianttemplate/domain/valueobjects/VariantFieldConfig.java backend/src/test/java/com/pilarestilo/varianttemplate/domain/valueobjects/VariantFieldConfigTest.java
git commit -m "feat: add VariantFieldConfig value object in new varianttemplate module"
```

---

## Task 3: VariantTemplate domain model + repository port

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/domain/model/VariantTemplate.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/domain/ports/VariantTemplateRepository.java`
- Test: `backend/src/test/java/com/pilarestilo/varianttemplate/domain/model/VariantTemplateTest.java`

**Interfaces:**
- Consumes: `VariantFieldConfig` from Task 2.
- Produces: `VariantTemplate.create(String name, VariantFieldConfig config)`,
  `VariantTemplate#update(String name, VariantFieldConfig config)`, getters `getId()`, `getName()`,
  `getConfig()`, `getCreatedAt()`, setters `setId(UUID)`/`setCreatedAt(Instant)` for persistence
  rehydration. `VariantTemplateRepository` port: `save`, `findById`, `findAll`, `deleteById`,
  `hasAssociatedProducts(UUID)`. Task 4's adapter implements this port; Tasks 5-7's use cases and
  validator depend on it.

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.varianttemplate.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VariantTemplateTest {

    @Test
    void create_trimsNameAndAssignsId() {
        VariantTemplate t = VariantTemplate.create("  Zapatos  ", VariantFieldConfig.genericFallback());

        assertEquals("Zapatos", t.getName());
        assertNotNull(t.getId());
        assertNotNull(t.getCreatedAt());
        assertEquals(VariantFieldConfig.genericFallback(), t.getConfig());
    }

    @Test
    void create_rejectsBlankName() {
        assertThrows(DomainException.class, () -> VariantTemplate.create("  ", VariantFieldConfig.genericFallback()));
    }

    @Test
    void create_rejectsNullConfig() {
        assertThrows(DomainException.class, () -> VariantTemplate.create("Zapatos", null));
    }

    @Test
    void update_replacesNameAndConfig() {
        VariantTemplate t = VariantTemplate.create("Zapatos", VariantFieldConfig.genericFallback());
        var newConfig = new VariantFieldConfig(
                new VariantFieldConfig.FieldConfig("Color", VariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true),
                new VariantFieldConfig.FieldConfig("Numero", VariantFieldConfig.InputType.RANGE, List.of(), 34, 43, true, true));

        t.update("Zapatos Deportivos", newConfig);

        assertEquals("Zapatos Deportivos", t.getName());
        assertEquals(newConfig, t.getConfig());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=VariantTemplateTest`
Expected: FAIL to compile — `VariantTemplate` does not exist yet.

- [ ] **Step 3: Write the domain model and port**

```java
package com.pilarestilo.varianttemplate.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;

import java.time.Instant;
import java.util.UUID;

public class VariantTemplate {

    private UUID id;
    private String name;
    private VariantFieldConfig config;
    private Instant createdAt;

    private VariantTemplate() {}

    public static VariantTemplate create(String name, VariantFieldConfig config) {
        validate(name, config);
        VariantTemplate t = new VariantTemplate();
        t.id = UUID.randomUUID();
        t.name = name.trim();
        t.config = config;
        t.createdAt = Instant.now();
        return t;
    }

    public void update(String name, VariantFieldConfig config) {
        validate(name, config);
        this.name = name.trim();
        this.config = config;
    }

    private static void validate(String name, VariantFieldConfig config) {
        if (name == null || name.isBlank()) {
            throw new DomainException("Variant template name cannot be blank");
        }
        if (config == null) {
            throw new DomainException("Variant template config cannot be null");
        }
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public VariantFieldConfig getConfig() { return config; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

```java
package com.pilarestilo.varianttemplate.domain.ports;

import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariantTemplateRepository {

    VariantTemplate save(VariantTemplate template);

    Optional<VariantTemplate> findById(UUID id);

    List<VariantTemplate> findAll();

    void deleteById(UUID id);

    boolean hasAssociatedProducts(UUID templateId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=VariantTemplateTest`
Expected: PASS, 4/4.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/varianttemplate/domain/model/VariantTemplate.java backend/src/main/java/com/pilarestilo/varianttemplate/domain/ports/VariantTemplateRepository.java backend/src/test/java/com/pilarestilo/varianttemplate/domain/model/VariantTemplateTest.java
git commit -m "feat: add VariantTemplate domain model and repository port"
```

---

## Task 4: VariantTemplateEntity, JPA repository, and repository adapter

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/persistence/entities/VariantTemplateEntity.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/persistence/repositories/VariantTemplateJpaRepository.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/persistence/repositories/VariantTemplateRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductJpaRepository.java`

**Interfaces:**
- Consumes: `VariantTemplate`/`VariantTemplateRepository` (Task 3), `VariantFieldConfig` (Task 2),
  the existing `ProductJpaRepository` (product module).
- Produces: `VariantTemplateRepositoryAdapter implements VariantTemplateRepository` (Spring
  `@Component`), backed by table `variant_templates` from Task 1;
  `ProductJpaRepository.countByVariantTemplateId(UUID)` — a new derived query mirroring the
  existing `countByCategoriesId(UUID)` on the same interface, used by the adapter's
  `hasAssociatedProducts`. Task 8 consumes `countByVariantTemplateId` too (product-side wiring
  reads/writes `products.variant_template_id` directly via JPA relationship, not this method).

No dedicated adapter test: per the Global Constraints test-tier ruling, `CategoryRepositoryAdapter`
(which this mirrors) has never had one either — its JSONB round-trip is proven by the end-to-end
Docker checklist in Task 17. This task is verified by compiling and by the CRUD use-case tests in
Task 5, which exercise the adapter indirectly once Spring wires it as the `VariantTemplateRepository`
bean.

- [ ] **Step 1: Add the derived count query to ProductJpaRepository**

In `backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductJpaRepository.java`,
add immediately after the existing `countByCategoriesId` method (do not touch anything else in the
file):

```java
    long countByCategoriesId(UUID categoryId);

    long countByVariantTemplateId(UUID variantTemplateId);
```

- [ ] **Step 2: Write the JPA entity**

```java
package com.pilarestilo.varianttemplate.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "variant_templates")
public class VariantTemplateEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at")
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> fieldConfig;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Map<String, Object> getFieldConfig() { return fieldConfig; }
    public void setFieldConfig(Map<String, Object> fieldConfig) { this.fieldConfig = fieldConfig; }
}
```

- [ ] **Step 3: Write the JPA repository interface**

```java
package com.pilarestilo.varianttemplate.infrastructure.persistence.repositories;

import com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VariantTemplateJpaRepository extends JpaRepository<VariantTemplateEntity, UUID> {
}
```

- [ ] **Step 4: Write the repository adapter**

Mirrors `CategoryRepositoryAdapter`'s JSONB conversion pattern exactly (manual `Map<String,Object>`
helpers, no generic serializer, duplicated per module by this repo's established convention).
Unlike the category version, `field_config` is `NOT NULL`, so `toRawConfig` never needs to return
null and carries no `@SuppressWarnings("java:S1168")`.

```java
package com.pilarestilo.varianttemplate.infrastructure.persistence.repositories;

import com.pilarestilo.product.infrastructure.persistence.repositories.ProductJpaRepository;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class VariantTemplateRepositoryAdapter implements VariantTemplateRepository {

    private final VariantTemplateJpaRepository jpa;
    private final ProductJpaRepository productJpa;

    public VariantTemplateRepositoryAdapter(VariantTemplateJpaRepository jpa, ProductJpaRepository productJpa) {
        this.jpa = jpa;
        this.productJpa = productJpa;
    }

    @Override
    public VariantTemplate save(VariantTemplate template) {
        VariantTemplateEntity e = toEntity(template);
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<VariantTemplate> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<VariantTemplate> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean hasAssociatedProducts(UUID templateId) {
        return productJpa.countByVariantTemplateId(templateId) > 0;
    }

    private VariantTemplateEntity toEntity(VariantTemplate t) {
        VariantTemplateEntity e = new VariantTemplateEntity();
        e.setId(t.getId());
        e.setName(t.getName());
        e.setCreatedAt(t.getCreatedAt() != null ? t.getCreatedAt() : Instant.now());
        e.setFieldConfig(toRawConfig(t.getConfig()));
        return e;
    }

    private VariantTemplate toDomain(VariantTemplateEntity e) {
        VariantTemplate t = VariantTemplate.create(e.getName(), fromRawConfig(e.getFieldConfig()));
        t.setId(e.getId());
        t.setCreatedAt(e.getCreatedAt());
        return t;
    }

    private static final String OPTIONS_KEY = "options";

    private static Map<String, Object> toRawConfig(VariantFieldConfig config) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("primary", toRawField(config.primary()));
        raw.put("secondary", toRawField(config.secondary()));
        return raw;
    }

    private static Map<String, Object> toRawField(VariantFieldConfig.FieldConfig field) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("label", field.label());
        raw.put("inputType", field.inputType().name());
        raw.put(OPTIONS_KEY, field.options());
        raw.put("min", field.min());
        raw.put("max", field.max());
        raw.put("allowMultiple", field.allowMultiple());
        raw.put("allowCustom", field.allowCustom());
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static VariantFieldConfig fromRawConfig(Map<String, Object> raw) {
        return new VariantFieldConfig(
                fromRawField((Map<String, Object>) raw.get("primary")),
                fromRawField((Map<String, Object>) raw.get("secondary")));
    }

    @SuppressWarnings("unchecked")
    private static VariantFieldConfig.FieldConfig fromRawField(Map<String, Object> raw) {
        List<String> options = raw.get(OPTIONS_KEY) == null
                ? List.of()
                : ((List<Object>) raw.get(OPTIONS_KEY)).stream().map(String::valueOf).toList();
        return new VariantFieldConfig.FieldConfig(
                (String) raw.get("label"),
                VariantFieldConfig.InputType.valueOf((String) raw.get("inputType")),
                options,
                raw.get("min") == null ? null : ((Number) raw.get("min")).intValue(),
                raw.get("max") == null ? null : ((Number) raw.get("max")).intValue(),
                Boolean.TRUE.equals(raw.get("allowMultiple")),
                Boolean.TRUE.equals(raw.get("allowCustom")));
    }
}
```

- [ ] **Step 5: Compile**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/persistence/ backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductJpaRepository.java
git commit -m "feat: add VariantTemplate JPA entity, repository, and adapter"
```

---

## Task 5: VariantTemplateDto and CRUD use cases

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/application/dto/VariantTemplateDto.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/web/requests/VariantFieldRequest.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/application/usecases/CreateVariantTemplateUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/application/usecases/UpdateVariantTemplateUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/application/usecases/DeleteVariantTemplateUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/application/usecases/GetVariantTemplateUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/application/usecases/ListVariantTemplatesUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/varianttemplate/application/usecases/CreateVariantTemplateUseCaseTest.java`
- Test: `backend/src/test/java/com/pilarestilo/varianttemplate/application/usecases/UpdateVariantTemplateUseCaseTest.java`
- Test: `backend/src/test/java/com/pilarestilo/varianttemplate/application/usecases/DeleteVariantTemplateUseCaseTest.java`

**Interfaces:**
- Consumes: `VariantTemplate`/`VariantTemplateRepository` (Task 3), `VariantFieldConfig` (Task 2).
- Produces: `VariantTemplateDto(UUID id, String name, VariantFieldConfigDto config)` with nested
  `VariantFieldConfigDto(FieldDto primary, FieldDto secondary)` and
  `FieldDto(String label, String inputType, List<String> options, Integer min, Integer max,
  boolean allowMultiple, boolean allowCustom)`; `VariantFieldRequest(String label, String
  inputType, List<String> options, Integer min, Integer max, boolean allowMultiple, boolean
  allowCustom)`; `CreateVariantTemplateUseCase.execute(String name, VariantFieldRequest primary,
  VariantFieldRequest secondary)`; `UpdateVariantTemplateUseCase.execute(UUID id, String name,
  VariantFieldRequest primary, VariantFieldRequest secondary)`;
  `DeleteVariantTemplateUseCase.execute(UUID id)`; `GetVariantTemplateUseCase.execute(UUID id)`;
  `ListVariantTemplatesUseCase.execute()`. Task 6's controller and its request records
  (`CreateVariantTemplateRequest`/`UpdateVariantTemplateRequest`) consume these signatures
  directly.

Only Create/Update/Delete get dedicated tests (per the Global Constraints test-tier ruling) — they
carry the config-construction and delete-guard logic. Get/List are one-line pass-throughs, same bar
as `GetCategoryTreeUseCase`/`ListCategoriesUseCase`, which have none.

- [ ] **Step 1: Write the failing tests**

```java
package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.VariantFieldRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateVariantTemplateUseCaseTest {

    @Mock
    VariantTemplateRepository variantTemplateRepository;

    @Test
    void create_persistsTemplateWithGivenConfig() {
        when(variantTemplateRepository.save(any(VariantTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        var useCase = new CreateVariantTemplateUseCase(variantTemplateRepository);
        var primary = new VariantFieldRequest("Color", "FREE_TEXT", List.of(), null, null, false, true);
        var secondary = new VariantFieldRequest("Numero", "RANGE", List.of(), 34, 43, true, true);

        VariantTemplateDto dto = useCase.execute("Zapatos", primary, secondary);

        assertEquals("Zapatos", dto.name());
        assertEquals("Numero", dto.config().secondary().label());
        assertEquals(34, dto.config().secondary().min());
    }
}
```

```java
package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.VariantFieldRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateVariantTemplateUseCaseTest {

    @Mock
    VariantTemplateRepository variantTemplateRepository;

    @Test
    void update_replacesNameAndConfig() {
        UUID id = UUID.randomUUID();
        VariantTemplate existing = VariantTemplate.create("Zapatos", VariantFieldConfig.genericFallback());
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(variantTemplateRepository.save(any(VariantTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        var useCase = new UpdateVariantTemplateUseCase(variantTemplateRepository);
        var primary = new VariantFieldRequest("Color", "FREE_TEXT", List.of(), null, null, false, true);
        var secondary = new VariantFieldRequest("Numero", "RANGE", List.of(), 34, 43, true, true);

        VariantTemplateDto dto = useCase.execute(id, "Zapatos Deportivos", primary, secondary);

        assertEquals("Zapatos Deportivos", dto.name());
        assertEquals("Numero", dto.config().secondary().label());
    }

    @Test
    void update_throwsWhenTemplateNotFound() {
        UUID id = UUID.randomUUID();
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.empty());
        var useCase = new UpdateVariantTemplateUseCase(variantTemplateRepository);
        var primary = new VariantFieldRequest("Color", "FREE_TEXT", List.of(), null, null, false, true);
        var secondary = new VariantFieldRequest("Numero", "RANGE", List.of(), 34, 43, true, true);

        assertThrows(DomainException.class, () -> useCase.execute(id, "Zapatos", primary, secondary));
    }
}
```

```java
package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteVariantTemplateUseCaseTest {

    @Mock
    VariantTemplateRepository variantTemplateRepository;

    @Test
    void delete_removesTemplateWithNoAssociatedProducts() {
        UUID id = UUID.randomUUID();
        VariantTemplate existing = VariantTemplate.create("Zapatos", VariantFieldConfig.genericFallback());
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(variantTemplateRepository.hasAssociatedProducts(id)).thenReturn(false);
        var useCase = new DeleteVariantTemplateUseCase(variantTemplateRepository);

        useCase.execute(id);

        verify(variantTemplateRepository).deleteById(id);
    }

    @Test
    void delete_rejectsWhenTemplateHasAssociatedProducts() {
        UUID id = UUID.randomUUID();
        VariantTemplate existing = VariantTemplate.create("Zapatos", VariantFieldConfig.genericFallback());
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(variantTemplateRepository.hasAssociatedProducts(id)).thenReturn(true);
        var useCase = new DeleteVariantTemplateUseCase(variantTemplateRepository);

        assertThrows(DomainException.class, () -> useCase.execute(id));
    }

    @Test
    void delete_throwsWhenTemplateNotFound() {
        UUID id = UUID.randomUUID();
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.empty());
        var useCase = new DeleteVariantTemplateUseCase(variantTemplateRepository);

        assertThrows(DomainException.class, () -> useCase.execute(id));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest=CreateVariantTemplateUseCaseTest,UpdateVariantTemplateUseCaseTest,DeleteVariantTemplateUseCaseTest`
Expected: FAIL to compile — none of the use cases, the DTO, or `VariantFieldRequest` exist yet.

- [ ] **Step 3: Write VariantTemplateDto**

```java
package com.pilarestilo.varianttemplate.application.dto;

import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;

import java.util.List;
import java.util.UUID;

public record VariantTemplateDto(UUID id, String name, VariantFieldConfigDto config) {

    public static VariantTemplateDto from(VariantTemplate t) {
        return new VariantTemplateDto(t.getId(), t.getName(), VariantFieldConfigDto.from(t.getConfig()));
    }

    public record VariantFieldConfigDto(FieldDto primary, FieldDto secondary) {
        public static VariantFieldConfigDto from(VariantFieldConfig config) {
            return new VariantFieldConfigDto(FieldDto.from(config.primary()), FieldDto.from(config.secondary()));
        }

        public record FieldDto(String label, String inputType, List<String> options, Integer min, Integer max,
                                boolean allowMultiple, boolean allowCustom) {
            public static FieldDto from(VariantFieldConfig.FieldConfig field) {
                return new FieldDto(field.label(), field.inputType().name(), field.options(),
                        field.min(), field.max(), field.allowMultiple(), field.allowCustom());
            }
        }
    }
}
```

- [ ] **Step 4: Write VariantFieldRequest**

```java
package com.pilarestilo.varianttemplate.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record VariantFieldRequest(
        @NotBlank String label,
        @NotNull @Pattern(regexp = "FREE_TEXT|OPTIONS|RANGE", message = "invalid inputType") String inputType,
        List<String> options,
        Integer min,
        Integer max,
        boolean allowMultiple,
        boolean allowCustom
) {}
```

- [ ] **Step 5: Write the five use cases**

```java
package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.VariantFieldRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateVariantTemplateUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public CreateVariantTemplateUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional
    public VariantTemplateDto execute(String name, VariantFieldRequest primary, VariantFieldRequest secondary) {
        VariantFieldConfig config = new VariantFieldConfig(toFieldConfig(primary), toFieldConfig(secondary));
        VariantTemplate template = VariantTemplate.create(name, config);
        return VariantTemplateDto.from(variantTemplateRepository.save(template));
    }

    private VariantFieldConfig.FieldConfig toFieldConfig(VariantFieldRequest req) {
        return new VariantFieldConfig.FieldConfig(
                req.label(),
                VariantFieldConfig.InputType.valueOf(req.inputType()),
                req.options(), req.min(), req.max(), req.allowMultiple(), req.allowCustom());
    }
}
```

```java
package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.VariantFieldRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateVariantTemplateUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public UpdateVariantTemplateUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional
    public VariantTemplateDto execute(UUID id, String name, VariantFieldRequest primary, VariantFieldRequest secondary) {
        VariantTemplate template = variantTemplateRepository.findById(id)
                .orElseThrow(() -> new DomainException("Variant template not found: " + id));
        VariantFieldConfig config = new VariantFieldConfig(toFieldConfig(primary), toFieldConfig(secondary));
        template.update(name, config);
        return VariantTemplateDto.from(variantTemplateRepository.save(template));
    }

    private VariantFieldConfig.FieldConfig toFieldConfig(VariantFieldRequest req) {
        return new VariantFieldConfig.FieldConfig(
                req.label(),
                VariantFieldConfig.InputType.valueOf(req.inputType()),
                req.options(), req.min(), req.max(), req.allowMultiple(), req.allowCustom());
    }
}
```

```java
package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteVariantTemplateUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public DeleteVariantTemplateUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional
    public void execute(UUID id) {
        if (variantTemplateRepository.findById(id).isEmpty()) {
            throw new DomainException("Variant template not found: " + id);
        }
        if (variantTemplateRepository.hasAssociatedProducts(id)) {
            throw new DomainException("Cannot delete variant template with associated products. Reassign products first.");
        }
        variantTemplateRepository.deleteById(id);
    }
}
```

```java
package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetVariantTemplateUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public GetVariantTemplateUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional(readOnly = true)
    public VariantTemplateDto execute(UUID id) {
        return variantTemplateRepository.findById(id)
                .map(VariantTemplateDto::from)
                .orElseThrow(() -> new DomainException("Variant template not found: " + id));
    }
}
```

```java
package com.pilarestilo.varianttemplate.application.usecases;

import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListVariantTemplatesUseCase {

    private final VariantTemplateRepository variantTemplateRepository;

    public ListVariantTemplatesUseCase(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    @Transactional(readOnly = true)
    public List<VariantTemplateDto> execute() {
        return variantTemplateRepository.findAll().stream().map(VariantTemplateDto::from).toList();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest=CreateVariantTemplateUseCaseTest,UpdateVariantTemplateUseCaseTest,DeleteVariantTemplateUseCaseTest`
Expected: PASS, 6/6.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/varianttemplate/application/ backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/web/requests/VariantFieldRequest.java backend/src/test/java/com/pilarestilo/varianttemplate/application/
git commit -m "feat: add VariantTemplateDto and CRUD use cases"
```

---

## Task 6: VariantTemplateController and its create/update requests

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/web/requests/CreateVariantTemplateRequest.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/web/requests/UpdateVariantTemplateRequest.java`
- Create: `backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/web/controllers/VariantTemplateController.java`

**Interfaces:**
- Consumes: the five use cases and `VariantFieldRequest` (Task 5).
- Produces: `GET/POST /api/variant-templates`, `GET/PATCH/DELETE /api/variant-templates/{id}`, all
  `@PreAuthorize("hasRole('ADMIN')")` per the Global Constraints RBAC ruling. Task 11's frontend
  `getVariantTemplates`/`createVariantTemplate`/`updateVariantTemplate`/`deleteVariantTemplate`
  call these exact paths.

No dedicated controller test, matching `CategoryController` (zero dedicated test files, verified
only through the Task 17 end-to-end checklist).

- [ ] **Step 1: Write the two request records**

```java
package com.pilarestilo.varianttemplate.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateVariantTemplateRequest(
        @NotBlank String name,
        @NotNull @Valid VariantFieldRequest primary,
        @NotNull @Valid VariantFieldRequest secondary
) {}
```

```java
package com.pilarestilo.varianttemplate.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateVariantTemplateRequest(
        @NotBlank String name,
        @NotNull @Valid VariantFieldRequest primary,
        @NotNull @Valid VariantFieldRequest secondary
) {}
```

- [ ] **Step 2: Write the controller**

```java
package com.pilarestilo.varianttemplate.infrastructure.web.controllers;

import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.application.usecases.CreateVariantTemplateUseCase;
import com.pilarestilo.varianttemplate.application.usecases.DeleteVariantTemplateUseCase;
import com.pilarestilo.varianttemplate.application.usecases.GetVariantTemplateUseCase;
import com.pilarestilo.varianttemplate.application.usecases.ListVariantTemplatesUseCase;
import com.pilarestilo.varianttemplate.application.usecases.UpdateVariantTemplateUseCase;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.CreateVariantTemplateRequest;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.UpdateVariantTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Every method requires ADMIN, unlike CategoryController -- the template catalogue has no
 * storefront or non-admin consumer (customers only ever see a product's already-resolved
 * variantFieldConfig, a separate field on ProductDto).
 */
@RestController
@RequestMapping("/api/variant-templates")
public class VariantTemplateController {

    private final CreateVariantTemplateUseCase createVariantTemplate;
    private final UpdateVariantTemplateUseCase updateVariantTemplate;
    private final DeleteVariantTemplateUseCase deleteVariantTemplate;
    private final GetVariantTemplateUseCase getVariantTemplate;
    private final ListVariantTemplatesUseCase listVariantTemplates;

    public VariantTemplateController(CreateVariantTemplateUseCase createVariantTemplate,
                                      UpdateVariantTemplateUseCase updateVariantTemplate,
                                      DeleteVariantTemplateUseCase deleteVariantTemplate,
                                      GetVariantTemplateUseCase getVariantTemplate,
                                      ListVariantTemplatesUseCase listVariantTemplates) {
        this.createVariantTemplate = createVariantTemplate;
        this.updateVariantTemplate = updateVariantTemplate;
        this.deleteVariantTemplate = deleteVariantTemplate;
        this.getVariantTemplate = getVariantTemplate;
        this.listVariantTemplates = listVariantTemplates;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<VariantTemplateDto> list() {
        return listVariantTemplates.execute();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public VariantTemplateDto getById(@PathVariable UUID id) {
        return getVariantTemplate.execute(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<VariantTemplateDto> create(@Valid @RequestBody CreateVariantTemplateRequest req) {
        VariantTemplateDto dto = createVariantTemplate.execute(req.name(), req.primary(), req.secondary());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public VariantTemplateDto update(@PathVariable UUID id, @Valid @RequestBody UpdateVariantTemplateRequest req) {
        return updateVariantTemplate.execute(id, req.name(), req.primary(), req.secondary());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteVariantTemplate.execute(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: Compile**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/varianttemplate/infrastructure/web/
git commit -m "feat: add VariantTemplateController"
```

---

## Task 7: VariantTemplateValidator (write-time validation)

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/product/application/VariantTemplateValidator.java`
- Test: `backend/src/test/java/com/pilarestilo/product/application/VariantTemplateValidatorTest.java`

**Interfaces:**
- Consumes: `VariantTemplateRepository`, `VariantTemplate`, `VariantFieldConfig` (varianttemplate
  module), `ProductVariantInput` (existing, `product.application.dto`).
- Produces: `VariantTemplateValidator.resolveConfig(UUID variantTemplateId)` →
  `VariantFieldConfig` (generic fallback when `null`, throws `DomainException` when the id does
  not resolve); `VariantTemplateValidator.validate(VariantFieldConfig config, List<ProductVariantInput>
  variants)` (no-op when `variants` is `null`). Task 8's `CreateProductUseCase`/
  `UpdateProductUseCase` call both methods exactly where `CategoryVariantFieldValidator` is called
  today.

This class lives in `product.application` (not `varianttemplate.application`), exactly mirroring
where `CategoryVariantFieldValidator` lives today even though it depends on the category module's
port — an established cross-module pattern in this codebase (`ProductRepositoryAdapter` already
reaches into `category.domain.model.Category`, and `CategoryRepositoryAdapter` already reaches
into `product.infrastructure.persistence.repositories.ProductJpaRepository`). The `validate()` body
is a byte-for-byte port of `CategoryVariantFieldValidator#validate` — the logic is unchanged, only
its `resolveConfig` input source changes from a category-id set to a single template id.

- [ ] **Step 1: Write the failing test**

Ported from the existing `CategoryVariantFieldValidatorTest` (product.application), replacing the
category-repository/category fixtures with template-repository/template fixtures.

```java
package com.pilarestilo.product.application;

import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariantTemplateValidatorTest {

    @Mock
    VariantTemplateRepository variantTemplateRepository;

    private final VariantFieldConfig.FieldConfig freeText =
            new VariantFieldConfig.FieldConfig("Color", VariantFieldConfig.InputType.FREE_TEXT,
                    List.of(), null, null, false, true);
    private final VariantFieldConfig.FieldConfig sizeOptions =
            new VariantFieldConfig.FieldConfig("Talla", VariantFieldConfig.InputType.OPTIONS,
                    List.of("S", "M", "L"), null, null, true, false);
    private final VariantFieldConfig.FieldConfig shoeRange =
            new VariantFieldConfig.FieldConfig("Numero", VariantFieldConfig.InputType.RANGE,
                    List.of(), 34, 43, true, false);

    @Test
    void resolveConfig_nullTemplateId_returnsGenericFallback() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);

        VariantFieldConfig config = validator.resolveConfig(null);

        assertEquals(VariantFieldConfig.genericFallback(), config);
    }

    @Test
    void resolveConfig_knownTemplateId_returnsItsConfig() {
        UUID id = UUID.randomUUID();
        VariantTemplate template = VariantTemplate.create("Zapatos", new VariantFieldConfig(freeText, shoeRange));
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.of(template));
        var validator = new VariantTemplateValidator(variantTemplateRepository);

        VariantFieldConfig config = validator.resolveConfig(id);

        assertEquals(shoeRange, config.secondary());
    }

    @Test
    void resolveConfig_unknownTemplateId_throws() {
        UUID id = UUID.randomUUID();
        when(variantTemplateRepository.findById(id)).thenReturn(Optional.empty());
        var validator = new VariantTemplateValidator(variantTemplateRepository);

        assertThrows(DomainException.class, () -> validator.resolveConfig(id));
    }

    @Test
    void validate_freeTextField_rejectsOnlyBlank() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, freeText);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "Cualquiera", 1))));
        var blank = List.of(new ProductVariantInput("Negro", "", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, blank));
    }

    @Test
    void validate_optionsField_rejectsValueNotInList_whenCustomNotAllowed() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, sizeOptions);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "M", 1))));
        var outOfList = List.of(new ProductVariantInput("Negro", "XXL", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, outOfList));
    }

    @Test
    void validate_optionsField_multiValue_splitsOnHyphenAndValidatesEachToken() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, sizeOptions);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "S-M-L", 1))));
        var oneTokenOutOfList = List.of(new ProductVariantInput("Negro", "S-XXL", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, oneTokenOutOfList));
    }

    @Test
    void validate_optionsField_multiValue_rejectsDuplicateToken() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, sizeOptions);

        var duplicateTokens = List.of(new ProductVariantInput("Negro", "S-S", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, duplicateTokens));
    }

    @Test
    void validate_singleValueField_doesNotSplitOnHyphen() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var singleValueFreeText = new VariantFieldConfig.FieldConfig(
                "Color", VariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true);
        var config = new VariantFieldConfig(singleValueFreeText, freeText);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Azul-Marino", "Cualquiera", 1))));
    }

    @Test
    void validate_rangeField_acceptsWithinBoundsAndRejectsOutside() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var config = new VariantFieldConfig(freeText, shoeRange);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Blanco", "38", 1))));
        var aboveRange = List.of(new ProductVariantInput("Blanco", "50", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, aboveRange));
        var notANumber = List.of(new ProductVariantInput("Blanco", "not-a-number", 1));
        assertThrows(DomainException.class, () -> validator.validate(config, notANumber));
    }

    @Test
    void validate_optionsField_allowsCustomValueWhenConfigured() {
        var validator = new VariantTemplateValidator(variantTemplateRepository);
        var customAllowed = new VariantFieldConfig.FieldConfig(
                "Talla", VariantFieldConfig.InputType.OPTIONS, List.of("S", "M", "L"), null, null, true, true);
        var config = new VariantFieldConfig(freeText, customAllowed);

        assertDoesNotThrow(() -> validator.validate(config, List.of(new ProductVariantInput("Negro", "XXL-a-medida", 1))));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=VariantTemplateValidatorTest`
Expected: FAIL to compile — `VariantTemplateValidator` does not exist yet.

- [ ] **Step 3: Write the validator**

```java
package com.pilarestilo.product.application;

import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves a product's variant template (if any) and validates submitted variant values against
 * its field config -- write time only. See ProductSizeRules for why this must never run on read.
 */
@Component
public class VariantTemplateValidator {

    private final VariantTemplateRepository variantTemplateRepository;

    public VariantTemplateValidator(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    public VariantFieldConfig resolveConfig(UUID variantTemplateId) {
        if (variantTemplateId == null) {
            return VariantFieldConfig.genericFallback();
        }
        return variantTemplateRepository.findById(variantTemplateId)
                .map(VariantTemplate::getConfig)
                .orElseThrow(() -> new DomainException("Variant template not found: " + variantTemplateId));
    }

    public void validate(VariantFieldConfig config, List<ProductVariantInput> variants) {
        if (variants == null) return;
        for (ProductVariantInput variant : variants) {
            validateField(config.primary(), variant.color());
            validateField(config.secondary(), variant.size());
        }
    }

    private void validateField(VariantFieldConfig.FieldConfig field, String rawValue) {
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

    private void validateToken(VariantFieldConfig.FieldConfig field, String token) {
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
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=VariantTemplateValidatorTest`
Expected: PASS, 11/11.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/product/application/VariantTemplateValidator.java backend/src/test/java/com/pilarestilo/product/application/VariantTemplateValidatorTest.java
git commit -m "feat: add VariantTemplateValidator for write-time variant validation"
```

---

## Task 8: Wire the product write path to variant_template_id

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
- Modify: `backend/src/test/java/com/pilarestilo/product/application/CreateProductUseCaseTest.java`

**Interfaces:**
- Consumes: `VariantTemplateValidator` (Task 7), `VariantFieldConfig` (Task 2),
  `VariantTemplateJpaRepository` (Task 4).
- Produces: `Product#getVariantTemplateId()`/`setVariantTemplateId(UUID)`;
  `ProductDto.variantTemplateId`; `CreateProductRequest.variantTemplateId`/
  `UpdateProductRequest.variantTemplateId`; `CreateProductUseCase.execute(...)` and
  `UpdateProductUseCase.execute(...)` both gain a `UUID variantTemplateId` parameter positioned
  right after `categoryIds` in both their short and full overloads. Task 10 (product-service)
  consumes the same `variant_template_id` column read-side; Tasks 14-15 (frontend) consume
  `ProductDto.variantTemplateId`/send `variantTemplateId` on the two request DTOs.

A product with **zero** categories currently gets `variantFieldConfig == null` from
`ProductRepositoryAdapter#toDomain` (its category-shape resolution only runs inside the
`if (!entity.getCategories().isEmpty())` branch — an existing quirk of the code being removed).
The template-based replacement resolves unconditionally, so this task also fixes that quirk as a
side effect: every product now gets a real `VariantFieldConfig` (generic fallback or the assigned
template's), never `null`.

- [ ] **Step 1: Product.java — replace the category-derived field with variantTemplateId**

Replace the whole file:

```java
package com.pilarestilo.product.domain.model;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.enums.ShippingOriginZone;
import com.pilarestilo.product.domain.valueobjects.Brand;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Product {

    private static final String VARIANT_NOT_FOUND_PREFIX = "Variante no encontrada: ";

    private UUID id;
    private String name;
    private String description;
    private Money price;
    private Money listPrice;
    private String imageUrl;
    private ProductCondition condition;
    private Brand brand;
    private int stock;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private java.math.BigDecimal avgRating = java.math.BigDecimal.ZERO;
    private int reviewCount = 0;
    private ShippingOriginZone shippingOriginZone = ShippingOriginZone.LOCAL;
    private List<ProductSizeStock> sizeStocks = new ArrayList<>();
    private List<ProductVariant> variants = new ArrayList<>();
    private Set<UUID> categoryIds = new HashSet<>();
    private List<String> categorySlugs = new ArrayList<>();
    private List<String> categoryTypes = new ArrayList<>();
    private UUID variantTemplateId;
    /**
     * The assigned template's field config (or the generic fallback), resolved on every read by
     * the repository adapter -- see VariantTemplateValidator for the write-time validation this
     * same config drives.
     */
    private VariantFieldConfig variantFieldConfig;

    private Product() {}

    public static Product create(String name, String description, Money price,
                                  String imageUrl, ProductCondition condition,
                                  String brand, int stock) {
        return create(name, description, price, imageUrl, condition, brand, stock, null);
    }

    // One parameter per column a product actually carries.
    @SuppressWarnings("java:S107")
    public static Product create(String name, String description, Money price,
                                  String imageUrl, ProductCondition condition,
                                  String brand, int stock, Money listPrice) {
        if (name == null || name.isBlank()) {
            throw new DomainException("Product name cannot be blank");
        }
        if (price == null) {
            throw new DomainException("Product price cannot be null");
        }
        if (price.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Product price must be greater than zero");
        }
        if (stock < 0) {
            throw new DomainException("Product stock cannot be negative");
        }
        if (condition == null) {
            throw new DomainException("Product condition cannot be null");
        }
        validateListPrice(price, listPrice);

        Product product = new Product();
        product.id = UUID.randomUUID();
        product.name = name.trim();
        product.description = description;
        product.price = price;
        product.listPrice = listPrice;
        product.imageUrl = imageUrl;
        product.condition = condition;
        product.brand = new Brand(brand);
        product.stock = stock;
        product.active = true;
        product.createdAt = Instant.now();
        product.updatedAt = product.createdAt;
        return product;
    }

    @SuppressWarnings("java:S107")
    public void update(String name, String description, Money price, String imageUrl,
                       ProductCondition condition, String brand, int stock, boolean active) {
        update(name, description, price, imageUrl, condition, brand, stock, active, null);
    }

    @SuppressWarnings("java:S107")
    public void update(String name, String description, Money price, String imageUrl,
                       ProductCondition condition, String brand, int stock, boolean active, Money listPrice) {
        if (name == null || name.isBlank()) {
            throw new DomainException("Product name cannot be blank");
        }
        if (price == null || price.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Product price must be greater than zero");
        }
        if (stock < 0) {
            throw new DomainException("Product stock cannot be negative");
        }
        validateListPrice(price, listPrice);
        this.name = name.trim();
        this.description = description;
        this.price = price;
        this.listPrice = listPrice;
        this.imageUrl = imageUrl;
        this.condition = condition;
        this.brand = new Brand(brand);
        this.stock = stock;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void decrementStock(int qty) {
        if (qty <= 0) {
            throw new DomainException("Quantity to decrement must be positive");
        }
        if (stock < qty) {
            throw new DomainException("Insufficient stock for product: " + name);
        }
        stock -= qty;
        this.updatedAt = Instant.now();
    }

    public void releaseStock(int qty) {
        if (qty <= 0) {
            throw new DomainException("Quantity to release must be positive");
        }
        stock += qty;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Money getPrice() { return price; }
    public Money getListPrice() { return listPrice; }
    public String getImageUrl() { return imageUrl; }
    public ProductCondition getCondition() { return condition; }
    public Brand getBrand() { return brand; }
    public int getStock() { return stock; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public java.math.BigDecimal getAvgRating() { return avgRating; }
    public int getReviewCount() { return reviewCount; }

    // Setters for reconstruction from persistence
    public void setId(UUID id) { this.id = id; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setActive(boolean active) { this.active = active; }
    public void setListPrice(Money listPrice) { this.listPrice = listPrice; }
    public void setAvgRating(java.math.BigDecimal avgRating) { this.avgRating = avgRating; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    private static void validateListPrice(Money price, Money listPrice) {
        if (listPrice == null) {
            return;
        }
        if (!price.currency().equalsIgnoreCase(listPrice.currency())) {
            throw new DomainException("Product list price currency must match sale price currency");
        }
        if (listPrice.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Product list price must be greater than zero");
        }
        if (listPrice.amount().compareTo(price.amount()) <= 0) {
            throw new DomainException("Product list price must be greater than sale price");
        }
    }

    public ShippingOriginZone getShippingOriginZone() { return shippingOriginZone; }

    public void setShippingOriginZone(ShippingOriginZone shippingOriginZone) {
        this.shippingOriginZone = shippingOriginZone;
    }

    public List<ProductSizeStock> getSizeStocks() { return sizeStocks; }
    public void setSizeStocks(List<ProductSizeStock> sizeStocks) {
        this.sizeStocks = sizeStocks != null ? sizeStocks : new ArrayList<>();
    }

    public Set<UUID> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(Set<UUID> categoryIds) {
        this.categoryIds = categoryIds != null ? categoryIds : new HashSet<>();
    }

    public List<String> getCategorySlugs() { return categorySlugs; }
    public void setCategorySlugs(List<String> categorySlugs) {
        this.categorySlugs = categorySlugs != null ? categorySlugs : new ArrayList<>();
    }

    public List<String> getCategoryTypes() { return categoryTypes; }
    public void setCategoryTypes(List<String> categoryTypes) {
        this.categoryTypes = categoryTypes != null ? categoryTypes : new ArrayList<>();
    }

    public UUID getVariantTemplateId() { return variantTemplateId; }
    public void setVariantTemplateId(UUID variantTemplateId) { this.variantTemplateId = variantTemplateId; }

    public VariantFieldConfig getVariantFieldConfig() { return variantFieldConfig; }
    public void setVariantFieldConfig(VariantFieldConfig variantFieldConfig) {
        this.variantFieldConfig = variantFieldConfig;
    }

    public List<ProductVariant> getVariants() { return variants; }
    public void setVariants(List<ProductVariant> variants) {
        this.variants = variants != null ? new ArrayList<>(variants) : new ArrayList<>();
        validateVariants();
        syncStocksFromVariants();
    }

    public void reserveVariant(int qty, String color, String size) {
        ProductVariant v = findVariant(color, size)
                .orElseThrow(() -> new DomainException(VARIANT_NOT_FOUND_PREFIX + color + " / " + size));
        if (v.available() < qty) {
            throw new DomainException("Stock insuficiente para variante: " + color + " / " + size);
        }
        List<ProductVariant> updated = variants.stream()
                .map(pv -> (lowerTrim(pv.getColor()).equals(lowerTrim(color))
                        && upperTrim(pv.getSize()).equals(upperTrim(size)))
                        ? new ProductVariant(pv.getColor(), pv.getSize(), pv.getStockOnHand(), pv.getStockReserved() + qty)
                        : pv)
                .toList();
        setVariants(updated);
    }

    public void releaseVariant(int qty, String color, String size) {
        ProductVariant v = findVariant(color, size)
                .orElseThrow(() -> new DomainException(VARIANT_NOT_FOUND_PREFIX + color + " / " + size));
        if (v.getStockReserved() < qty) {
            throw new DomainException("Stock reservado insuficiente para variante: " + color + " / " + size);
        }
        List<ProductVariant> updated = variants.stream()
                .map(pv -> (lowerTrim(pv.getColor()).equals(lowerTrim(color))
                        && upperTrim(pv.getSize()).equals(upperTrim(size)))
                        ? new ProductVariant(pv.getColor(), pv.getSize(), pv.getStockOnHand(), pv.getStockReserved() - qty)
                        : pv)
                .toList();
        setVariants(updated);
    }

    public void confirmVariant(int qty, String color, String size) {
        ProductVariant v = findVariant(color, size)
                .orElseThrow(() -> new DomainException(VARIANT_NOT_FOUND_PREFIX + color + " / " + size));
        if (v.getStockReserved() < qty) {
            throw new DomainException("Stock reservado insuficiente para confirmar variante: " + color + " / " + size);
        }
        List<ProductVariant> updated = variants.stream()
                .map(pv -> (lowerTrim(pv.getColor()).equals(lowerTrim(color))
                        && upperTrim(pv.getSize()).equals(upperTrim(size)))
                        ? new ProductVariant(pv.getColor(), pv.getSize(),
                                pv.getStockOnHand() - qty, pv.getStockReserved() - qty)
                        : pv)
                .toList();
        setVariants(updated);
    }

    private Optional<ProductVariant> findVariant(String color, String size) {
        return variants.stream()
                .filter(v -> lowerTrim(v.getColor()).equals(lowerTrim(color))
                        && upperTrim(v.getSize()).equals(upperTrim(size)))
                .findFirst();
    }

    private static String lowerTrim(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static String upperTrim(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }

    private void validateVariants() {
        if (variants.isEmpty()) {
            return;
        }
        Set<String> uniqueKeys = new HashSet<>();
        for (ProductVariant variant : variants) {
            String key = lowerTrim(variant.getColor()) + "::" + upperTrim(variant.getSize());
            if (!uniqueKeys.add(key)) {
                throw new DomainException("Duplicated product variant combination: " + variant.getColor() + " / " + variant.getSize());
            }
        }
    }

    private void syncStocksFromVariants() {
        if (variants.isEmpty()) {
            return;
        }

        int totalAvailable = variants.stream().mapToInt(ProductVariant::available).sum();
        Map<String, Integer> bySize = new LinkedHashMap<>();
        for (ProductVariant variant : variants) {
            bySize.merge(variant.getSize(), variant.available(), Integer::sum);
        }

        List<ProductSizeStock> nextSizeStocks = bySize.entrySet().stream()
                .map(e -> new ProductSizeStock(e.getKey(), e.getValue()))
                .toList();

        this.stock = totalAvailable;
        this.sizeStocks = nextSizeStocks;
    }
}
```

- [ ] **Step 2: ProductEntity.java — add the variant_template_id relationship**

In `backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/entities/ProductEntity.java`,
add the import and field/getter/setter (leave everything else — categories, size stocks, variants
— untouched):

```java
import com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity;
```

Add after the existing `categories` field (`private java.util.Set<CategoryEntity> categories = new java.util.HashSet<>();`):

```java

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_template_id")
    private VariantTemplateEntity variantTemplate;
```

Add after `getCategories()`/`setCategories()`:

```java

    public VariantTemplateEntity getVariantTemplate() { return variantTemplate; }
    public void setVariantTemplate(VariantTemplateEntity variantTemplate) { this.variantTemplate = variantTemplate; }
```

`ManyToOne` and `JoinColumn` are already imported via the file's `jakarta.persistence.*` wildcard
import — no new persistence import needed beyond `VariantTemplateEntity` itself.

- [ ] **Step 3: ProductRepositoryAdapter.java — resolve from the template, not the category walk**

Replace the whole file:

```java
package com.pilarestilo.product.infrastructure.persistence.repositories;

import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.category.infrastructure.persistence.repositories.CategoryJpaRepository;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductSizeStock;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductEntity;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductSizeStockEmbeddable;
import com.pilarestilo.product.infrastructure.persistence.entities.ProductVariantEmbeddable;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity;
import com.pilarestilo.varianttemplate.infrastructure.persistence.repositories.VariantTemplateJpaRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductRepositoryAdapter implements ProductRepository {

    private static final String CATEGORIES_ATTR = "categories";

    private final ProductJpaRepository jpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final VariantTemplateJpaRepository variantTemplateJpaRepository;

    public ProductRepositoryAdapter(ProductJpaRepository jpaRepository,
                                    CategoryJpaRepository categoryJpaRepository,
                                    VariantTemplateJpaRepository variantTemplateJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
        this.variantTemplateJpaRepository = variantTemplateJpaRepository;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = product.getId() == null
                ? new ProductEntity()
                : jpaRepository.findById(product.getId()).orElseGet(ProductEntity::new);
        applyToEntity(product, entity);
        ProductEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Product> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<Product> findAll(ProductFilter filter, Pageable pageable) {
        Specification<ProductEntity> spec = buildSpecification(filter);
        return jpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void updateRatingSummary(UUID productId, BigDecimal avgRating, int reviewCount) {
        jpaRepository.updateRatingSummary(productId, avgRating, reviewCount);
    }

    @Override
    public int syncProductStockFromVariants(UUID productId) {
        return jpaRepository.syncProductStockFromVariants(productId);
    }

    @Override
    public int atomicReserveVariantStock(UUID productId, String color, String size, int qty) {
        return jpaRepository.atomicReserveVariantStock(productId, color, size, qty);
    }

    @Override
    public int atomicReleaseVariantStock(UUID productId, String color, String size, int qty) {
        return jpaRepository.atomicReleaseVariantStock(productId, color, size, qty);
    }

    @Override
    public int atomicConfirmVariantStock(UUID productId, String color, String size, int qty) {
        return jpaRepository.atomicConfirmVariantStock(productId, color, size, qty);
    }

    @Override
    public int atomicReturnVariantStock(UUID productId, String color, String size, int qty) {
        return jpaRepository.atomicReturnVariantStock(productId, color, size, qty);
    }

    @Override
    public Page<Product> search(String term,
                                Boolean active,
                                Boolean inStock,
                                String condition,
                                String categorySlug,
                                LocalDate createdFrom,
                                LocalDate createdTo,
                                Pageable pageable) {
        String trimmedTerm = term == null ? "" : term.trim();
        String pattern = trimmedTerm.isEmpty() ? null : "%" + trimmedTerm.toLowerCase() + "%";
        Specification<ProductEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendSearchPredicates(predicates, root, query, cb, new SearchCriteria(pattern, active, inStock, condition));
            if (categorySlug != null && !categorySlug.isBlank()) {
                Join<Object, Object> filterCats = root.join(CATEGORIES_ATTR, JoinType.INNER);
                predicates.add(cb.equal(filterCats.get("slug"), categorySlug));
            }
            appendCreatedAtPredicates(predicates, root, cb, createdFrom, createdTo);
            if (query != null) query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return jpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    private record SearchCriteria(String pattern, Boolean active, Boolean inStock, String condition) {}

    private void appendSearchPredicates(List<Predicate> predicates,
                                        jakarta.persistence.criteria.Root<ProductEntity> root,
                                        jakarta.persistence.criteria.CriteriaQuery<?> query,
                                        jakarta.persistence.criteria.CriteriaBuilder cb,
                                        SearchCriteria criteria) {
        if (criteria.pattern() != null) {
            predicates.add(buildSearchTermPredicate(root, cb, criteria.pattern()));
        }
        if (criteria.active() != null) {
            predicates.add(cb.equal(root.get("active"), criteria.active()));
        }
        if (Boolean.TRUE.equals(criteria.inStock())) {
            predicates.add(buildInStockPredicate(root, query, cb));
        }
        if (criteria.condition() != null && !criteria.condition().isBlank()) {
            predicates.add(cb.equal(root.get("condition"), ProductCondition.valueOf(criteria.condition())));
        }
    }

    private Predicate buildSearchTermPredicate(jakarta.persistence.criteria.Root<ProductEntity> root,
                                               jakarta.persistence.criteria.CriteriaBuilder cb,
                                               String pattern) {
        Join<Object, Object> textCats = root.join(CATEGORIES_ATTR, JoinType.LEFT);
        return cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("brand")), pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(textCats.get("nameEs")), pattern),
                cb.like(cb.lower(textCats.get("nameEn")), pattern),
                cb.like(cb.lower(textCats.get("slug")), pattern)
        );
    }

    private Specification<ProductEntity> buildSpecification(ProductFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            appendPriceAndAttributePredicates(predicates, root, cb, filter);
            if (Boolean.TRUE.equals(filter.inStock())) {
                predicates.add(buildInStockPredicate(root, query, cb));
            }
            if (filter.categorySlug() != null) {
                Join<Object, Object> cats = root.join(CATEGORIES_ATTR, jakarta.persistence.criteria.JoinType.INNER);
                predicates.add(cb.equal(cats.get("slug"), filter.categorySlug()));
                if (query != null) query.distinct(true);
            }
            appendCreatedAtPredicates(predicates, root, cb, filter.createdFrom(), filter.createdTo());

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void appendPriceAndAttributePredicates(List<Predicate> predicates,
                                                    jakarta.persistence.criteria.Root<ProductEntity> root,
                                                    jakarta.persistence.criteria.CriteriaBuilder cb,
                                                    ProductFilter filter) {
        if (filter.condition() != null) {
            predicates.add(cb.equal(root.get("condition"),
                    ProductCondition.valueOf(filter.condition())));
        }
        if (filter.brand() != null) {
            predicates.add(cb.like(cb.lower(root.get("brand")),
                    "%" + filter.brand().toLowerCase() + "%"));
        }
        if (filter.minPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("priceAmount"), filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("priceAmount"), filter.maxPrice()));
        }
        if (filter.active() != null) {
            predicates.add(cb.equal(root.get("active"), filter.active()));
        }
    }

    private Predicate buildInStockPredicate(jakarta.persistence.criteria.Root<ProductEntity> root,
                                            jakarta.persistence.criteria.CriteriaQuery<?> query,
                                            jakarta.persistence.criteria.CriteriaBuilder cb) {
        Join<Object, Object> variants = root.join("variants", JoinType.LEFT);
        if (query != null) {
            query.distinct(true);
        }
        return cb.or(
                cb.greaterThan(root.get("stock"), 0),
                cb.greaterThan(variants.get("stockOnHand"), 0)
        );
    }

    private void appendCreatedAtPredicates(List<Predicate> predicates,
                                           jakarta.persistence.criteria.Root<ProductEntity> root,
                                           jakarta.persistence.criteria.CriteriaBuilder cb,
                                           LocalDate createdFrom,
                                           LocalDate createdTo) {
        if (createdFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createdAt"),
                    createdFrom.atStartOfDay().toInstant(ZoneOffset.UTC)
            ));
        }
        if (createdTo != null) {
            predicates.add(cb.lessThan(
                    root.get("createdAt"),
                    createdTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
            ));
        }
    }

    private void applyToEntity(Product product, ProductEntity entity) {
        entity.setId(product.getId());
        entity.setName(product.getName());
        entity.setDescription(product.getDescription());
        entity.setPriceAmount(product.getPrice().amount());
        entity.setPriceCurrency(product.getPrice().currency());
        entity.setListPriceAmount(product.getListPrice() != null ? product.getListPrice().amount() : null);
        entity.setListPriceCurrency(product.getListPrice() != null ? product.getListPrice().currency() : null);
        entity.setImageUrl(product.getImageUrl());
        entity.setCondition(product.getCondition());
        entity.setBrand(product.getBrand().value());
        entity.setStock(product.getStock());
        entity.setActive(product.isActive());
        entity.setCreatedAt(product.getCreatedAt());
        entity.setUpdatedAt(product.getUpdatedAt());
        entity.setShippingOriginZone(product.getShippingOriginZone());

        // Hibernate's merge path clears and repopulates @ElementCollection lists in place
        // (CollectionType.replaceElements), so these must stay mutable -- .toList() here throws
        // UnsupportedOperationException on the next save of an already-persisted product.
        @SuppressWarnings("java:S6204")
        List<ProductSizeStockEmbeddable> sizeEmbeddables = product.getSizeStocks().stream()
                .map(s -> new ProductSizeStockEmbeddable(s.getSize(), s.getStock()))
                .collect(Collectors.toList());
        entity.setSizeStocks(sizeEmbeddables);

        @SuppressWarnings("java:S6204")
        List<ProductVariantEmbeddable> variantEmbeddables = product.getVariants().stream()
                .map(v -> new ProductVariantEmbeddable(v.getColor(), v.getSize(), v.getStockOnHand(), v.getStockReserved()))
                .collect(Collectors.toList());
        entity.setVariants(variantEmbeddables);

        Set<CategoryEntity> cats = new HashSet<>(
                categoryJpaRepository.findAllById(product.getCategoryIds())
        );
        entity.setCategories(cats);

        entity.setVariantTemplate(product.getVariantTemplateId() != null
                ? variantTemplateJpaRepository.findById(product.getVariantTemplateId()).orElse(null)
                : null);
    }

    private Product toDomain(ProductEntity entity) {
        Money listPrice = null;
        if (entity.getListPriceAmount() != null) {
            String resolvedListCurrency = entity.getListPriceCurrency() == null || entity.getListPriceCurrency().isBlank()
                    ? entity.getPriceCurrency()
                    : entity.getListPriceCurrency();
            listPrice = new Money(entity.getListPriceAmount(), resolvedListCurrency);
        }
        Product product = Product.create(
                entity.getName(),
                entity.getDescription(),
                new Money(entity.getPriceAmount(), entity.getPriceCurrency()),
                entity.getImageUrl(),
                entity.getCondition(),
                entity.getBrand(),
                entity.getStock(),
                listPrice
        );
        product.setId(entity.getId());
        product.setActive(entity.isActive());
        product.setCreatedAt(entity.getCreatedAt());
        product.setUpdatedAt(entity.getUpdatedAt());
        product.setAvgRating(entity.getAvgRating());
        product.setReviewCount(entity.getReviewCount());
        if (entity.getShippingOriginZone() != null) {
            product.setShippingOriginZone(entity.getShippingOriginZone());
        }
        List<ProductVariant> variants = (entity.getVariants() == null ? List.<ProductVariantEmbeddable>of() : entity.getVariants()).stream()
                .map(v -> new ProductVariant(v.getColor(), v.getSize(), v.getStockOnHand(), v.getStockReserved()))
                .toList();
        /*
         * setVariants runs syncStocksFromVariants, which derives sizeStocks and stock from these
         * rows — so reading product_size_stocks first was work that got overwritten a line later.
         * The stored value is only kept for a product with no variants at all, where the
         * derivation returns early and there is nothing else to fall back on.
         */
        product.setVariants(variants);
        if (variants.isEmpty()) {
            product.setSizeStocks(entity.getSizeStocks().stream()
                    .map(sizeStock -> new ProductSizeStock(sizeStock.getSize(), sizeStock.getStock()))
                    .toList());
        }

        // Map categories from entity
        if (entity.getCategories() != null && !entity.getCategories().isEmpty()) {
            Set<UUID> ids = entity.getCategories().stream()
                    .map(CategoryEntity::getId)
                    .collect(Collectors.toSet());
            List<String> slugs = entity.getCategories().stream()
                    .map(CategoryEntity::getSlug)
                    .sorted()
                    .toList();
            List<String> categoryTypes = entity.getCategories().stream()
                    .map(category -> category.getCategoryType() != null ? category.getCategoryType().name() : "GENERIC")
                    .distinct()
                    .sorted()
                    .toList();
            product.setCategoryIds(ids);
            product.setCategorySlugs(slugs);
            product.setCategoryTypes(categoryTypes);
        }

        VariantTemplateEntity template = entity.getVariantTemplate();
        product.setVariantTemplateId(template != null ? template.getId() : null);
        product.setVariantFieldConfig(template != null
                ? fromRawConfig(template.getFieldConfig())
                : VariantFieldConfig.genericFallback());

        return product;
    }

    private static final String OPTIONS_KEY = "options";

    @SuppressWarnings("unchecked")
    private static VariantFieldConfig fromRawConfig(Map<String, Object> raw) {
        return new VariantFieldConfig(
                fromRawField((Map<String, Object>) raw.get("primary")),
                fromRawField((Map<String, Object>) raw.get("secondary")));
    }

    @SuppressWarnings("unchecked")
    private static VariantFieldConfig.FieldConfig fromRawField(Map<String, Object> raw) {
        List<String> options = raw.get(OPTIONS_KEY) == null
                ? List.of()
                : ((List<Object>) raw.get(OPTIONS_KEY)).stream().map(String::valueOf).toList();
        return new VariantFieldConfig.FieldConfig(
                (String) raw.get("label"),
                VariantFieldConfig.InputType.valueOf((String) raw.get("inputType")),
                options,
                raw.get("min") == null ? null : ((Number) raw.get("min")).intValue(),
                raw.get("max") == null ? null : ((Number) raw.get("max")).intValue(),
                Boolean.TRUE.equals(raw.get("allowMultiple")),
                Boolean.TRUE.equals(raw.get("allowCustom")));
    }
}
```

Note: `product.getCategoryIds()`/`categorySlugs`/`categoryTypes` now default to empty
collections (from `Product`'s field initializers) when a product has no categories, exactly as
before — only the variant-template resolution moved outside that `if` block, fixing the
null-`variantFieldConfig`-on-zero-categories quirk described above.

- [ ] **Step 4: ProductDto.java — add variantTemplateId**

Replace the whole file:

```java
package com.pilarestilo.product.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductDto(
        UUID id,
        String name,
        String description,
        BigDecimal priceAmount,
        String priceCurrency,
        BigDecimal listPriceAmount,
        String listPriceCurrency,
        String imageUrl,
        String condition,
        String brand,
        int stock,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal avgRating,
        int reviewCount,
        String shippingOriginZone,
        UUID variantTemplateId,
        ProductVariantFieldConfigDto variantFieldConfig,
        List<SizeStockDto> sizeStocks,
        List<String> categorySlugs,
        List<String> categoryTypes,
        List<VariantDto> variants
) {
    public record SizeStockDto(String size, int stock) {}
    public record VariantDto(String color, String size, int stock, int stockOnHand, int stockReserved, int stockAvailable) {}
    public record ProductVariantFieldConfigDto(FieldDto primary, FieldDto secondary) {
        public record FieldDto(String label, String inputType, List<String> options,
                                Integer min, Integer max, boolean allowMultiple, boolean allowCustom) {}
    }
}
```

- [ ] **Step 5: ProductMapper.java — retarget the config conversion and pass through the id**

Replace the whole file:

```java
package com.pilarestilo.product.application.mappers;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;

import java.util.List;

public class ProductMapper {

    private ProductMapper() {}

    public static ProductDto toDto(Product product) {
        List<ProductDto.SizeStockDto> sizeStocks = product.getSizeStocks().stream()
                .map(s -> new ProductDto.SizeStockDto(s.getSize(), s.getStock()))
                .toList();
        List<ProductDto.VariantDto> variants = product.getVariants().stream()
                .map(v -> new ProductDto.VariantDto(v.getColor(), v.getSize(), v.getStockOnHand(), v.getStockOnHand(), v.getStockReserved(), v.available()))
                .toList();

        List<String> slugs = product.getCategorySlugs();
        List<String> categoryTypes = product.getCategoryTypes();

        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().amount(),
                product.getPrice().currency(),
                product.getListPrice() != null ? product.getListPrice().amount() : null,
                product.getListPrice() != null ? product.getListPrice().currency() : null,
                product.getImageUrl(),
                product.getCondition().name(),
                product.getBrand().value(),
                product.getStock(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getAvgRating(),
                product.getReviewCount(),
                product.getShippingOriginZone().name(),
                product.getVariantTemplateId(),
                toConfigDto(product.getVariantFieldConfig()),
                sizeStocks,
                slugs,
                categoryTypes,
                variants
        );
    }

    private static ProductDto.ProductVariantFieldConfigDto toConfigDto(VariantFieldConfig config) {
        if (config == null) return null;
        return new ProductDto.ProductVariantFieldConfigDto(toFieldDto(config.primary()), toFieldDto(config.secondary()));
    }

    private static ProductDto.ProductVariantFieldConfigDto.FieldDto toFieldDto(VariantFieldConfig.FieldConfig field) {
        return new ProductDto.ProductVariantFieldConfigDto.FieldDto(field.label(), field.inputType().name(),
                field.options(), field.min(), field.max(), field.allowMultiple(), field.allowCustom());
    }
}
```

- [ ] **Step 6: CreateProductUseCase.java — swap the validator, add variantTemplateId**

Replace the whole file:

```java
package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.application.VariantTemplateValidator;
import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.events.ProductCreated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateProductUseCase {

    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;
    private final VariantTemplateValidator variantTemplateValidator;

    public CreateProductUseCase(ProductRepository productRepository, DomainEventPublisher eventPublisher,
                                 VariantTemplateValidator variantTemplateValidator) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.variantTemplateValidator = variantTemplateValidator;
    }

    // Delegates via 'this' to the fuller overload below, bypassing its own @Transactional proxy --
    // harmless, since this overload's own @Transactional is already active by the time it does.
    // One parameter per field the product form actually submits.
    @SuppressWarnings({"java:S6809", "java:S107"})
    @Transactional
    public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               Boolean active, Set<UUID> categoryIds, UUID variantTemplateId) {
        return execute(name, description, priceAmount, priceCurrency, listPriceAmount,
                listPriceCurrency, imageUrl, condition, brand, stock, active, categoryIds, variantTemplateId, null);
    }

    @SuppressWarnings("java:S107")
    @Transactional
    public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               Boolean active, Set<UUID> categoryIds, UUID variantTemplateId,
                               List<ProductVariantInput> variants) {
        Money price = Money.of(priceAmount, priceCurrency == null || priceCurrency.isBlank()
                ? Money.DEFAULT_CURRENCY
                : priceCurrency);
        Money listPrice = null;
        if (listPriceAmount != null) {
            String resolvedListCurrency = listPriceCurrency == null || listPriceCurrency.isBlank()
                    ? price.currency()
                    : listPriceCurrency;
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
        product.setVariantTemplateId(variantTemplateId);
        if (variants != null) {
            VariantFieldConfig config = variantTemplateValidator.resolveConfig(variantTemplateId);
            variantTemplateValidator.validate(config, variants);
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
}
```

- [ ] **Step 7: UpdateProductUseCase.java — the same treatment**

Replace the whole file:

```java
package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.application.VariantTemplateValidator;
import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.mappers.ProductMapper;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.events.ProductUpdated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class UpdateProductUseCase {

    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;
    private final VariantTemplateValidator variantTemplateValidator;

    public UpdateProductUseCase(ProductRepository productRepository, DomainEventPublisher eventPublisher,
                                 VariantTemplateValidator variantTemplateValidator) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.variantTemplateValidator = variantTemplateValidator;
    }

    // Delegates via 'this' to the fuller overload below, bypassing its own @Transactional proxy --
    // harmless, since this overload's own @Transactional is already active by the time it does.
    // One parameter per field the product form actually submits.
    @SuppressWarnings({"java:S6809", "java:S107"})
    @Transactional
    public ProductDto execute(UUID id, String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               boolean active, Set<UUID> categoryIds, UUID variantTemplateId) {
        return execute(id, name, description, priceAmount, priceCurrency, listPriceAmount,
                listPriceCurrency, imageUrl, condition, brand, stock, active, categoryIds, variantTemplateId, null);
    }

    @SuppressWarnings("java:S107")
    @Transactional
    public ProductDto execute(UUID id, String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               boolean active, Set<UUID> categoryIds, UUID variantTemplateId,
                               List<ProductVariantInput> variants) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));

        Money price = Money.of(priceAmount, priceCurrency == null || priceCurrency.isBlank()
                ? Money.DEFAULT_CURRENCY
                : priceCurrency);
        Money listPrice = null;
        if (listPriceAmount != null) {
            String resolvedListCurrency = listPriceCurrency == null || listPriceCurrency.isBlank()
                    ? price.currency()
                    : listPriceCurrency;
            listPrice = Money.of(listPriceAmount, resolvedListCurrency);
        }
        ProductCondition productCondition = ProductCondition.valueOf(condition);

        product.update(name, description, price, imageUrl, productCondition, brand, stock, active, listPrice);
        if (categoryIds != null) {
            product.setCategoryIds(categoryIds);
        }
        product.setVariantTemplateId(variantTemplateId);
        if (variants != null) {
            VariantFieldConfig config = variantTemplateValidator.resolveConfig(variantTemplateId);
            variantTemplateValidator.validate(config, variants);
            product.setVariants(variants.stream().map(this::toVariant).toList());
        }
        Product saved = productRepository.save(product);

        eventPublisher.publish(new ProductUpdated(saved.getId(), saved.getName()));

        return ProductMapper.toDto(saved);
    }

    private ProductVariant toVariant(ProductVariantInput input) {
        try {
            return new ProductVariant(input.color(), input.size(), input.stock());
        } catch (DomainException _) {
            throw new DomainException("Invalid product variant size: " + input.size());
        }
    }
}
```

- [ ] **Step 8: CreateProductRequest.java / UpdateProductRequest.java — add variantTemplateId**

In both `backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/CreateProductRequest.java`
and `.../UpdateProductRequest.java`, add a `UUID variantTemplateId` field right after `categoryIds`
and before `variants`. `CreateProductRequest` becomes:

```java
package com.pilarestilo.product.infrastructure.web.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank(message = "Name is required")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal priceAmount,

        String priceCurrency,

        @DecimalMin(value = "0.01", message = "List price must be greater than zero")
        BigDecimal listPriceAmount,

        String listPriceCurrency,

        String imageUrl,

        @NotBlank(message = "Condition is required")
        String condition,

        @NotBlank(message = "Brand is required")
        String brand,

        @Min(value = 0, message = "Stock cannot be negative")
        int stock,

        Boolean active,

        Set<UUID> categoryIds,

        UUID variantTemplateId,

        List<@Valid ProductVariantRequest> variants
) {}
```

`UpdateProductRequest` gets the identical `UUID variantTemplateId` field added in the same
position (right after `categoryIds`, before `variants`); every other field and annotation stays
exactly as it is today (including `boolean active` unlike `Boolean active` on the create request).

- [ ] **Step 9: ProductController.java — thread variantTemplateId through both calls**

In `backend/src/main/java/com/pilarestilo/product/infrastructure/web/controllers/ProductController.java`,
change the two use-case invocations:

```java
    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody CreateProductRequest request) {
        ProductDto dto = createProductUseCase.execute(
                request.name(), request.description(), request.priceAmount(), request.priceCurrency(),
                request.listPriceAmount(), request.listPriceCurrency(),
                request.imageUrl(), request.condition(), request.brand(), request.stock(),
                request.active(), request.categoryIds(), request.variantTemplateId(), toVariantInputs(request.variants())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
```

```java
    @PutMapping("/{id}")
    public ProductDto update(@PathVariable UUID id,
                              @Valid @RequestBody UpdateProductRequest request) {
        return updateProductUseCase.execute(
                id, request.name(), request.description(), request.priceAmount(), request.priceCurrency(),
                request.listPriceAmount(), request.listPriceCurrency(),
                request.imageUrl(), request.condition(), request.brand(),
                request.stock(), request.active(), request.categoryIds(), request.variantTemplateId(),
                toVariantInputs(request.variants())
        );
    }
```

No other method in the file changes.

- [ ] **Step 10: Update CreateProductUseCaseTest.java for the new signature and mock type**

Replace the whole file:

```java
package com.pilarestilo.product.application;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.product.application.usecases.CreateProductUseCase;
import com.pilarestilo.product.domain.events.ProductCreated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    DomainEventPublisher eventPublisher;

    @Mock
    VariantTemplateValidator variantTemplateValidator;

    @InjectMocks
    CreateProductUseCase useCase;

    @Test
    void creates_product_and_publishes_event() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDto dto = useCase.execute(
                "Bolso LV", "Desc autentico",
                BigDecimal.valueOf(300000),
                "CLP",
                BigDecimal.valueOf(360000),
                "CLP",
                "http://img.example.com/bolso.jpg",
                "USED", "Louis Vuitton", 3, true, null, null
        );

        assertNotNull(dto.id());
        assertEquals("Bolso LV", dto.name());
        assertEquals("Louis Vuitton", dto.brand());
        assertEquals(3, dto.stock());
        assertEquals(0, BigDecimal.valueOf(360000).compareTo(dto.listPriceAmount()));
        verify(eventPublisher).publish(any(ProductCreated.class));
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void throws_when_price_is_zero() {
        assertThrows(Exception.class, () ->
                useCase.execute("Bolso", "desc", BigDecimal.ZERO, "CLP", null, null, "http://img", "USED", "LV", 1, true, null, null)
        );
    }

    @Test
    void throws_when_name_is_blank() {
        assertThrows(Exception.class, () ->
                useCase.execute("   ", "desc", BigDecimal.valueOf(100000), "CLP", null, null, "http://img", "USED", "LV", 1, true, null, null)
        );
    }

    @Test
    void throws_when_list_price_is_not_greater_than_sale_price() {
        assertThrows(Exception.class, () ->
                useCase.execute("Bolso", "desc", BigDecimal.valueOf(100000), "CLP",
                        BigDecimal.valueOf(90000), "CLP", "http://img", "USED", "LV", 1, true, null, null)
        );
    }

    @Test
    void stores_sizes_exactly_as_submitted_after_trimming() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDto dto = useCase.execute(
                "Abrigo", "desc", BigDecimal.valueOf(120000), "CLP", null, null,
                "http://img", "NEW", "Pilar", 0, true, null, null,
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

    @Test
    void accepts_a_single_letter_size_structural_check_only_now() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDto dto = useCase.execute(
                "Abrigo", "desc", BigDecimal.valueOf(120000), "CLP", null, null,
                "http://img", "NEW", "Pilar", 0, true, null, null,
                List.of(new ProductVariantInput("Camel", "X", 1))
        );

        assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("X")));
    }

    @Test
    void accepts_a_double_hyphen_structural_check_only_now() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDto dto = useCase.execute(
                "Abrigo", "desc", BigDecimal.valueOf(120000), "CLP", null, null,
                "http://img", "NEW", "Pilar", 0, true, null, null,
                List.of(new ProductVariantInput("Camel", "L--XL", 1))
        );

        assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("L--XL")));
    }
}
```

- [ ] **Step 11: Run the product module's tests**

Run: `cd backend && mvn test -Dtest=CreateProductUseCaseTest,VariantTemplateValidatorTest`
Expected: PASS, all green. (`UpdateProductUseCase` has no dedicated test file, same as before this
change — it is exercised via `ProductControllerIT`/`ProductRepositoryAdapterIT` at the IT layer.)

- [ ] **Step 12: Compile the whole backend**

Run: `cd backend && mvn -DskipTests compile`
Expected: BUILD SUCCESS. This does not yet compile clean end-to-end — Task 9 still has to delete
`CategoryVariantFieldValidator`, `ShapeCategoryResolver`, and `CategoryVariantFieldConfig`, which
`Product`/`ProductRepositoryAdapter` no longer reference after this task but which still exist on
disk unchanged until Task 9. Do not delete them here; Task 9 owns that removal.

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/product/ backend/src/test/java/com/pilarestilo/product/application/CreateProductUseCaseTest.java
git commit -m "feat: wire product write/read path to variant_template_id"
```

---

## Task 9: Decouple categories from variant fields

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/category/domain/model/Category.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/persistence/entities/CategoryEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/persistence/repositories/CategoryRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/dto/CategoryDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/dto/CategoryTreeNode.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/usecases/CreateCategoryUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/usecases/UpdateCategoryUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/application/usecases/GetCategoryTreeUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/web/controllers/CategoryController.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/CreateCategoryRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/UpdateCategoryRequest.java`
- Delete: `backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/CategoryVariantFieldRequest.java`
- Delete: `backend/src/main/java/com/pilarestilo/category/domain/valueobjects/CategoryVariantFieldConfig.java`
- Delete: `backend/src/main/java/com/pilarestilo/category/domain/model/ShapeCategoryResolver.java`
- Delete: `backend/src/main/java/com/pilarestilo/product/application/CategoryVariantFieldValidator.java`
- Delete: `backend/src/test/java/com/pilarestilo/category/domain/valueobjects/CategoryVariantFieldConfigTest.java`
- Delete: `backend/src/test/java/com/pilarestilo/category/domain/model/CategoryVariantFieldConfigMutatorTest.java`
- Delete: `backend/src/test/java/com/pilarestilo/category/domain/model/ShapeCategoryResolverTest.java`
- Delete: `backend/src/test/java/com/pilarestilo/category/application/usecases/CreateCategoryUseCaseVariantFieldTest.java`
- Delete: `backend/src/test/java/com/pilarestilo/product/application/CategoryVariantFieldValidatorTest.java`

**Interfaces:**
- Produces: `Category`/`CategoryDto`/`CategoryTreeNode`/`CreateCategoryRequest`/
  `UpdateCategoryRequest` all lose every `definesVariantFields`/`variantFieldConfig`/`primary`/
  `secondary` field; `CreateCategoryUseCase.execute`/`UpdateCategoryUseCase.execute` revert to
  their pre-feature 11/14-parameter signatures (slug, nameEs, nameEn, parentId, sortOrder,
  imageUrl, active, featured, menuVisible, categoryType, heroImageUrl — plus `id` for update).
  Task 11 (frontend `api.ts`) consumes these trimmed shapes.

`categories.defines_variant_fields`/`variant_field_config` columns and their CHECK constraint stay
in Postgres, untouched — this task only removes the Java code that reads/writes them, per the
Global Constraints expand/contract rule. `Category.categoryType`/`getCategoryType()`/
`updateMenuMetadata` and everything else on these files is unrelated and stays exactly as-is.

- [ ] **Step 1: Category.java — remove the variant-field fields and mutator**

Replace the whole file:

```java
package com.pilarestilo.category.domain.model;

import com.pilarestilo.category.domain.enums.CategoryType;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public class Category {

    /*
     * Deliberately linear. The obvious spelling of this rule, "^[a-z0-9]+(?:-[a-z0-9]+)*$", is the
     * classic catastrophic-backtracking shape: a long almost-matching slug makes the engine try
     * every split of the groups before failing. The three checks below say the same thing — only
     * these characters, no leading or trailing hyphen, no doubled hyphen — in one pass.
     */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private UUID id;
    private String slug;
    private String nameEs;
    private String nameEn;
    private UUID parentId;
    private int sortOrder;
    private boolean active;
    private boolean featured;
    private String imageUrl;
    private Instant createdAt;
    private boolean menuVisible = true;
    private CategoryType categoryType = CategoryType.GENERIC;
    private String heroImageUrl;

    private Category() {}

    public static Category create(String slug, String nameEs, String nameEn,
                                  UUID parentId, int sortOrder, String imageUrl) {
        validate(slug, nameEs, nameEn);
        Category c = new Category();
        c.id = UUID.randomUUID();
        c.slug = slug.trim().toLowerCase();
        c.nameEs = nameEs.trim();
        c.nameEn = nameEn.trim();
        c.parentId = parentId;
        c.sortOrder = sortOrder;
        c.active = true;
        c.imageUrl = imageUrl;
        c.createdAt = Instant.now();
        c.menuVisible = true;
        c.categoryType = CategoryType.GENERIC;
        return c;
    }

    @SuppressWarnings("java:S107")
    public void update(String slug, String nameEs, String nameEn,
                       UUID parentId, int sortOrder, boolean active, boolean featured, String imageUrl) {
        validate(slug, nameEs, nameEn);
        this.slug = slug.trim().toLowerCase();
        this.nameEs = nameEs.trim();
        this.nameEn = nameEn.trim();
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.active = active;
        this.featured = featured;
        this.imageUrl = imageUrl;
    }

    public void updateMenuMetadata(boolean menuVisible, CategoryType categoryType, String heroImageUrl) {
        this.menuVisible = menuVisible;
        this.categoryType = categoryType != null ? categoryType : CategoryType.GENERIC;
        this.heroImageUrl = heroImageUrl;
    }

    private static void validate(String slug, String nameEs, String nameEn) {
        if (slug == null || slug.isBlank() || !isValidSlug(slug.trim().toLowerCase())) {
            throw new DomainException("Category slug must be lowercase, hyphen-separated url-safe text");
        }
        if (nameEs == null || nameEs.isBlank()) throw new DomainException("Category nameEs is required");
        if (nameEn == null || nameEn.isBlank()) throw new DomainException("Category nameEn is required");
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getNameEs() { return nameEs; }
    public String getNameEn() { return nameEn; }
    public UUID getParentId() { return parentId; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public boolean isFeatured() { return featured; }
    public String getImageUrl() { return imageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isMenuVisible() { return menuVisible; }
    public CategoryType getCategoryType() { return categoryType; }
    public String getHeroImageUrl() { return heroImageUrl; }

    public void reorder(int sortOrder) { this.sortOrder = sortOrder; }

    // setters for persistence rehydration
    public void setId(UUID id) { this.id = id; }
    public void setActive(boolean active) { this.active = active; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setMenuVisible(boolean menuVisible) { this.menuVisible = menuVisible; }
    public void setCategoryType(CategoryType categoryType) { this.categoryType = categoryType; }
    public void setHeroImageUrl(String heroImageUrl) { this.heroImageUrl = heroImageUrl; }

    /**
     * A slug is lowercase letters, digits and single hyphens between them.
     *
     * <p>Three checks rather than one grouped pattern: the natural spelling,
     * {@code ^[a-z0-9]+(?:-[a-z0-9]+)*$}, is the classic catastrophic-backtracking shape — a long
     * almost-valid slug makes the engine try every split of the groups before failing.
     */
    private static boolean isValidSlug(String candidate) {
        return SLUG_PATTERN.matcher(candidate).matches()
                && !candidate.startsWith("-")
                && !candidate.endsWith("-")
                && !candidate.contains("--");
    }
}
```

- [ ] **Step 2: CategoryEntity.java — remove the JSONB mapping**

Replace the whole file:

```java
package com.pilarestilo.category.infrastructure.persistence.entities;

import com.pilarestilo.category.domain.enums.CategoryType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "name_es", nullable = false, length = 120)
    private String nameEs;

    @Column(name = "name_en", nullable = false, length = 120)
    private String nameEn;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "menu_visible", nullable = false)
    private boolean menuVisible = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", length = 24, nullable = false)
    private CategoryType categoryType = CategoryType.GENERIC;

    @Column(name = "hero_image_url", length = 500)
    private String heroImageUrl;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getNameEs() { return nameEs; }
    public void setNameEs(String nameEs) { this.nameEs = nameEs; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isMenuVisible() { return menuVisible; }
    public void setMenuVisible(boolean menuVisible) { this.menuVisible = menuVisible; }
    public CategoryType getCategoryType() { return categoryType; }
    public void setCategoryType(CategoryType categoryType) { this.categoryType = categoryType; }
    public String getHeroImageUrl() { return heroImageUrl; }
    public void setHeroImageUrl(String heroImageUrl) { this.heroImageUrl = heroImageUrl; }
}
```

- [ ] **Step 3: CategoryRepositoryAdapter.java — drop the JSONB helpers**

Replace the whole file:

```java
package com.pilarestilo.category.infrastructure.persistence.repositories;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.product.infrastructure.persistence.repositories.ProductJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;
    private final ProductJpaRepository productJpa;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpa, ProductJpaRepository productJpa) {
        this.jpa = jpa;
        this.productJpa = productJpa;
    }

    @Override
    public Category save(Category c) {
        CategoryEntity e = toEntity(c);
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Category> findAllByIds(Collection<UUID> ids) {
        return jpa.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Category> findChildren(UUID parentId) {
        return jpa.findByParentId(parentId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }

    @Override
    public boolean hasAssociatedProducts(UUID categoryId) {
        return productJpa.countByCategoriesId(categoryId) > 0;
    }

    @Override
    public List<Category> findFeatured() {
        return jpa.findByFeaturedTrueAndActiveTrueOrderBySortOrderAsc().stream().map(this::toDomain).toList();
    }

    private CategoryEntity toEntity(Category c) {
        CategoryEntity e = new CategoryEntity();
        e.setId(c.getId());
        e.setSlug(c.getSlug());
        e.setNameEs(c.getNameEs());
        e.setNameEn(c.getNameEn());
        e.setParentId(c.getParentId());
        e.setSortOrder(c.getSortOrder());
        e.setActive(c.isActive());
        e.setFeatured(c.isFeatured());
        e.setImageUrl(c.getImageUrl());
        e.setCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt() : Instant.now());
        e.setMenuVisible(c.isMenuVisible());
        e.setCategoryType(c.getCategoryType());
        e.setHeroImageUrl(c.getHeroImageUrl());
        return e;
    }

    private Category toDomain(CategoryEntity e) {
        Category c = Category.create(
                e.getSlug(), e.getNameEs(), e.getNameEn(),
                e.getParentId(), e.getSortOrder(), e.getImageUrl()
        );
        c.setId(e.getId());
        c.setActive(e.isActive());
        c.setFeatured(e.isFeatured());
        c.setCreatedAt(e.getCreatedAt());
        c.setMenuVisible(e.isMenuVisible());
        c.setCategoryType(e.getCategoryType());
        c.setHeroImageUrl(e.getHeroImageUrl());
        return c;
    }
}
```

- [ ] **Step 4: CategoryDto.java — remove the nested variant-field records**

Replace the whole file:

```java
package com.pilarestilo.category.application.dto;

import com.pilarestilo.category.domain.model.Category;

import java.util.UUID;

public record CategoryDto(
        UUID id,
        String slug,
        String nameEs,
        String nameEn,
        UUID parentId,
        int sortOrder,
        boolean active,
        boolean featured,
        String imageUrl,
        boolean menuVisible,
        String categoryType,
        String heroImageUrl
) {
    public static CategoryDto from(Category c) {
        return new CategoryDto(
                c.getId(), c.getSlug(), c.getNameEs(), c.getNameEn(),
                c.getParentId(), c.getSortOrder(), c.isActive(), c.isFeatured(), c.getImageUrl(),
                c.isMenuVisible(),
                c.getCategoryType() != null ? c.getCategoryType().name() : "GENERIC",
                c.getHeroImageUrl()
        );
    }
}
```

- [ ] **Step 5: CategoryTreeNode.java — remove the same two fields**

Replace the whole file:

```java
package com.pilarestilo.category.application.dto;

import java.util.List;
import java.util.UUID;

public record CategoryTreeNode(
        UUID id,
        UUID parentId,
        String slug,
        String nameEs,
        String nameEn,
        int sortOrder,
        boolean active,
        boolean featured,
        String imageUrl,
        boolean menuVisible,
        String categoryType,
        String heroImageUrl,
        List<CategoryTreeNode> children
) {}
```

- [ ] **Step 6: CreateCategoryUseCase.java — revert to the pre-feature signature**

Replace the whole file:

```java
package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.enums.CategoryType;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public CreateCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // One parameter per field the category form actually submits.
    @SuppressWarnings("java:S107")
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.CATEGORY_LIST, CacheNames.CATEGORY_TREE}, allEntries = true)
    public CategoryDto execute(String slug, String nameEs, String nameEn,
                               UUID parentId, int sortOrder, String imageUrl,
                               boolean active, boolean featured, boolean menuVisible,
                               String categoryType, String heroImageUrl) {
        if (categoryRepository.existsBySlug(slug)) {
            throw new DomainException("Category slug already exists: " + slug);
        }
        Category c = Category.create(slug, nameEs, nameEn, parentId, sortOrder, imageUrl);
        c.setActive(active);
        c.setFeatured(featured);
        CategoryType type;
        try {
            type = categoryType != null ? CategoryType.valueOf(categoryType) : CategoryType.GENERIC;
        } catch (IllegalArgumentException _) {
            throw new DomainException("Invalid categoryType: " + categoryType);
        }
        c.updateMenuMetadata(menuVisible, type, heroImageUrl);
        return CategoryDto.from(categoryRepository.save(c));
    }
}
```

- [ ] **Step 7: UpdateCategoryUseCase.java — the same treatment**

Replace the whole file:

```java
package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.enums.CategoryType;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public UpdateCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // One parameter per field the category form actually submits.
    @SuppressWarnings("java:S107")
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.CATEGORY_LIST, CacheNames.CATEGORY_TREE}, allEntries = true)
    public CategoryDto execute(UUID id, String slug, String nameEs, String nameEn,
                               UUID parentId, int sortOrder, boolean active, boolean featured, String imageUrl,
                               boolean menuVisible, String categoryType, String heroImageUrl) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new DomainException("Category not found: " + id));
        c.update(slug, nameEs, nameEn, parentId, sortOrder, active, featured, imageUrl);
        CategoryType type;
        try {
            type = categoryType != null ? CategoryType.valueOf(categoryType) : CategoryType.GENERIC;
        } catch (IllegalArgumentException _) {
            throw new DomainException("Invalid categoryType: " + categoryType);
        }
        c.updateMenuMetadata(menuVisible, type, heroImageUrl);
        return CategoryDto.from(categoryRepository.save(c));
    }
}
```

- [ ] **Step 8: GetCategoryTreeUseCase.java — drop the two trailing constructor args**

Replace the whole file:

```java
package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryTreeNode;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetCategoryTreeUseCase {

    private final CategoryRepository categoryRepository;

    public GetCategoryTreeUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATEGORY_TREE, sync = true)
    public List<CategoryTreeNode> execute() {
        List<Category> all = categoryRepository.findAll().stream()
                .filter(Category::isActive)
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .toList();

        Map<java.util.UUID, List<Category>> byParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        return all.stream()
                .filter(c -> c.getParentId() == null)
                .map(root -> toNode(root, byParent))
                .toList();
    }

    private CategoryTreeNode toNode(Category c, Map<java.util.UUID, List<Category>> byParent) {
        List<CategoryTreeNode> children = byParent.getOrDefault(c.getId(), List.of()).stream()
                .map(child -> toNode(child, byParent))
                .toList();
        return new CategoryTreeNode(
                c.getId(), c.getParentId(), c.getSlug(), c.getNameEs(), c.getNameEn(),
                c.getSortOrder(), c.isActive(), c.isFeatured(), c.getImageUrl(),
                c.isMenuVisible(),
                c.getCategoryType() != null ? c.getCategoryType().name() : "GENERIC",
                c.getHeroImageUrl(),
                children
        );
    }
}
```

- [ ] **Step 9: CategoryController.java — drop the 3 trailing args from create/update**

In `backend/src/main/java/com/pilarestilo/category/infrastructure/web/controllers/CategoryController.java`,
change only the two use-case calls:

```java
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody CreateCategoryRequest req) {
        CategoryDto dto = createCategory.execute(
                req.slug(), req.nameEs(), req.nameEn(),
                req.parentId(), req.sortOrder(), req.imageUrl(),
                req.active(), req.featured(), req.menuVisible(), req.categoryType(), req.heroImageUrl()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
```

```java
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public CategoryDto update(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest req) {
        return updateCategory.execute(
                id, req.slug(), req.nameEs(), req.nameEn(),
                req.parentId(), req.sortOrder(), req.active(), req.featured(), req.imageUrl(),
                req.menuVisible(), req.categoryType(), req.heroImageUrl()
        );
    }
```

Everything else in the file (the `@PreAuthorize` annotations, `reorder`, `delete`, `list`, `tree`,
`featured`) is unchanged.

- [ ] **Step 10: CreateCategoryRequest.java / UpdateCategoryRequest.java — drop the 3 fields**

Replace `backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/CreateCategoryRequest.java`:

```java
package com.pilarestilo.category.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank String slug,
        @NotBlank String nameEs,
        @NotBlank String nameEn,
        UUID parentId,
        @PositiveOrZero int sortOrder,
        String imageUrl,
        boolean active,
        boolean featured,
        boolean menuVisible,
        @Pattern(regexp = "GENERIC|CLOTHING|SHOES|JEWELRY|ACCESSORY|COLLECTION|SEASON", message = "invalid categoryType") String categoryType,
        String heroImageUrl
) {}
```

Replace `backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/UpdateCategoryRequest.java`:

```java
package com.pilarestilo.category.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record UpdateCategoryRequest(
        @NotBlank String slug,
        @NotBlank String nameEs,
        @NotBlank String nameEn,
        UUID parentId,
        @PositiveOrZero int sortOrder,
        boolean active,
        boolean featured,
        String imageUrl,
        boolean menuVisible,
        @Pattern(regexp = "GENERIC|CLOTHING|SHOES|JEWELRY|ACCESSORY|COLLECTION|SEASON", message = "invalid categoryType") String categoryType,
        String heroImageUrl
) {}
```

- [ ] **Step 11: Delete the obsolete production files**

```bash
git rm backend/src/main/java/com/pilarestilo/category/infrastructure/web/requests/CategoryVariantFieldRequest.java
git rm backend/src/main/java/com/pilarestilo/category/domain/valueobjects/CategoryVariantFieldConfig.java
git rm backend/src/main/java/com/pilarestilo/category/domain/model/ShapeCategoryResolver.java
git rm backend/src/main/java/com/pilarestilo/product/application/CategoryVariantFieldValidator.java
```

- [ ] **Step 12: Delete the obsolete tests**

```bash
git rm backend/src/test/java/com/pilarestilo/category/domain/valueobjects/CategoryVariantFieldConfigTest.java
git rm backend/src/test/java/com/pilarestilo/category/domain/model/CategoryVariantFieldConfigMutatorTest.java
git rm backend/src/test/java/com/pilarestilo/category/domain/model/ShapeCategoryResolverTest.java
git rm backend/src/test/java/com/pilarestilo/category/application/usecases/CreateCategoryUseCaseVariantFieldTest.java
git rm backend/src/test/java/com/pilarestilo/product/application/CategoryVariantFieldValidatorTest.java
```

- [ ] **Step 13: Run the full backend test suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS, 0 failures. This is the first point in the plan where the whole backend
compiles and every remaining test (old and new) is green — Tasks 1-8 left dangling references to
the files this task deletes, so this is also the first clean compile since Task 1.

- [ ] **Step 14: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/category/
git commit -m "refactor: remove category-derived variant field config entirely"
```

---

## Task 10: product-service parity

**Files:**
- Create: `services/product-service/src/main/java/com/pilarestilo/productservice/persistence/VariantTemplateEntity.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/persistence/CategoryEntity.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/persistence/ProductEntity.java`
- Modify: `services/product-service/src/main/java/com/pilarestilo/productservice/web/ProductMapper.java`
- Modify: `services/product-service/src/test/java/com/pilarestilo/productservice/web/ProductMapperTest.java`

**Interfaces:**
- Consumes: table `variant_templates` (Task 1), column `products.variant_template_id` (Task 1) —
  product-service shares the monolith's database and mirrors its shape with no shared code, per
  CLAUDE.md's cross-repo-boundary rule.
- Produces: `ProductMapper.toDto(ProductEntity)` resolves `variantFieldConfig` from
  `entity.getVariantTemplate()` instead of walking `entity.getCategories()`. `ProductDto` (product-
  service) is unchanged in shape — it never gains `variantTemplateId` since this service is
  read-only and never feeds `ProductForm.tsx`.

Must ship in the same commit set as Task 8/9 (never separately) — this is the same rule already
applied twice this cycle for the category-derived config.

- [ ] **Step 1: Write the failing test**

Replace `services/product-service/src/test/java/com/pilarestilo/productservice/web/ProductMapperTest.java`
in full: keep the first test but drop the now-nonexistent `definesVariantFields`/
`variantFieldConfig` fixture lines on `CategoryEntity`; replace the two shape-category tests with
template-based equivalents.

```java
package com.pilarestilo.productservice.web;

import com.pilarestilo.productservice.persistence.CategoryEntity;
import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.persistence.ProductSizeStockEmbeddable;
import com.pilarestilo.productservice.persistence.ProductVariantEmbeddable;
import com.pilarestilo.productservice.persistence.VariantTemplateEntity;
import com.pilarestilo.productservice.web.dto.ProductDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductMapperTest {

    @Test
    void maps_entity_to_dto_with_sorted_categories_and_variants() {
        ProductEntity entity = new ProductEntity();
        UUID productId = UUID.randomUUID();
        ReflectionTestUtils.setField(entity, "id", productId);
        ReflectionTestUtils.setField(entity, "name", "Cartera");
        ReflectionTestUtils.setField(entity, "description", "Cartera cuero");
        ReflectionTestUtils.setField(entity, "priceAmount", new BigDecimal("79000.00"));
        ReflectionTestUtils.setField(entity, "priceCurrency", "CLP");
        ReflectionTestUtils.setField(entity, "listPriceAmount", new BigDecimal("99000.00"));
        ReflectionTestUtils.setField(entity, "listPriceCurrency", "CLP");
        ReflectionTestUtils.setField(entity, "imageUrl", "https://img");
        ReflectionTestUtils.setField(entity, "condition", "NUEVO");
        ReflectionTestUtils.setField(entity, "brand", "Prada");
        ReflectionTestUtils.setField(entity, "stock", 3);
        ReflectionTestUtils.setField(entity, "active", true);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "avgRating", new BigDecimal("4.50"));
        ReflectionTestUtils.setField(entity, "reviewCount", 10);
        ReflectionTestUtils.setField(entity, "shippingOriginZone", "LOCAL");

        ProductSizeStockEmbeddable size = new ProductSizeStockEmbeddable();
        ReflectionTestUtils.setField(size, "size", "M");
        ReflectionTestUtils.setField(size, "stock", 2);
        ProductVariantEmbeddable variant = new ProductVariantEmbeddable();
        ReflectionTestUtils.setField(variant, "color", "Negro");
        ReflectionTestUtils.setField(variant, "size", "M");
        ReflectionTestUtils.setField(variant, "stockOnHand", 5);
        ReflectionTestUtils.setField(variant, "stockReserved", 2);
        ReflectionTestUtils.setField(entity, "sizeStocks", List.of(size));
        ReflectionTestUtils.setField(entity, "variants", List.of(variant));

        CategoryEntity catB = new CategoryEntity();
        ReflectionTestUtils.setField(catB, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(catB, "slug", "zapatos");
        ReflectionTestUtils.setField(catB, "categoryType", "SHOES");
        CategoryEntity catA = new CategoryEntity();
        ReflectionTestUtils.setField(catA, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(catA, "slug", "accesorios");
        ReflectionTestUtils.setField(catA, "categoryType", "ACCESSORY");
        ReflectionTestUtils.setField(entity, "categories", Set.of(catB, catA));

        ProductDto dto = ProductMapper.toDto(entity);

        assertEquals(productId, dto.id());
        assertEquals("Cartera", dto.name());
        assertEquals(1, dto.sizeStocks().size());
        assertEquals("M", dto.sizeStocks().get(0).size());
        assertEquals(1, dto.variants().size());
        assertEquals("Negro", dto.variants().get(0).color());
        assertEquals(5, dto.variants().get(0).stock());
        assertEquals(5, dto.variants().get(0).stockOnHand());
        assertEquals(2, dto.variants().get(0).stockReserved());
        assertEquals(3, dto.variants().get(0).stockAvailable());
        assertEquals(List.of("accesorios", "zapatos"), dto.categorySlugs());
        assertEquals("Variante", dto.variantFieldConfig().primary().label());
    }

    @Test
    void maps_variantFieldConfig_fromAssignedTemplate() {
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
        ReflectionTestUtils.setField(entity, "categories", Set.of(mujer));

        VariantTemplateEntity zapatosTemplate = new VariantTemplateEntity();
        ReflectionTestUtils.setField(zapatosTemplate, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(zapatosTemplate, "name", "Zapatos");
        ReflectionTestUtils.setField(zapatosTemplate, "fieldConfig", Map.of(
                "primary", Map.of("label", "Color", "inputType", "FREE_TEXT", "options", List.of(),
                        "allowMultiple", false, "allowCustom", true),
                "secondary", Map.of("label", "Numero", "inputType", "RANGE", "options", List.of(),
                        "min", 34, "max", 43, "allowMultiple", true, "allowCustom", true)
        ));
        ReflectionTestUtils.setField(entity, "variantTemplate", zapatosTemplate);

        ProductDto dto = ProductMapper.toDto(entity);

        assertEquals("Numero", dto.variantFieldConfig().secondary().label());
        assertEquals("RANGE", dto.variantFieldConfig().secondary().inputType());
        assertEquals(34, dto.variantFieldConfig().secondary().min());
        assertEquals(43, dto.variantFieldConfig().secondary().max());
    }

    @Test
    void maps_variantFieldConfig_fallsBackToGenericWhenNoTemplateAssigned() {
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
        ReflectionTestUtils.setField(entity, "categories", Set.of(mujer));

        ProductDto dto = ProductMapper.toDto(entity);

        assertEquals("Variante", dto.variantFieldConfig().primary().label());
        assertEquals("Detalle", dto.variantFieldConfig().secondary().label());
        assertEquals("FREE_TEXT", dto.variantFieldConfig().primary().inputType());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/product-service && mvn test -Dtest=ProductMapperTest`
Expected: FAIL to compile — `VariantTemplateEntity` does not exist yet and `ProductEntity` has no
`variantTemplate` field.

- [ ] **Step 3: Write the read-only VariantTemplateEntity mirror**

```java
package com.pilarestilo.productservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "variant_templates")
public class VariantTemplateEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_config", columnDefinition = "jsonb")
    private Map<String, Object> fieldConfig;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getFieldConfig() {
        return fieldConfig;
    }
}
```

- [ ] **Step 4: Strip CategoryEntity.java down to what product-service still needs**

Replace `services/product-service/src/main/java/com/pilarestilo/productservice/persistence/CategoryEntity.java`:

```java
package com.pilarestilo.productservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "name_es", nullable = false, length = 120)
    private String nameEs;

    @Column(name = "name_en", nullable = false, length = 120)
    private String nameEn;

    @Column(name = "category_type", nullable = false, length = 32)
    private String categoryType;

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getNameEs() {
        return nameEs;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getCategoryType() {
        return categoryType;
    }
}
```

- [ ] **Step 5: Add the variantTemplate relationship to ProductEntity.java**

In `services/product-service/src/main/java/com/pilarestilo/productservice/persistence/ProductEntity.java`,
add the field and getter (categories/sizeStocks/variants stay untouched):

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_template_id")
    private VariantTemplateEntity variantTemplate;
```

placed immediately after the existing `categories` field, and:

```java
    public VariantTemplateEntity getVariantTemplate() {
        return variantTemplate;
    }
```

placed immediately after `getCategories()`. Add `import jakarta.persistence.ManyToOne;` to the
existing import block (`FetchType`, `JoinColumn` are already imported).

- [ ] **Step 6: Rewrite ProductMapper.java's resolution to use the template**

Replace the whole file:

```java
package com.pilarestilo.productservice.web;

import com.pilarestilo.productservice.persistence.CategoryEntity;
import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.persistence.VariantTemplateEntity;
import com.pilarestilo.productservice.web.dto.ProductDto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class ProductMapper {

    private ProductMapper() {}

    static ProductDto toDto(ProductEntity entity) {
        List<ProductDto.SizeStockDto> sizeStocks = entity.getSizeStocks().stream()
                .map(s -> new ProductDto.SizeStockDto(s.getSize(), s.getStock()))
                .toList();

        List<ProductDto.VariantDto> variants = entity.getVariants().stream()
                .map(v -> new ProductDto.VariantDto(
                        v.getColor(),
                        v.getSize(),
                        v.getStockOnHand(),
                        v.getStockOnHand(),
                        v.getStockReserved(),
                        v.available()
                ))
                .toList();

        List<String> categorySlugs = entity.getCategories().stream()
                .map(CategoryEntity::getSlug)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new ProductDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPriceAmount(),
                entity.getPriceCurrency(),
                entity.getListPriceAmount(),
                entity.getListPriceCurrency(),
                entity.getImageUrl(),
                entity.getCondition(),
                entity.getBrand(),
                entity.getStock(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getAvgRating(),
                entity.getReviewCount(),
                entity.getShippingOriginZone(),
                sizeStocks,
                categorySlugs,
                resolveVariantFieldConfig(entity.getVariantTemplate()),
                variants
        );
    }

    private static ProductDto.ProductVariantFieldConfigDto resolveVariantFieldConfig(VariantTemplateEntity template) {
        if (template == null) {
            return genericFallback();
        }
        return toConfigDto(template.getFieldConfig());
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

`ProductDto.java` (product-service) is unchanged — its record shape already carries
`ProductVariantFieldConfigDto variantFieldConfig` fed by this method, and this service never
serves `ProductForm.tsx`, so it does not need `variantTemplateId`.

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd services/product-service && mvn test -Dtest=ProductMapperTest`
Expected: PASS, 3/3.

- [ ] **Step 8: Run the full product-service test suite**

Run: `cd services/product-service && mvn test`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 9: Commit**

```bash
git add services/product-service/
git commit -m "feat: mirror variant-template resolution in product-service"
```

---

## Task 11: frontend api.ts — rename DTOs, add fields, new VariantTemplate CRUD API

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Produces: `VariantFieldInputType` (unchanged name), `VariantFieldDto` (renamed from
  `CategoryVariantFieldDto`), `VariantFieldConfigDto` (renamed from `CategoryVariantFieldConfigDto`);
  `CategoryDto` loses `definesVariantFields`/`variantFieldConfig`; `CreateCategoryRequest` loses
  `definesVariantFields`/`primary`/`secondary`; `ProductDto.variantFieldConfig` retyped to
  `VariantFieldConfigDto | null` and gains `variantTemplateId?: string | null`;
  `CreateProductRequest`/`UpdateProductRequest` gain `variantTemplateId?: string | null`;
  `VariantTemplateDto { id, name, config: VariantFieldConfigDto }`,
  `CreateVariantTemplateRequest { name, primary: VariantFieldDto, secondary: VariantFieldDto }`,
  and `getVariantTemplates(token)`/`createVariantTemplate(data, token)`/
  `updateVariantTemplate(id, data, token)`/`deleteVariantTemplate(id, token)`. Task 12
  (`variantSchema.ts`) consumes `VariantFieldConfigDto`/`VariantFieldDto`; Tasks 14-15 (frontend
  admin islands) consume the new `VariantTemplateDto`/CRUD functions and the retyped `ProductDto`/
  request fields.

This is a single large file; every change below is a targeted find-and-replace within it, not a
full-file rewrite.

- [ ] **Step 1: Retype ProductDto's variantFieldConfig and add variantTemplateId**

Find:

```typescript
export interface ProductDto {
  id: string;
  name: string;
  description: string;
  price: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl: string;
  condition: 'NEW' | 'USED';
  brand: string;
  stock: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  avgRating?: number;
  reviewCount?: number;
  shippingOriginZone?: 'LOCAL' | 'REGIONAL' | 'NACIONAL';
  sizeStocks?: SizeStockDto[];
  categorySlugs?: string[];
  categoryTypes?: CategoryType[];
  variantFieldConfig?: CategoryVariantFieldConfigDto | null;
  variants?: ProductVariantDto[];
}
```

Replace with:

```typescript
export interface ProductDto {
  id: string;
  name: string;
  description: string;
  price: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl: string;
  condition: 'NEW' | 'USED';
  brand: string;
  stock: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  avgRating?: number;
  reviewCount?: number;
  shippingOriginZone?: 'LOCAL' | 'REGIONAL' | 'NACIONAL';
  sizeStocks?: SizeStockDto[];
  categorySlugs?: string[];
  categoryTypes?: CategoryType[];
  variantTemplateId?: string | null;
  variantFieldConfig?: VariantFieldConfigDto | null;
  variants?: ProductVariantDto[];
}
```

- [ ] **Step 2: Add variantTemplateId to CreateProductRequest and UpdateProductRequest**

Find:

```typescript
export interface CreateProductRequest {
  name: string;
  description: string;
  price: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl: string;
  condition: 'NEW' | 'USED';
  brand: string;
  stock: number;
  active: boolean;
  categoryIds?: string[];
  variants?: ProductVariantDto[];
}

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  price?: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl?: string;
  condition?: 'NEW' | 'USED';
  brand?: string;
  stock?: number;
  active?: boolean;
  categoryIds?: string[];
  variants?: ProductVariantDto[];
}
```

Replace with:

```typescript
export interface CreateProductRequest {
  name: string;
  description: string;
  price: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl: string;
  condition: 'NEW' | 'USED';
  brand: string;
  stock: number;
  active: boolean;
  categoryIds?: string[];
  variantTemplateId?: string | null;
  variants?: ProductVariantDto[];
}

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  price?: MoneyDto;
  listPrice?: MoneyDto;
  imageUrl?: string;
  condition?: 'NEW' | 'USED';
  brand?: string;
  stock?: number;
  active?: boolean;
  categoryIds?: string[];
  variantTemplateId?: string | null;
  variants?: ProductVariantDto[];
}
```

- [ ] **Step 3: Rename the two variant-field DTOs and strip CategoryDto/CreateCategoryRequest**

Find:

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

export interface CategoryDto {
  id: string;
  slug: string;
  nameEs: string;
  nameEn: string;
  parentId: string | null;
  sortOrder: number;
  active: boolean;
  featured: boolean;
  imageUrl?: string;
  menuVisible: boolean;
  categoryType: CategoryType;
  heroImageUrl?: string;
  definesVariantFields: boolean;
  variantFieldConfig: CategoryVariantFieldConfigDto | null;
}
```

Replace with:

```typescript
export type VariantFieldInputType = 'FREE_TEXT' | 'OPTIONS' | 'RANGE';

export interface VariantFieldDto {
  label: string;
  inputType: VariantFieldInputType;
  options: string[];
  min: number | null;
  max: number | null;
  allowMultiple: boolean;
  allowCustom: boolean;
}

export interface VariantFieldConfigDto {
  primary: VariantFieldDto;
  secondary: VariantFieldDto;
}

export interface CategoryDto {
  id: string;
  slug: string;
  nameEs: string;
  nameEn: string;
  parentId: string | null;
  sortOrder: number;
  active: boolean;
  featured: boolean;
  imageUrl?: string;
  menuVisible: boolean;
  categoryType: CategoryType;
  heroImageUrl?: string;
}
```

- [ ] **Step 4: Strip CreateCategoryRequest's variant-field fields**

Find:

```typescript
export interface CreateCategoryRequest {
  slug: string;
  nameEs: string;
  nameEn: string;
  parentId?: string;
  sortOrder: number;
  imageUrl?: string;
  active?: boolean;
  featured?: boolean;
  menuVisible?: boolean;
  categoryType?: CategoryType;
  heroImageUrl?: string;
  definesVariantFields?: boolean;
  primary?: CategoryVariantFieldDto;
  secondary?: CategoryVariantFieldDto;
}
```

Replace with:

```typescript
export interface CreateCategoryRequest {
  slug: string;
  nameEs: string;
  nameEn: string;
  parentId?: string;
  sortOrder: number;
  imageUrl?: string;
  active?: boolean;
  featured?: boolean;
  menuVisible?: boolean;
  categoryType?: CategoryType;
  heroImageUrl?: string;
}
```

- [ ] **Step 5: Add the VariantTemplate DTOs and CRUD API functions**

Insert immediately after the `reorderCategories` function at the end of the "Category API" block
(before the `// ─── Navigation Section Admin API ───` comment):

```typescript
// ─── Variant Template API ──────────────────────────────────────────────────────

export interface VariantTemplateDto {
  id: string;
  name: string;
  config: VariantFieldConfigDto;
}

export interface CreateVariantTemplateRequest {
  name: string;
  primary: VariantFieldDto;
  secondary: VariantFieldDto;
}

export async function getVariantTemplates(token: string): Promise<VariantTemplateDto[]> {
  try {
    return await apiFetch<VariantTemplateDto[]>('/variant-templates', {
      headers: authHeaders(token),
    });
  } catch {
    return [];
  }
}

export async function createVariantTemplate(
  data: CreateVariantTemplateRequest,
  token: string
): Promise<VariantTemplateDto> {
  return apiFetch<VariantTemplateDto>('/variant-templates', {
    method: 'POST',
    body: JSON.stringify(data),
    headers: authHeaders(token),
  });
}

export async function updateVariantTemplate(
  id: string,
  data: CreateVariantTemplateRequest,
  token: string
): Promise<VariantTemplateDto> {
  return apiFetch<VariantTemplateDto>(`/variant-templates/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
    headers: authHeaders(token),
  });
}

export async function deleteVariantTemplate(id: string, token: string): Promise<void> {
  await apiFetch<void>(`/variant-templates/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}
```

`authHeaders` is the existing private helper (`function authHeaders(token?: string):
Record<string, string> { return token ? { Authorization: \`Bearer ${token}\` } : {}; }`) already
used by `getPendingDocumentCount` — every method on this resource requires ADMIN, unlike
categories, so even the GET needs the token.

- [ ] **Step 6: Typecheck**

Run: `cd frontend && npm run build`
Expected: fails at this point — `CategoryTree.tsx`, `ProductForm.tsx`, `variantSchema.ts`, and
`ProductVariantSelector.tsx` (Tasks 12, 13, 15, 16) still import `CategoryVariantFieldDto`/
`CategoryVariantFieldConfigDto` and reference `CategoryDto.definesVariantFields`/
`variantFieldConfig`/`CreateCategoryRequest.definesVariantFields`, which this task removed. This is
expected — those tasks fix it. Confirm the *only* errors reported are in those four files (no
new errors elsewhere), which proves this task's edits are otherwise correct.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat: add VariantTemplate types and CRUD API to api.ts"
```

---

## Task 12: variantSchema.ts — drop category resolution, retype buildVariantSchema

**Files:**
- Modify: `frontend/src/lib/variantSchema.ts`
- Modify: `frontend/src/lib/__tests__/variantSchema.test.ts`

**Interfaces:**
- Consumes: `VariantFieldConfigDto`/`VariantFieldDto` (Task 11).
- Produces: `buildVariantSchema(config: VariantFieldConfigDto | null, key = 'GENERIC'):
  VariantSchema` (same behavior, retyped parameter) remains the only category/template-facing
  export that changes; every other export (`getPrimaryAttribute`, `getAttributeValue(s)`,
  `createEmptyVariantSelections`, `normalizeAttributeValues`, `legacyVariantToSelections`,
  `selectionsToLegacyVariant`, `toVariantAttributeRecord`, `summarizeVariantAttributeValues`)
  is untouched. `findShapeCategory`, `resolveVariantFieldConfig`, `allowedCategoryIdsFor` are
  deleted. Task 15 (`ProductForm.tsx`) stops importing the two deleted functions and calls
  `buildVariantSchema` with a template's `config` instead of a category-resolved one.

- [ ] **Step 1: Update the failing test first — drop the allowedCategoryIdsFor suite**

Replace `frontend/src/lib/__tests__/variantSchema.test.ts` in full:

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
  buildVariantSchema,
} from '../variantSchema';
import type { VariantFieldConfigDto } from '../api';

/**
 * Characterization tests for the multi-value/custom-value composition logic -- unchanged by the
 * category-to-template decoupling, only the type of buildVariantSchema's parameter changed.
 */
const CLOTHING_CONFIG: VariantFieldConfigDto = {
  primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
  secondary: {
    label: 'Talla', inputType: 'OPTIONS',
    options: ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL', 'UNICO'],
    min: null, max: null, allowMultiple: true, allowCustom: true,
  },
};

describe('variantSchema: multi-value composition (must survive the template-driven rewrite)', () => {
  it('composes multiple selected clothing sizes into one hyphen-joined stored value', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
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
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const secondary = getSecondaryAttribute(schema);
    const selections = legacyVariantToSelections(
      { color: 'Negro', size: 'S-M-L', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      schema,
    );
    expect(selections[secondary.code].sort()).toEqual(['L', 'M', 'S']);
  });

  it('accepts a custom value alongside the fixed options list', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const secondary = getSecondaryAttribute(schema);
    const normalized = normalizeAttributeValues(secondary, ['Talla especial']);
    expect(normalized).toEqual(['Talla especial']);
  });

  it('defaults to UNICO for the generic pair when nothing is selected', () => {
    const schema = buildVariantSchema(null);
    const selections = createEmptyVariantSelections(schema);
    const secondary = getSecondaryAttribute(schema);
    expect(selections[secondary.code]).toEqual(['UNICO']);
  });

  it('summarizes multiple variants secondary values, deduped and sorted', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const secondary = getSecondaryAttribute(schema);
    const summary = summarizeVariantAttributeValues(
      [
        { color: 'Negro', size: 'M', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
        { color: 'Rojo', size: 'S-L', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      ],
      schema,
      secondary.code,
    );
    expect(summary).toBe('S-M-L');
  });

  it('round-trips a single-value field (color) without splitting on hyphen', () => {
    const schema = buildVariantSchema(CLOTHING_CONFIG);
    const record = toVariantAttributeRecord(
      { color: 'Azul-Marino', size: 'M', stock: 1, stockOnHand: 1, stockReserved: 0, stockAvailable: 1 },
      schema,
    );
    expect(record[getPrimaryAttribute(schema).code]).toBe('Azul-Marino');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/lib/__tests__/variantSchema.test.ts`
Expected: FAIL to compile — `variantSchema.ts` still types `buildVariantSchema`'s parameter as
`CategoryVariantFieldConfigDto`, which `api.ts` no longer exports (Task 11 renamed it).

- [ ] **Step 3: Rewrite variantSchema.ts**

Replace the whole file:

```typescript
import type { VariantFieldConfigDto, VariantFieldDto, ProductVariantDto } from './api';

export interface VariantAttributeOption {
  value: string;
  label: string;
  position: number;
}

export interface CategoryAttributeDefinition {
  code: string;
  label: string;
  type: 'text' | 'choice';
  options: VariantAttributeOption[];
  required: boolean;
  position: number;
  allowMultiple?: boolean;
  allowCustom?: boolean;
  placeholder?: string;
  legacyField: 'color' | 'size';
  defaultValues?: string[];
  summaryJoiner?: string;
}

export interface VariantSchema {
  key: string;
  /** What the product is, in the words the shop uses. The picker lists this, not the enum. */
  noun: string;
  title: string;
  attributes: [CategoryAttributeDefinition, CategoryAttributeDefinition];
}

export type VariantAttributeSelections = Record<string, string[]>;
export type VariantAttributeRecord = Record<string, string>;

function optionList(values: string[]): VariantAttributeOption[] {
  return values.map((value, index) => ({ value, label: value, position: index }));
}

function normalizeToken(value: string): string {
  return value.trim().replace(/\s+/g, ' ');
}

function buildOptionIndex(attribute: CategoryAttributeDefinition): Map<string, VariantAttributeOption> {
  return new Map(attribute.options.map((option) => [option.value.toLowerCase(), option]));
}

function sortAttributeValues(attribute: CategoryAttributeDefinition, values: string[]): string[] {
  const unique = Array.from(new Set(values.map(normalizeToken).filter(Boolean)));
  const optionIndex = buildOptionIndex(attribute);
  return unique.sort((left, right) => {
    const leftOption = optionIndex.get(left.toLowerCase());
    const rightOption = optionIndex.get(right.toLowerCase());
    if (leftOption || rightOption) {
      return (leftOption?.position ?? 999) - (rightOption?.position ?? 999);
    }
    const leftNumber = Number(left);
    const rightNumber = Number(right);
    if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) {
      return leftNumber - rightNumber;
    }
    return left.localeCompare(right, 'es', { sensitivity: 'base', numeric: true });
  });
}

export function getPrimaryAttribute(schema: VariantSchema): CategoryAttributeDefinition {
  return schema.attributes[0];
}

export function getSecondaryAttribute(schema: VariantSchema): CategoryAttributeDefinition {
  return schema.attributes[1];
}

export function getAttributeValue(
  selections: VariantAttributeSelections,
  attribute: CategoryAttributeDefinition,
): string {
  return selections[attribute.code]?.[0] ?? '';
}

export function getAttributeValues(
  selections: VariantAttributeSelections,
  attribute: CategoryAttributeDefinition,
): string[] {
  return selections[attribute.code] ?? [];
}

export function createEmptyVariantSelections(schema: VariantSchema): VariantAttributeSelections {
  return Object.fromEntries(
    schema.attributes.map((attribute) => [
      attribute.code,
      sortAttributeValues(attribute, attribute.defaultValues ?? []),
    ]),
  );
}

export function normalizeAttributeValues(
  attribute: CategoryAttributeDefinition,
  rawValues: string[],
): string[] {
  const optionIndex = buildOptionIndex(attribute);
  const canonical = rawValues
    .map(normalizeToken)
    .filter(Boolean)
    .map((value) => optionIndex.get(value.toLowerCase())?.value ?? value);
  return sortAttributeValues(attribute, canonical);
}

function composeStoredAttributeValue(
  attribute: CategoryAttributeDefinition,
  values: string[],
): string {
  const normalized = normalizeAttributeValues(attribute, values);
  if (normalized.length === 0) return '';
  if (attribute.allowMultiple) {
    return normalized.join(attribute.summaryJoiner ?? '-');
  }
  return normalized[0];
}

function parseStoredAttributeValue(
  attribute: CategoryAttributeDefinition,
  rawValue: string | null | undefined,
): string[] {
  const value = normalizeToken(rawValue ?? '');
  if (!value) {
    return sortAttributeValues(attribute, attribute.defaultValues ?? []);
  }
  const parts = attribute.allowMultiple
    ? value.split(attribute.summaryJoiner ?? '-').map(normalizeToken)
    : [value];
  return normalizeAttributeValues(attribute, parts);
}

export function legacyVariantToSelections(
  variant: ProductVariantDto,
  schema: VariantSchema,
): VariantAttributeSelections {
  const entries = schema.attributes.map((attribute) => {
    const raw = attribute.legacyField === 'color' ? variant.color : variant.size;
    return [attribute.code, parseStoredAttributeValue(attribute, raw)] as const;
  });
  return Object.fromEntries(entries);
}

export function selectionsToLegacyVariant(
  selections: VariantAttributeSelections,
  stock: number,
  schema: VariantSchema,
): ProductVariantDto {
  const primary = getPrimaryAttribute(schema);
  const secondary = getSecondaryAttribute(schema);
  const color = composeStoredAttributeValue(primary, selections[primary.code] ?? []);
  const size = composeStoredAttributeValue(secondary, selections[secondary.code] ?? []);
  return {
    color,
    size,
    stock,
    stockOnHand: stock,
    stockReserved: 0,
    stockAvailable: stock,
  };
}

export function toVariantAttributeRecord(
  variant: ProductVariantDto,
  schema: VariantSchema,
): VariantAttributeRecord {
  return Object.fromEntries(
    schema.attributes.map((attribute) => {
      const raw = attribute.legacyField === 'color' ? variant.color : variant.size;
      return [attribute.code, composeStoredAttributeValue(attribute, parseStoredAttributeValue(attribute, raw))];
    }),
  );
}

export function summarizeVariantAttributeValues(
  variants: ProductVariantDto[] | undefined,
  schema: VariantSchema,
  attributeCode: string,
): string {
  if (!Array.isArray(variants) || variants.length === 0) return '';
  const attribute = schema.attributes.find((item) => item.code === attributeCode);
  if (!attribute) return '';
  const values = variants.flatMap((variant) => {
    const raw = attribute.legacyField === 'color' ? variant.color : variant.size;
    return parseStoredAttributeValue(attribute, raw);
  });
  const normalized = sortAttributeValues(attribute, values);
  if (normalized.length === 0) return '';
  return normalized.join(attribute.summaryJoiner ?? ' / ');
}

const GENERIC_FALLBACK: VariantFieldConfigDto = {
  primary: { label: 'Variante', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true },
  secondary: { label: 'Detalle', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true },
};

function optionsFor(field: VariantFieldDto): VariantAttributeOption[] {
  if (field.inputType === 'OPTIONS') {
    return optionList(field.options);
  }
  if (field.inputType === 'RANGE' && field.min != null && field.max != null) {
    const min = field.min;
    const max = field.max;
    return optionList(Array.from({ length: max - min + 1 }, (_, i) => String(min + i)));
  }
  return [];
}

function fieldToAttribute(
  field: VariantFieldDto,
  code: string,
  legacyField: 'color' | 'size',
): CategoryAttributeDefinition {
  const options = optionsFor(field);
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

/** Builds the two-attribute schema a variant template's (or the generic fallback's) config describes. */
export function buildVariantSchema(config: VariantFieldConfigDto | null, key = 'GENERIC'): VariantSchema {
  const resolved = config ?? GENERIC_FALLBACK;
  const secondaryAttribute = fieldToAttribute(resolved.secondary, 'secondary', 'size');
  if (!config) {
    // A product with no variant template is exactly the case the old GENERIC/ACCESSORY/
    // COLLECTION/SEASON schemas covered, and all of them pre-filled the detail field with
    // "UNICO" -- preserved here so an admin creating a variant for an unassigned product sees the
    // same starting point as before this rewrite.
    secondaryAttribute.defaultValues = ['UNICO'];
  }
  return {
    key,
    noun: 'Variante',
    title: `${resolved.primary.label} + ${resolved.secondary.label} + stock`,
    attributes: [
      fieldToAttribute(resolved.primary, 'primary', 'color'),
      secondaryAttribute,
    ],
  };
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/lib/__tests__/variantSchema.test.ts`
Expected: PASS, 6/6.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/variantSchema.ts frontend/src/lib/__tests__/variantSchema.test.ts
git commit -m "refactor: drop category-derived resolution from variantSchema.ts"
```

---

## Task 13: Extract VariantFieldEditor and revert CategoryTree.tsx

**Files:**
- Create: `frontend/src/islands/admin/VariantFieldEditor.tsx`
- Modify: `frontend/src/islands/admin/CategoryTree.tsx`
- Delete: `frontend/src/islands/admin/__tests__/CategoryTree.test.tsx`

**Interfaces:**
- Produces: `VariantFieldEditor({ fieldNumber, field, onChange }: { fieldNumber: 1 | 2; field:
  VariantFieldDto; onChange: (next: VariantFieldDto) => void })` as a default export from its own
  file. Task 14's `VariantTemplateTable.tsx` imports it.
- `CategoryTree.tsx` reverts to exactly what it looked like before the category-derived variant
  feature touched it (slug/name/order/hero/image/active/featured/menu fields only) — no more
  `VariantFieldEditor` embedded, no more "esta categoria define campos de variante" checkbox, no
  more variant badge on `CategoryRow`.

`CategoryTree.test.tsx` is deleted entirely — its whole subject (the category-embedded variant
field editor) no longer exists.

- [ ] **Step 1: Delete the obsolete test**

```bash
git rm frontend/src/islands/admin/__tests__/CategoryTree.test.tsx
```

- [ ] **Step 2: Create VariantFieldEditor.tsx**

```typescript
import type { VariantFieldDto, VariantFieldInputType } from '../../lib/api';

const INPUT_CLASS = 'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors';

export default function VariantFieldEditor({
  fieldNumber, field, onChange,
}: { readonly fieldNumber: 1 | 2; readonly field: VariantFieldDto; readonly onChange: (next: VariantFieldDto) => void }) {
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
        <span>Permitir combinar varios valores en una variante</span>
      </label>
      {field.inputType !== 'FREE_TEXT' && (
        <label className="inline-flex items-center gap-1.5 font-sans text-[0.68rem] text-pe-charcoal">
          <input type="checkbox" checked={field.allowCustom}
            onChange={(e) => onChange({ ...field, allowCustom: e.target.checked })} />
          <span>Permitir un valor fuera de la lista</span>
        </label>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Rewrite CategoryTree.tsx without the variant-field editor**

Replace the whole file:

```typescript
import { useState, useEffect } from 'react';
import { Plus, Edit3, Trash2, ChevronDown, ChevronRight, Loader2, Check, X, Star, GripVertical } from 'lucide-react';
import {
  DndContext, PointerSensor, useSensor, useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext, useSortable, verticalListSortingStrategy, arrayMove,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  getCategoryTree, createCategory, updateCategory, deleteCategory, reorderCategories,
  type CategoryTreeNode, type CategoryDto,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import ImageDropzone from './ImageDropzone';
import { useToast, Toaster } from './Toast';

type EditForm = {
  slug: string; nameEs: string; nameEn: string;
  parentId: string; sortOrder: string; imageUrl: string; active: boolean; featured: boolean;
  menuVisible: boolean; heroImageUrl: string;
  /** No UI control any more -- carried through unchanged so saving doesn't reset it. */
  categoryType: CategoryDto['categoryType'];
};

const EMPTY_FORM: EditForm = {
  slug: '', nameEs: '', nameEn: '', parentId: '', sortOrder: '0', imageUrl: '', active: true, featured: false,
  menuVisible: true, heroImageUrl: '', categoryType: 'GENERIC',
};

function fromDto(dto: CategoryDto): EditForm {
  return {
    slug: dto.slug, nameEs: dto.nameEs, nameEn: dto.nameEn,
    parentId: dto.parentId ?? '', sortOrder: String(dto.sortOrder),
    imageUrl: dto.imageUrl ?? '', active: dto.active, featured: dto.featured,
    menuVisible: dto.menuVisible, heroImageUrl: dto.heroImageUrl ?? '',
    categoryType: dto.categoryType,
  };
}

const INPUT_CLASS = 'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors';

function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '');
}

// ─── FormRow ─────────────────────────────────────────────────────────────────

interface FormRowProps {
  readonly form: EditForm;
  readonly setForm: React.Dispatch<React.SetStateAction<EditForm>>;
  readonly saving: boolean;
  readonly onSubmit: () => void;
  readonly onCancel: () => void;
  readonly token: string | null;
}

function FormRow({ form, setForm, saving, onSubmit, onCancel, token }: FormRowProps) {
  return (
    <div className="bg-pe-cream/50 border border-pe-black/8 p-3 mt-2 flex flex-col gap-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Slug *</label>
          <input className={INPUT_CLASS} value={form.slug}
            onChange={e => setForm(f => ({ ...f, slug: slugify(e.target.value) }))} placeholder="ej: zapatos" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Nombre ES *</label>
          <input className={INPUT_CLASS} value={form.nameEs}
            onChange={e => setForm(f => ({ ...f, nameEs: e.target.value }))} placeholder="Zapatos" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Nombre EN</label>
          <input className={INPUT_CLASS} value={form.nameEn}
            onChange={e => setForm(f => ({ ...f, nameEn: e.target.value }))} placeholder="Shoes" />
        </div>
        <div className="flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Orden</label>
          <input type="number" min="0" className={INPUT_CLASS} value={form.sortOrder}
            onChange={e => setForm(f => ({ ...f, sortOrder: e.target.value }))} />
        </div>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
        <div className="sm:col-span-3 flex flex-col gap-0.5">
          <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Hero imagen</label>
          <input
            className={INPUT_CLASS}
            value={form.heroImageUrl}
            onChange={e => setForm(f => ({ ...f, heroImageUrl: e.target.value }))}
            placeholder="https://..."
          />
        </div>
      </div>
      <ImageDropzone
        label="Imagen"
        folder="categories"
        value={form.imageUrl || undefined}
        onUpload={url => setForm(f => ({ ...f, imageUrl: url }))}
        token={token ?? ''}
      />
      <div className="flex flex-wrap items-center gap-2">
        <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-muted cursor-pointer">
          <input type="checkbox" checked={form.active} onChange={e => setForm(f => ({ ...f, active: e.target.checked }))} className="accent-pe-rose" />
          Activa
        </label>
        <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-muted cursor-pointer">
          <input type="checkbox" checked={form.featured} onChange={e => setForm(f => ({ ...f, featured: e.target.checked }))} className="accent-pe-rose" />
          <Star size={11} className="text-amber-500" /> Destacada en inicio
        </label>
        <label className="flex items-center gap-1.5 font-sans text-[0.78rem] text-pe-muted cursor-pointer">
          <input type="checkbox" checked={form.menuVisible} onChange={e => setForm(f => ({ ...f, menuVisible: e.target.checked }))} className="accent-pe-rose" />
          Visible en menu
        </label>
        <button type="button" onClick={onSubmit} disabled={saving}
          className="flex items-center gap-1 bg-pe-rose-action text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:bg-pe-rose-action-action-deep transition-colors disabled:opacity-50">
          {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          Guardar
        </button>
        <button type="button" onClick={onCancel}
          className="flex items-center gap-1 border border-pe-black/12 font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:border-pe-charcoal transition-colors text-pe-muted">
          <X size={12} /> Cancelar
        </button>
      </div>
    </div>
  );
}

// ─── CategoryRow ──────────────────────────────────────────────────────────────

interface DragHandleProps {
  listeners?: Record<string, any>;
  attributes?: Record<string, any>;
}

interface CategoryRowProps {
  readonly node: CategoryTreeNode;
  readonly depth: number;
  readonly editing: string | null;
  readonly creating: string | null;
  readonly expanded: Set<string>;
  readonly form: EditForm;
  readonly setForm: React.Dispatch<React.SetStateAction<EditForm>>;
  readonly saving: boolean;
  readonly token: string | null;
  readonly dragHandle?: DragHandleProps;
  readonly setExpanded: React.Dispatch<React.SetStateAction<Set<string>>>;
  readonly setEditing: (id: string | null) => void;
  readonly setCreating: (id: string | null) => void;
  readonly onSaveEdit: (id: string) => void;
  readonly onDelete: (id: string, name: string) => void;
  readonly onCreate: (parentId: string | null) => void;
  readonly onReorder: (items: { id: string; sortOrder: number }[]) => void;
}

function CategoryRow({
  node, depth, editing, creating, expanded, form, setForm, saving, token, dragHandle,
  setExpanded, setEditing, setCreating, onSaveEdit, onDelete, onCreate, onReorder,
}: CategoryRowProps) {
  const isExpanded = expanded.has(node.id);
  const hasChildren = node.children.length > 0;
  const isEditing = editing === node.id;
  const isCreatingChild = creating === node.id;

  const handleCancel = () => { setEditing(null); setCreating(null); };

  const childProps = {
    editing, creating, expanded, form, setForm, saving, token,
    setExpanded, setEditing, setCreating,
    onSaveEdit, onDelete, onCreate, onReorder,
  };

  return (
    <div>
      <div
        className="group flex items-center gap-2 rounded-sm px-2 py-2 hover:bg-pe-cream/40 transition-colors"
        style={{ paddingLeft: `${(depth + 1) * 16}px` }}
      >
        {/* Drag handle */}
        <button
          type="button"
          {...dragHandle?.listeners}
          {...dragHandle?.attributes}
          className="p-0.5 text-pe-muted hover:text-pe-muted transition-colors cursor-grab active:cursor-grabbing touch-none shrink-0"
          title="Arrastrar para reordenar"
          tabIndex={-1}
        >
          <GripVertical size={13} />
        </button>

        <button
          type="button"
          onClick={() => setExpanded(prev => {
            const next = new Set(prev);
            if (next.has(node.id)) {
              next.delete(node.id);
            } else {
              next.add(node.id);
            }
            return next;
          })}
          className={['p-0.5 text-pe-muted hover:text-pe-charcoal transition-colors', hasChildren ? '' : 'invisible'].join(' ')}
        >
          {isExpanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </button>

        {node.imageUrl ? (
          <img src={node.imageUrl} alt="" className="w-6 h-6 object-cover shrink-0 rounded-xs opacity-80" />
        ) : (
          <span className="w-6 h-6 shrink-0" />
        )}
        <span className={['min-w-0 truncate font-sans text-[0.82rem]', node.active ? 'text-pe-charcoal' : 'text-pe-muted line-through'].join(' ')}>
          {node.nameEs}
        </span>
        <span className="min-w-0 truncate font-sans text-[0.65rem] text-pe-muted ml-1">/{node.slug}</span>
        {!node.active && (
          <span className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-muted bg-pe-cream px-1.5 py-0.5">
            Inactiva
          </span>
        )}
        {!node.menuVisible && (
          <span className="font-sans text-[0.6rem] uppercase tracking-wider text-pe-muted bg-pe-cream px-1.5 py-0.5">
            Oculta menu
          </span>
        )}
        {node.featured && (
          <span title="Destacada en inicio">
            <Star size={11} className="shrink-0 text-amber-400 fill-amber-400" />
          </span>
        )}

        <div className="ml-auto flex items-center gap-1 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
          {depth < 3 && (
            <button
              type="button"
              onClick={() => { setCreating(node.id); setForm({ ...EMPTY_FORM }); setEditing(null); }}
              className="p-1 text-pe-muted hover:text-pe-rose-ink transition-colors"
              title="Agregar subcategoría"
            >
              <Plus size={13} />
            </button>
          )}
          <button
            type="button"
            onClick={() => { setEditing(node.id); setForm(fromDto(node)); setCreating(null); }}
            className="p-1 text-pe-muted hover:text-pe-rose-ink transition-colors"
            title="Editar"
          >
            <Edit3 size={13} />
          </button>
          <button
            type="button"
            onClick={() => onDelete(node.id, node.nameEs)}
            className="p-1 text-pe-muted hover:text-red-500 transition-colors"
            title="Eliminar"
          >
            <Trash2 size={13} />
          </button>
        </div>
      </div>

      {isEditing && (
        <div style={{ paddingLeft: `${(depth + 1) * 16}px` }}>
          <FormRow form={form} setForm={setForm} saving={saving}
            onSubmit={() => onSaveEdit(node.id)} onCancel={handleCancel} token={token} />
        </div>
      )}

      {isExpanded && hasChildren && (
        <SortableContext items={node.children.map(c => c.id)} strategy={verticalListSortingStrategy}>
          {node.children.map(child => (
            <SortableCategoryRow key={child.id} node={child} depth={depth + 1} {...childProps} />
          ))}
        </SortableContext>
      )}

      {isCreatingChild && (
        <div style={{ paddingLeft: `${(depth + 2) * 16}px` }}>
          <p className="font-sans text-[0.65rem] uppercase tracking-wider text-pe-muted mb-1 mt-2 px-2">
            Nueva subcategoría en {node.nameEs}
          </p>
          <FormRow form={form} setForm={setForm} saving={saving}
            onSubmit={() => onCreate(node.id)} onCancel={handleCancel} token={token} />
        </div>
      )}
    </div>
  );
}

// ─── SortableCategoryRow ──────────────────────────────────────────────────────

function SortableCategoryRow(props: Omit<CategoryRowProps, 'dragHandle'>) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: props.node.id });

  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : undefined,
        position: 'relative',
        zIndex: isDragging ? 1 : undefined,
      }}
    >
      <CategoryRow {...props} dragHandle={{ listeners, attributes }} />
    </div>
  );
}

// ─── CategoryTree ─────────────────────────────────────────────────────────────

export default function CategoryTree() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [tree, setTree]       = useState<CategoryTreeNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [editing, setEditing]   = useState<string | null>(null);
  const [creating, setCreating] = useState<string | null>(null);
  const [form, setForm]         = useState<EditForm>({ ...EMPTY_FORM });
  const [saving, setSaving]     = useState(false);
  const { toasts, show, dismiss } = useToast();

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
  );

  async function loadTree() {
    setLoading(true);
    const data = await getCategoryTree();
    setTree(data);
    setLoading(false);
    const ids = new Set<string>();
    function collect(nodes: CategoryTreeNode[]) { nodes.forEach(n => { ids.add(n.id); collect(n.children); }); }
    collect(data);
    setExpanded(ids);
  }

  useEffect(() => { loadTree(); }, []);

  async function persistReorder(items: { id: string; sortOrder: number }[]) {
    if (!effectiveToken) return;
    try {
      await reorderCategories(items, effectiveToken);
    } catch {
      show('error', 'Error al guardar el nuevo orden.');
      await loadTree();
    }
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const activeId = String(active.id);
    const overId = String(over.id);

    // Root level reorder
    const rootIds = tree.map(n => n.id);
    if (rootIds.includes(activeId) && rootIds.includes(overId)) {
      const oldIdx = tree.findIndex(n => n.id === activeId);
      const newIdx = tree.findIndex(n => n.id === overId);
      const reordered = arrayMove(tree, oldIdx, newIdx);
      setTree(reordered);
      void persistReorder(reordered.map((n, i) => ({ id: n.id, sortOrder: i })));
      return;
    }

    // Recursive search: find the parent whose direct children include both ids
    function reorderInTree(nodes: CategoryTreeNode[]): CategoryTreeNode[] | null {
      for (const node of nodes) {
        const childIds = node.children.map(c => c.id);
        if (childIds.includes(activeId) && childIds.includes(overId)) {
          const oldIdx = node.children.findIndex(c => c.id === activeId);
          const newIdx = node.children.findIndex(c => c.id === overId);
          const reorderedChildren = arrayMove(node.children, oldIdx, newIdx);
          void persistReorder(reorderedChildren.map((c, i) => ({ id: c.id, sortOrder: i })));
          return nodes.map(n => n.id === node.id ? { ...n, children: reorderedChildren } : n);
        }
        const updated = reorderInTree(node.children);
        if (updated) return nodes.map(n => n.id === node.id ? { ...n, children: updated } : n);
      }
      return null;
    }

    const updated = reorderInTree(tree);
    if (updated) setTree(updated);
  }

  async function handleSaveEdit(id: string) {
    if (!effectiveToken || !form.slug || !form.nameEs) {
      show('error', 'Slug y Nombre ES son requeridos.'); return;
    }
    setSaving(true);
    try {
      await updateCategory(id, {
        slug: form.slug, nameEs: form.nameEs, nameEn: form.nameEn,
        parentId: form.parentId || undefined, sortOrder: Number(form.sortOrder),
        imageUrl: form.imageUrl || undefined, active: form.active, featured: form.featured,
        menuVisible: form.menuVisible, categoryType: form.categoryType, heroImageUrl: form.heroImageUrl || undefined,
      }, effectiveToken);
      setEditing(null);
      show('success', 'Categoría actualizada.');
      await loadTree();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al guardar.');
    } finally { setSaving(false); }
  }

  async function handleCreate(parentId: string | null) {
    if (!effectiveToken || !form.slug || !form.nameEs) {
      show('error', 'Slug y Nombre ES son requeridos.'); return;
    }
    setSaving(true);
    try {
      await createCategory({
        slug: form.slug, nameEs: form.nameEs, nameEn: form.nameEn,
        parentId: parentId ?? undefined, sortOrder: Number(form.sortOrder),
        imageUrl: form.imageUrl || undefined,
        active: form.active,
        featured: form.featured,
        menuVisible: form.menuVisible,
        categoryType: form.categoryType,
        heroImageUrl: form.heroImageUrl || undefined,
      }, effectiveToken);
      setCreating(null);
      setForm({ ...EMPTY_FORM });
      show('success', 'Categoría creada.');
      await loadTree();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al crear categoría.');
    } finally { setSaving(false); }
  }

  async function handleDelete(id: string, name: string) {
    if (!effectiveToken || !confirm(`¿Eliminar categoría "${name}"?\n\nEsta acción no se puede deshacer.`)) return;
    try {
      await deleteCategory(id, effectiveToken);
      show('success', `Categoría "${name}" eliminada.`);
      await loadTree();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al eliminar la categoría.');
    }
  }

  const handleCancel = () => { setEditing(null); setCreating(null); };

  const rowProps = {
    editing, creating, expanded, form, setForm, saving, token: effectiveToken,
    setExpanded, setEditing, setCreating,
    onSaveEdit: handleSaveEdit, onDelete: handleDelete, onCreate: handleCreate,
    onReorder: persistReorder,
  };

  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 size={24} className="animate-spin text-pe-rose-ink" /></div>;
  }

  return (
    <div>
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="font-sans text-[0.72rem] text-pe-muted">{tree.length} categorías raíz</p>
        <button
          type="button"
          onClick={() => { setCreating('__root__'); setForm({ ...EMPTY_FORM }); setEditing(null); }}
          className="inline-flex w-full sm:w-auto items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-action-action-deep transition-colors duration-200"
        >
          <Plus size={13} />
          Nueva categoría raíz
        </button>
      </div>

      {creating === '__root__' && (
        <div className="mb-4">
          <FormRow form={form} setForm={setForm} saving={saving}
            onSubmit={() => handleCreate(null)} onCancel={handleCancel} token={effectiveToken} />
        </div>
      )}

      <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
        <div className="bg-pe-white border border-pe-black/6 shadow-xs py-1">
          {tree.length === 0 ? (
            <p className="font-sans text-[0.82rem] text-pe-muted text-center py-12">
              No hay categorías. Crea la primera.
            </p>
          ) : (
            <SortableContext items={tree.map(n => n.id)} strategy={verticalListSortingStrategy}>
              {tree.map(node => <SortableCategoryRow key={node.id} node={node} depth={0} {...rowProps} />)}
            </SortableContext>
          )}
        </div>
      </DndContext>

      <Toaster toasts={toasts} dismiss={dismiss} />
    </div>
  );
}
```

- [ ] **Step 4: Compile and run remaining islands tests**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/`
Expected: `CategoryTree.test.tsx` is gone (no longer listed); `ProductForm.test.tsx` and
`ProductFormValidation.test.ts` still fail here — Task 15 fixes them. Confirm no other admin test
regressed.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/islands/admin/VariantFieldEditor.tsx frontend/src/islands/admin/CategoryTree.tsx
git rm frontend/src/islands/admin/__tests__/CategoryTree.test.tsx
git commit -m "refactor: revert CategoryTree.tsx and extract VariantFieldEditor"
```

---

## Task 14: New CMS page for variant templates

**Files:**
- Create: `frontend/src/islands/admin/VariantTemplateTable.tsx`
- Create: `frontend/src/pages/admin/tipos-variante.astro`
- Modify: `frontend/src/islands/admin/AdminSidebar.tsx`
- Test: `frontend/src/islands/admin/__tests__/VariantTemplateTable.test.tsx`

**Interfaces:**
- Consumes: `VariantFieldEditor` (Task 13), `getVariantTemplates`/`createVariantTemplate`/
  `updateVariantTemplate`/`deleteVariantTemplate`/`VariantTemplateDto` (Task 11).
- Produces: page `/admin/tipos-variante` with island `VariantTemplateTable`, reachable from a new
  ADMIN-only sidebar entry "Tipos de Variante". Task 15's `ProductForm.tsx` reads from the same
  `getVariantTemplates` this page's table also uses — no shared component, just the shared API
  function, matching how `CategoryTree.tsx` and `ProductForm.tsx` both independently call
  `getCategories()` today.

The nav item is gated ADMIN-only (a special case in `AdminSidebar`'s filter, not the generic
`viewKey` permission check) because `VariantTemplateController` requires `hasRole('ADMIN')` on
every method — a non-ADMIN user with the legacy `productos` permission would otherwise see a link
that always 403s.

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import VariantTemplateTable from '../VariantTemplateTable';
import type { VariantTemplateDto } from '../../../lib/api';

const getVariantTemplates = vi.fn();
const createVariantTemplate = vi.fn();
const updateVariantTemplate = vi.fn();
const deleteVariantTemplate = vi.fn();

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getVariantTemplates: (...args: unknown[]) => getVariantTemplates(...args),
    createVariantTemplate: (...args: unknown[]) => createVariantTemplate(...args),
    updateVariantTemplate: (...args: unknown[]) => updateVariantTemplate(...args),
    deleteVariantTemplate: (...args: unknown[]) => deleteVariantTemplate(...args),
  };
});

function template(overrides: Partial<VariantTemplateDto> = {}): VariantTemplateDto {
  return {
    id: 'tpl-1', name: 'Zapatos',
    config: {
      primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
      secondary: { label: 'Numero', inputType: 'RANGE', options: [], min: 34, max: 43, allowMultiple: true, allowCustom: true },
    },
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  document.cookie = 'pe_token=test-token';
  getVariantTemplates.mockResolvedValue([template()]);
});

describe('VariantTemplateTable', () => {
  it('shows the resolved field labels for a template', async () => {
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');
    expect(screen.getByTitle('Color / Numero')).toBeInTheDocument();
  });

  it('opens the edit form with the template values', async () => {
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.getByDisplayValue('Zapatos')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Color')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Numero')).toBeInTheDocument();
  });

  it('shows a min/max range editor when the secondary field is RANGE', async () => {
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    expect(screen.getByDisplayValue('34')).toBeInTheDocument();
    expect(screen.getByDisplayValue('43')).toBeInTheDocument();
  });

  it('submits the edited config on save', async () => {
    updateVariantTemplate.mockResolvedValue(template());
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');
    await userEvent.click(screen.getByRole('button', { name: /editar/i }));

    const labelInputs = screen.getAllByDisplayValue('Color');
    await userEvent.clear(labelInputs[0]);
    await userEvent.type(labelInputs[0], 'Tono');
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(updateVariantTemplate).toHaveBeenCalledWith('tpl-1', expect.objectContaining({
      name: 'Zapatos',
      primary: expect.objectContaining({ label: 'Tono' }),
    }), expect.any(String));
  });

  it('creates a new template', async () => {
    createVariantTemplate.mockResolvedValue(template({ id: 'tpl-2', name: 'Carteras' }));
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /nuevo tipo de variante/i }));
    await userEvent.type(screen.getByPlaceholderText('ej: Zapatos'), 'Carteras');
    await userEvent.click(screen.getByRole('button', { name: /guardar/i }));

    expect(createVariantTemplate).toHaveBeenCalledWith(expect.objectContaining({ name: 'Carteras' }), expect.any(String));
  });

  it('deletes a template after confirmation', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<VariantTemplateTable />);
    await screen.findByText('Zapatos');

    await userEvent.click(screen.getByRole('button', { name: /eliminar/i }));

    expect(confirmSpy).toHaveBeenCalled();
    expect(deleteVariantTemplate).toHaveBeenCalledWith('tpl-1', expect.any(String));
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/VariantTemplateTable.test.tsx`
Expected: FAIL — `VariantTemplateTable` does not exist yet.

- [ ] **Step 3: Write VariantTemplateTable.tsx**

```typescript
import { useEffect, useState } from 'react';
import { Plus, Edit3, Trash2, Loader2, Check, X } from 'lucide-react';
import {
  getVariantTemplates, createVariantTemplate, updateVariantTemplate, deleteVariantTemplate,
  type VariantTemplateDto, type VariantFieldDto,
} from '../../lib/api';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import { useToast, Toaster } from './Toast';
import VariantFieldEditor from './VariantFieldEditor';

type EditForm = {
  name: string;
  primary: VariantFieldDto;
  secondary: VariantFieldDto;
};

const EMPTY_FIELD: VariantFieldDto = {
  label: '', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: true, allowCustom: true,
};

const EMPTY_FORM: EditForm = { name: '', primary: EMPTY_FIELD, secondary: EMPTY_FIELD };

function fromDto(dto: VariantTemplateDto): EditForm {
  return { name: dto.name, primary: dto.config.primary, secondary: dto.config.secondary };
}

const INPUT_CLASS = 'font-sans text-[0.78rem] border border-pe-black/12 bg-pe-white px-2 py-1.5 text-pe-charcoal focus:outline-hidden focus:border-pe-rose/50 transition-colors';

interface FormRowProps {
  readonly form: EditForm;
  readonly setForm: React.Dispatch<React.SetStateAction<EditForm>>;
  readonly saving: boolean;
  readonly onSubmit: () => void;
  readonly onCancel: () => void;
}

function FormRow({ form, setForm, saving, onSubmit, onCancel }: FormRowProps) {
  return (
    <div className="bg-pe-cream/50 border border-pe-black/8 p-3 mt-2 flex flex-col gap-3">
      <div className="flex flex-col gap-0.5">
        <label className="font-sans text-[0.62rem] uppercase tracking-wider text-pe-muted">Nombre *</label>
        <input className={INPUT_CLASS} value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} placeholder="ej: Zapatos" />
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <VariantFieldEditor fieldNumber={1} field={form.primary} onChange={(next) => setForm((f) => ({ ...f, primary: next }))} />
        <VariantFieldEditor fieldNumber={2} field={form.secondary} onChange={(next) => setForm((f) => ({ ...f, secondary: next }))} />
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <button type="button" onClick={onSubmit} disabled={saving}
          className="flex items-center gap-1 bg-pe-rose-action text-pe-offwhite font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:bg-pe-rose-action-action-deep transition-colors disabled:opacity-50">
          {saving ? <Loader2 size={12} className="animate-spin" /> : <Check size={12} />}
          Guardar
        </button>
        <button type="button" onClick={onCancel}
          className="flex items-center gap-1 border border-pe-black/12 font-sans text-[0.68rem] uppercase tracking-wider px-3 py-1.5 hover:border-pe-charcoal transition-colors text-pe-muted">
          <X size={12} /> Cancelar
        </button>
      </div>
    </div>
  );
}

export default function VariantTemplateTable() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [templates, setTemplates] = useState<VariantTemplateDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState<EditForm>({ ...EMPTY_FORM });
  const [saving, setSaving] = useState(false);
  const { toasts, show, dismiss } = useToast();

  async function loadTemplates() {
    if (!effectiveToken) {
      setLoading(false);
      return;
    }
    setLoading(true);
    const data = await getVariantTemplates(effectiveToken);
    setTemplates(data);
    setLoading(false);
  }

  useEffect(() => { loadTemplates(); }, []);

  async function handleSaveEdit(id: string) {
    if (!effectiveToken || !form.name.trim()) {
      show('error', 'Nombre es requerido.'); return;
    }
    setSaving(true);
    try {
      await updateVariantTemplate(id, { name: form.name, primary: form.primary, secondary: form.secondary }, effectiveToken);
      setEditing(null);
      show('success', 'Tipo de variante actualizado.');
      await loadTemplates();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al guardar.');
    } finally { setSaving(false); }
  }

  async function handleCreate() {
    if (!effectiveToken || !form.name.trim()) {
      show('error', 'Nombre es requerido.'); return;
    }
    setSaving(true);
    try {
      await createVariantTemplate({ name: form.name, primary: form.primary, secondary: form.secondary }, effectiveToken);
      setCreating(false);
      setForm({ ...EMPTY_FORM });
      show('success', 'Tipo de variante creado.');
      await loadTemplates();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al crear tipo de variante.');
    } finally { setSaving(false); }
  }

  async function handleDelete(id: string, name: string) {
    if (!effectiveToken || !confirm(`¿Eliminar tipo de variante "${name}"?\n\nEsta acción no se puede deshacer.`)) return;
    try {
      await deleteVariantTemplate(id, effectiveToken);
      show('success', `Tipo de variante "${name}" eliminado.`);
      await loadTemplates();
    } catch (err) {
      show('error', err instanceof Error ? err.message : 'Error al eliminar el tipo de variante.');
    }
  }

  const handleCancel = () => { setEditing(null); setCreating(false); };

  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 size={24} className="animate-spin text-pe-rose-ink" /></div>;
  }

  return (
    <div>
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="font-sans text-[0.72rem] text-pe-muted">{templates.length} tipos de variante</p>
        <button
          type="button"
          onClick={() => { setCreating(true); setForm({ ...EMPTY_FORM }); setEditing(null); }}
          className="inline-flex w-full sm:w-auto items-center justify-center gap-2 bg-pe-rose-action text-pe-offwhite font-sans text-[0.72rem] tracking-[0.14em] uppercase px-4 py-2 hover:bg-pe-rose-action-action-deep transition-colors duration-200"
        >
          <Plus size={13} />
          Nuevo tipo de variante
        </button>
      </div>

      {creating && (
        <div className="mb-4">
          <FormRow form={form} setForm={setForm} saving={saving} onSubmit={handleCreate} onCancel={handleCancel} />
        </div>
      )}

      <div className="bg-pe-white border border-pe-black/6 shadow-xs py-1">
        {templates.length === 0 ? (
          <p className="font-sans text-[0.82rem] text-pe-muted text-center py-12">
            No hay tipos de variante. Crea el primero.
          </p>
        ) : (
          templates.map((t) => (
            <div key={t.id}>
              <div className="group flex items-center gap-2 rounded-sm px-2 py-2 hover:bg-pe-cream/40 transition-colors">
                <span className="min-w-0 truncate font-sans text-[0.82rem] text-pe-charcoal">{t.name}</span>
                <span
                  className="font-sans text-[0.58rem] uppercase tracking-[0.12em] text-pe-muted bg-pe-cream px-1.5 py-0.5 ml-1"
                  title={`${t.config.primary.label} / ${t.config.secondary.label}`}
                >
                  {t.config.primary.label} / {t.config.secondary.label}
                </span>
                <div className="ml-auto flex items-center gap-1 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                  <button
                    type="button"
                    onClick={() => { setEditing(t.id); setForm(fromDto(t)); setCreating(false); }}
                    className="p-1 text-pe-muted hover:text-pe-rose-ink transition-colors"
                    title="Editar"
                  >
                    <Edit3 size={13} />
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(t.id, t.name)}
                    className="p-1 text-pe-muted hover:text-red-500 transition-colors"
                    title="Eliminar"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
              {editing === t.id && (
                <div className="px-2">
                  <FormRow form={form} setForm={setForm} saving={saving}
                    onSubmit={() => handleSaveEdit(t.id)} onCancel={handleCancel} />
                </div>
              )}
            </div>
          ))
        )}
      </div>

      <Toaster toasts={toasts} dismiss={dismiss} />
    </div>
  );
}
```

- [ ] **Step 4: Write the Astro page**

```astro
---
import AdminLayout from '../../layouts/AdminLayout.astro';
import VariantTemplateTable from '../../islands/admin/VariantTemplateTable';
---

<AdminLayout
  title="Tipos de Variante"
  breadcrumbs={[{ label: 'Tipos de Variante' }]}
>
  <div class="mb-5 sm:mb-6">
    <h1 class="font-display text-pe-black text-2xl sm:text-3xl font-light">Tipos de Variante</h1>
    <p class="font-sans text-[0.78rem] sm:text-sm text-pe-muted mt-1">
      Gestiona los tipos de variante que los productos pueden usar, independientes de la categoria.
    </p>
  </div>

  <VariantTemplateTable client:load />
</AdminLayout>
```

- [ ] **Step 5: Add the sidebar nav item, gated ADMIN-only**

In `frontend/src/islands/admin/AdminSidebar.tsx`, add `SlidersHorizontal` to the `lucide-react`
import list, add the nav entry, and add the ADMIN-only guard to the filter:

```typescript
import {
  LayoutDashboard,
  Package,
  Tag,
  Star,
  CreditCard,
  Users,
  Settings,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Store,
  Wallet,
  Image,
  Bell,
  Ticket,
  ShieldCheck,
  ShieldOff,
  DollarSign,
  Truck,
  Megaphone,
  Navigation,
  Receipt,
  Undo2,
  SlidersHorizontal,
} from 'lucide-react';
```

```typescript
const navItems: Array<{ href: string; icon: typeof LayoutDashboard; label: string; viewKey: string }> = [
  { href: '/admin/', icon: LayoutDashboard, label: 'Dashboard', viewKey: 'dashboard' },
  { href: '/admin/products', icon: Package, label: 'Productos', viewKey: 'productos' },
  { href: '/admin/categories', icon: Tag, label: 'Categorias', viewKey: 'productos' },
  { href: '/admin/tipos-variante', icon: SlidersHorizontal, label: 'Tipos de Variante', viewKey: 'productos' },
  { href: '/admin/navegacion', icon: Navigation, label: 'Navegación', viewKey: 'productos' },
  { href: '/admin/reviews', icon: Star, label: 'Resenas', viewKey: 'productos' },
  { href: '/admin/ventas', icon: Receipt, label: 'Ventas', viewKey: 'caja' },
  { href: '/admin/payments', icon: CreditCard, label: 'Pagos', viewKey: 'caja' },
  { href: '/admin/caja', icon: DollarSign, label: 'Caja', viewKey: 'caja' },
  { href: '/admin/despachos', icon: Truck, label: 'Despachos', viewKey: 'despachos' },
  { href: '/admin/devoluciones', icon: Undo2, label: 'Devoluciones', viewKey: 'caja' },
  { href: '/admin/publicaciones', icon: Megaphone, label: 'Publicaciones', viewKey: 'productos' },
  { href: '/admin/discounts', icon: Ticket, label: 'Descuentos', viewKey: 'productos' },
  { href: '/admin/users', icon: Users, label: 'Usuarios', viewKey: 'usuarios' },
  { href: '/admin/privacidad', icon: ShieldOff, label: 'Privacidad', viewKey: 'privacy.read' },
  { href: '/admin/roles-permisos', icon: ShieldCheck, label: 'Roles/Permisos', viewKey: 'roles_permisos' },
];
```

Find the `visibleNavItems` filter:

```typescript
  const visibleNavItems = user?.role === 'ADMIN'
    ? navItems
    : navItems.filter((item) => {
      if (item.href === '/admin/users') return canSeeUsers;
      if (item.href === '/admin/roles-permisos') return canSeeRoles;
      if (item.href === '/admin/ventas') return canSeeSales || permissions.includes('caja');
      if (item.href === '/admin/devoluciones') return canSeeReturns || permissions.includes('caja');
      if (item.href === '/admin/privacidad') return canSeePrivacy;
      return permissions.includes(item.viewKey);
    });
```

Replace with:

```typescript
  const visibleNavItems = user?.role === 'ADMIN'
    ? navItems
    : navItems.filter((item) => {
      if (item.href === '/admin/users') return canSeeUsers;
      if (item.href === '/admin/roles-permisos') return canSeeRoles;
      if (item.href === '/admin/ventas') return canSeeSales || permissions.includes('caja');
      if (item.href === '/admin/devoluciones') return canSeeReturns || permissions.includes('caja');
      if (item.href === '/admin/privacidad') return canSeePrivacy;
      // VariantTemplateController requires ADMIN on every method (unlike categories), so a
      // non-ADMIN user must never see a link that always 403s.
      if (item.href === '/admin/tipos-variante') return false;
      return permissions.includes(item.viewKey);
    });
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/VariantTemplateTable.test.tsx`
Expected: PASS, 6/6.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/islands/admin/VariantTemplateTable.tsx frontend/src/pages/admin/tipos-variante.astro frontend/src/islands/admin/AdminSidebar.tsx frontend/src/islands/admin/__tests__/VariantTemplateTable.test.tsx
git commit -m "feat: add variant template CMS page and admin nav entry"
```

---

## Task 15: Rewire ProductForm.tsx to the template dropdown

**Files:**
- Modify: `frontend/src/islands/admin/ProductForm.tsx`
- Modify: `frontend/src/islands/admin/__tests__/ProductForm.test.tsx`
- Modify: `frontend/src/islands/admin/__tests__/ProductFormValidation.test.ts`

**Interfaces:**
- Consumes: `getVariantTemplates`/`VariantTemplateDto` (Task 11), `buildVariantSchema` (Task 12,
  retyped).
- Produces: a "Tipo de Variante" `<select id="pf-variant-template">` populated from
  `GET /api/variant-templates`; the category tree reverts to a plain, unrestricted multi-select
  (`CategoryTreeItem` loses `allowedIds`/`lockedHint`); `validateProductForm`'s
  `ValidateProductFormArgs` loses `categories`/`selectedCatIds`/`allowedCatIds`; the submit payload
  gains `variantTemplateId`.

This task makes 12 targeted edits to one file, listed as find/replace pairs against the file's
current (post-category-derived-feature) content — not a full-file rewrite, since the file is 1471
lines and most of it (AI tools, price fields, image upload, unsaved-changes dialog) is untouched.

- [ ] **Step 1: Write the failing tests first (rewritten ProductForm.test.tsx)**

Replace `frontend/src/islands/admin/__tests__/ProductForm.test.tsx` in full:

```typescript
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ProductForm from '../ProductForm';
import type { VariantTemplateDto } from '../../../lib/api';

/**
 * Characterization suite for the template-driven rewrite (variant fields no longer derive from
 * categories -- a product picks a variant template directly from its own dropdown). Also covers
 * the regression the deleted variant-type-picker test guarded: changing the resolved schema (now
 * via the template dropdown, not category selection) must not wipe already-typed fields, since the
 * effect that seeds the form from `product` used to list the schema among its dependencies.
 */

const TEMPLATES: VariantTemplateDto[] = [
  {
    id: 'tpl-zapatos', name: 'Zapatos',
    config: {
      primary: { label: 'Color', inputType: 'FREE_TEXT', options: [], min: null, max: null, allowMultiple: false, allowCustom: true },
      secondary: { label: 'Numero', inputType: 'RANGE', options: [], min: 34, max: 43, allowMultiple: true, allowCustom: true },
    },
  },
];

vi.mock('../../../lib/api', async () => {
  const actual = await vi.importActual<typeof import('../../../lib/api')>('../../../lib/api');
  return {
    ...actual,
    getCategories: vi.fn(async () => []),
    getVariantTemplates: vi.fn(async () => TEMPLATES),
    getSystemSettings: vi.fn(async () => ({})),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    inferSingleProductAi: vi.fn(),
    transformSingleProductAiImage: vi.fn(),
    assignHeroModelFromProduct: vi.fn(),
  };
});

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ProductForm: template-driven variant schema', () => {
  it('renders the generic Variante/Detalle fallback when no template is selected', async () => {
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    await screen.findByText('Variante');
    expect(screen.getByText('Detalle(s)')).toBeInTheDocument();
  });

  it('renders the selected template field labels and range options', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    const select = await screen.findByLabelText(/tipo de variante/i);
    await user.selectOptions(select, 'tpl-zapatos');

    await screen.findByText('Numero(s)');
    expect(screen.getByRole('button', { name: '34' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '43' })).toBeInTheDocument();
  });

  it('keeps already-typed fields when selecting a template changes the schema', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    const name = await screen.findByLabelText(/nombre/i);
    await user.type(name, 'Zapato Elegance');
    const select = await screen.findByLabelText(/tipo de variante/i);

    await user.selectOptions(select, 'tpl-zapatos');

    await screen.findByText('Numero(s)');
    expect((name as HTMLInputElement).value).toBe('Zapato Elegance');
  });

  it('adds and removes a variant row', async () => {
    const user = userEvent.setup();
    render(<ProductForm product={null} token="t" onSave={() => {}} onCancel={() => {}} />);
    await screen.findByText('Variante');
    expect(screen.getAllByRole('button', { name: /quitar/i })).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: /agregar/i }));
    expect(screen.getAllByRole('button', { name: /quitar/i })).toHaveLength(2);

    await user.click(screen.getAllByRole('button', { name: /quitar/i })[0]);
    expect(screen.getAllByRole('button', { name: /quitar/i })).toHaveLength(1);
  });
});
```

Replace `frontend/src/islands/admin/__tests__/ProductFormValidation.test.ts` in full (drops the
`category()` helper and the two category-compatibility tests; `baseArgs()` drops
`categories`/`selectedCatIds`/`allowedCatIds`):

```typescript
import { describe, expect, it } from 'vitest';
import { validateProductForm, type ValidateProductFormArgs } from '../ProductForm';
import {
  buildVariantSchema,
  getPrimaryAttribute,
  getSecondaryAttribute,
  createEmptyVariantSelections,
} from '@/lib/variantSchema';

/**
 * Unit tests for validate()'s extracted pure form, written before splitting it into
 * validateBasicFields/validateVariantRows (S3776, complexity 38) -- it had none. A pure function
 * taking everything as arguments needs no component rendering, so this is a plain unit test rather
 * than a characterization test with mocks.
 */

const schema = buildVariantSchema(null);
const primaryAttribute = getPrimaryAttribute(schema);
const secondaryAttribute = getSecondaryAttribute(schema);

function baseForm() {
  return {
    name: 'Vestido',
    description: 'Un vestido bonito',
    amount: '20000',
    listAmount: '',
    currency: 'CLP',
    imageUrl: '',
    condition: 'NEW' as const,
    brand: 'PilarEstilo',
    stock: '1',
    active: true,
  };
}

function variantRow(primary = 'Negro', secondary = 'UNICO', stock = '5') {
  return {
    attributes: {
      ...createEmptyVariantSelections(schema),
      [primaryAttribute.code]: [primary],
      [secondaryAttribute.code]: [secondary],
    },
    stock,
  };
}

function baseArgs(overrides: Partial<ValidateProductFormArgs> = {}): ValidateProductFormArgs {
  return {
    form: baseForm(),
    variantRows: [variantRow()],
    variantSchema: schema,
    primaryAttribute,
    secondaryAttribute,
    ...overrides,
  };
}

describe('validateProductForm', () => {
  it('accepts a fully valid form', () => {
    expect(validateProductForm(baseArgs())).toEqual({});
  });

  it('flags missing name, brand and description', () => {
    const errors = validateProductForm(baseArgs({ form: { ...baseForm(), name: ' ', brand: '', description: '' } }));
    expect(errors.name).toBeDefined();
    expect(errors.brand).toBeDefined();
    expect(errors.description).toBeDefined();
  });

  it('flags a missing or non-positive price', () => {
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: '' } })).amount).toBeDefined();
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: '0' } })).amount).toBeDefined();
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: 'abc' } })).amount).toBeDefined();
  });

  it('flags an invalid list price, and one that does not beat the sale price', () => {
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), listAmount: 'abc' } })).listAmount).toBeDefined();
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: '20000', listAmount: '15000' } })).listAmount).toBeDefined();
    expect(validateProductForm(baseArgs({ form: { ...baseForm(), amount: '20000', listAmount: '25000' } })).listAmount).toBeUndefined();
  });

  it('requires at least one variant row', () => {
    expect(validateProductForm(baseArgs({ variantRows: [] })).combinations).toMatch(/al menos una combinacion/i);
  });

  it('flags a required attribute missing on a specific row', () => {
    const rowMissingPrimary = {
      attributes: { ...createEmptyVariantSelections(schema), [secondaryAttribute.code]: ['UNICO'] },
      stock: '5',
    };
    const errors = validateProductForm(baseArgs({ variantRows: [rowMissingPrimary] }));
    expect(errors.combinations).toMatch(new RegExp(`${primaryAttribute.label}.*fila 1`, 'i'));
  });

  it('flags an invalid stock value on a specific row', () => {
    const errors = validateProductForm(baseArgs({ variantRows: [variantRow('Negro', 'UNICO', 'abc')] }));
    expect(errors.combinations).toMatch(/stock valido requerido en fila 1/i);
  });

  it('flags duplicate color+detail combinations across rows', () => {
    const errors = validateProductForm(baseArgs({
      variantRows: [variantRow('Negro', 'UNICO'), variantRow('negro', 'UNICO')],
    }));
    expect(errors.combinations).toMatch(/no se permiten combinaciones duplicadas/i);
  });

  it('allows two rows that differ only by case-insensitive color are still the same key (case folded)', () => {
    const errors = validateProductForm(baseArgs({
      variantRows: [variantRow('Negro', 'UNICO'), variantRow('Rojo', 'UNICO')],
    }));
    expect(errors.combinations).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/ProductForm.test.tsx src/islands/admin/__tests__/ProductFormValidation.test.ts`
Expected: FAIL — `ProductForm.tsx` still imports the deleted `resolveVariantFieldConfig`/
`allowedCategoryIdsFor`, has no "Tipo de Variante" label, and `ValidateProductFormArgs` still
requires `categories`/`selectedCatIds`/`allowedCatIds`.

- [ ] **Step 3: Update the imports**

Find:

```typescript
import {
  assignHeroModelFromProduct,
  createProduct,
  updateProduct,
  getCategories,
  getSystemSettings,
  inferSingleProductAi,
  transformSingleProductAiImage,
  type ProductDto,
  type CreateProductRequest,
  type CategoryDto,
  type ProductVariantDto,
} from '../../lib/api';
import ImageDropzone from './ImageDropzone';
import {
  createEmptyVariantSelections,
  getAttributeValue,
  getAttributeValues,
  getPrimaryAttribute,
  getSecondaryAttribute,
  buildVariantSchema,
  resolveVariantFieldConfig,
  allowedCategoryIdsFor,
  legacyVariantToSelections,
  normalizeAttributeValues,
  selectionsToLegacyVariant,
  type CategoryAttributeDefinition,
  type VariantAttributeSelections,
  type VariantSchema,
} from '../../lib/variantSchema';
```

Replace with:

```typescript
import {
  assignHeroModelFromProduct,
  createProduct,
  updateProduct,
  getCategories,
  getVariantTemplates,
  getSystemSettings,
  inferSingleProductAi,
  transformSingleProductAiImage,
  type ProductDto,
  type CreateProductRequest,
  type CategoryDto,
  type ProductVariantDto,
  type VariantTemplateDto,
} from '../../lib/api';
import ImageDropzone from './ImageDropzone';
import {
  createEmptyVariantSelections,
  getAttributeValue,
  getAttributeValues,
  getPrimaryAttribute,
  getSecondaryAttribute,
  buildVariantSchema,
  legacyVariantToSelections,
  normalizeAttributeValues,
  selectionsToLegacyVariant,
  type CategoryAttributeDefinition,
  type VariantAttributeSelections,
  type VariantSchema,
} from '../../lib/variantSchema';
```

- [ ] **Step 4: Revert CategoryTreeItem to a plain checkbox tree**

Find:

```typescript
function CategoryTreeItem({
  node,
  depth,
  selected,
  onToggle,
  expanded,
  onToggleExpand,
  allowedIds,
  lockedHint,
}: {
  readonly node: CatNode;
  readonly depth: number;
  readonly selected: string[];
  readonly onToggle: (id: string) => void;
  readonly expanded: Set<string>;
  readonly onToggleExpand: (id: string) => void;
  /** Ids selectable for the current shape category; see allowedCategoryIdsFor. */
  readonly allowedIds: Set<string>;
  /** The resolved schema's title, used only for the locked-tooltip text. */
  readonly lockedHint: string;
}) {
  const hasChildren = node.children.length > 0;
  const isOpen = expanded.has(node.id);
  const isSelected = selected.includes(node.id);
  /*
   * Shown, not hidden. An admin who cannot find "Zapatos" assumes it is missing; one who sees it
   * greyed out learns the rule — a product is one shape, and its variants follow from it.
   * Already-selected categories stay operable so a product whose type is being changed can be
   * untangled rather than stranded.
   */
  const compatible = allowedIds.has(node.id);
  const locked = !compatible && !isSelected;
  const descendantsSelected = hasChildren ? collectSelectedDescendantCount(node, selected) : 0;

  // Visual hierarchy by depth — stronger contrast, clearer rhythm
  const ROW_CLASS_BY_DEPTH = [
    'text-[0.82rem] font-semibold text-[#1A1A1A] dark:text-[#E8DCC8] tracking-tight',
    'text-[0.78rem] font-medium text-[#2A2A2A] dark:text-[#D6C8B5]',
    'text-[0.74rem] text-[#4A4A4A] dark:text-[#C2B49E]',
  ];
  const rowClass = ROW_CLASS_BY_DEPTH[depth] ?? 'text-[0.7rem] text-[#6A6A6A] dark:text-[#A89C88]';

  const indent = depth * 16;

  return (
    <div>
      <div
        className={[
          'flex items-center gap-1.5 py-1 pr-2 group transition-colors rounded-xs',
          isSelected
            ? 'bg-[#B76E79]/8 dark:bg-[#E4B8BF]/12'
            : 'hover:bg-[#B76E79]/5 dark:hover:bg-[#E4B8BF]/8',
        ].join(' ')}
        style={{ paddingLeft: `${indent + 4}px` }}
      >
        {/* Chevron toggle for parents, spacer for leaves */}
        {hasChildren ? (
          <button
            type="button"
            onClick={() => onToggleExpand(node.id)}
            className="shrink-0 w-4 h-4 flex items-center justify-center text-pe-muted dark:text-[#D6C8B5]/55 hover:text-[#B76E79] dark:hover:text-[#E4B8BF] transition-colors"
            aria-label={isOpen ? 'Contraer' : 'Expandir'}
          >
            {isOpen ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
          </button>
        ) : (
          <span className="shrink-0 w-4 h-4 flex items-center justify-center text-pe-muted dark:text-[#D6C8B5]/30">
            <Tag size={9} />
          </span>
        )}

        <label
          className={`flex items-center gap-2 flex-1 min-w-0 ${
            locked ? 'cursor-not-allowed opacity-45' : 'cursor-pointer'
          }`}
          title={locked ? `No aplica a ${lockedHint}` : undefined}
        >
          <input
            type="checkbox"
            className="w-3.5 h-3.5 shrink-0 accent-[#B76E79] disabled:cursor-not-allowed"
            checked={isSelected}
            disabled={locked}
            onChange={() => onToggle(node.id)}
          />
          {hasChildren && (
            <span className="shrink-0 text-pe-muted dark:text-[#D6C8B5]/55 group-hover:text-[#B76E79] dark:group-hover:text-[#E4B8BF] transition-colors">
              {isOpen ? <FolderOpen size={12} /> : <Folder size={12} />}
            </span>
          )}
          <span className={`font-sans leading-snug truncate group-hover:text-[#B76E79] dark:group-hover:text-[#E4B8BF] transition-colors ${rowClass}`}>
            {node.nameEs}
          </span>
          {!compatible && isSelected && (
            <span className="shrink-0 font-sans text-[0.58rem] tracking-wider uppercase px-1 py-0.5 bg-[#8f2d3b]/10 text-[#8f2d3b]">
              No aplica
            </span>
          )}
          {hasChildren && (
            <span className="shrink-0 ml-auto font-sans text-[0.6rem] text-pe-muted dark:text-[#D6C8B5]/45">
              {descendantsSelected > 0
                ? `${descendantsSelected}/${node.children.length}`
                : node.children.length}
            </span>
          )}
        </label>
      </div>

      {hasChildren && isOpen && (
        <div>
          {node.children.map(child => (
            <CategoryTreeItem
              key={child.id}
              node={child}
              allowedIds={allowedIds}
              lockedHint={lockedHint}
              depth={depth + 1}
              selected={selected}
              onToggle={onToggle}
              expanded={expanded}
              onToggleExpand={onToggleExpand}
            />
          ))}
        </div>
      )}
    </div>
  );
}
```

Replace with:

```typescript
function CategoryTreeItem({
  node,
  depth,
  selected,
  onToggle,
  expanded,
  onToggleExpand,
}: {
  readonly node: CatNode;
  readonly depth: number;
  readonly selected: string[];
  readonly onToggle: (id: string) => void;
  readonly expanded: Set<string>;
  readonly onToggleExpand: (id: string) => void;
}) {
  const hasChildren = node.children.length > 0;
  const isOpen = expanded.has(node.id);
  const isSelected = selected.includes(node.id);
  const descendantsSelected = hasChildren ? collectSelectedDescendantCount(node, selected) : 0;

  // Visual hierarchy by depth — stronger contrast, clearer rhythm
  const ROW_CLASS_BY_DEPTH = [
    'text-[0.82rem] font-semibold text-[#1A1A1A] dark:text-[#E8DCC8] tracking-tight',
    'text-[0.78rem] font-medium text-[#2A2A2A] dark:text-[#D6C8B5]',
    'text-[0.74rem] text-[#4A4A4A] dark:text-[#C2B49E]',
  ];
  const rowClass = ROW_CLASS_BY_DEPTH[depth] ?? 'text-[0.7rem] text-[#6A6A6A] dark:text-[#A89C88]';

  const indent = depth * 16;

  return (
    <div>
      <div
        className={[
          'flex items-center gap-1.5 py-1 pr-2 group transition-colors rounded-xs',
          isSelected
            ? 'bg-[#B76E79]/8 dark:bg-[#E4B8BF]/12'
            : 'hover:bg-[#B76E79]/5 dark:hover:bg-[#E4B8BF]/8',
        ].join(' ')}
        style={{ paddingLeft: `${indent + 4}px` }}
      >
        {/* Chevron toggle for parents, spacer for leaves */}
        {hasChildren ? (
          <button
            type="button"
            onClick={() => onToggleExpand(node.id)}
            className="shrink-0 w-4 h-4 flex items-center justify-center text-pe-muted dark:text-[#D6C8B5]/55 hover:text-[#B76E79] dark:hover:text-[#E4B8BF] transition-colors"
            aria-label={isOpen ? 'Contraer' : 'Expandir'}
          >
            {isOpen ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
          </button>
        ) : (
          <span className="shrink-0 w-4 h-4 flex items-center justify-center text-pe-muted dark:text-[#D6C8B5]/30">
            <Tag size={9} />
          </span>
        )}

        <label className="flex items-center gap-2 flex-1 min-w-0 cursor-pointer">
          <input
            type="checkbox"
            className="w-3.5 h-3.5 shrink-0 accent-[#B76E79]"
            checked={isSelected}
            onChange={() => onToggle(node.id)}
          />
          {hasChildren && (
            <span className="shrink-0 text-pe-muted dark:text-[#D6C8B5]/55 group-hover:text-[#B76E79] dark:group-hover:text-[#E4B8BF] transition-colors">
              {isOpen ? <FolderOpen size={12} /> : <Folder size={12} />}
            </span>
          )}
          <span className={`font-sans leading-snug truncate group-hover:text-[#B76E79] dark:group-hover:text-[#E4B8BF] transition-colors ${rowClass}`}>
            {node.nameEs}
          </span>
          {hasChildren && (
            <span className="shrink-0 ml-auto font-sans text-[0.6rem] text-pe-muted dark:text-[#D6C8B5]/45">
              {descendantsSelected > 0
                ? `${descendantsSelected}/${node.children.length}`
                : node.children.length}
            </span>
          )}
        </label>
      </div>

      {hasChildren && isOpen && (
        <div>
          {node.children.map(child => (
            <CategoryTreeItem
              key={child.id}
              node={child}
              depth={depth + 1}
              selected={selected}
              onToggle={onToggle}
              expanded={expanded}
              onToggleExpand={onToggleExpand}
            />
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 5: Delete validateCategoryCompatibility and trim ValidateProductFormArgs/validateProductForm**

Find:

```typescript
/**
 * A category left over from a previous variant type. The tree greys these out, but a selection
 * made before the type changed stays operable so it can be untangled -- which means it can also
 * reach save. Refused here rather than silently stored: a shoe filed under "Vestidos" is wrong in
 * the catalogue long after anyone remembers why.
 */
function validateCategoryCompatibility(
  categories: CategoryDto[],
  selectedCatIds: string[],
  allowedCatIds: Set<string>,
): string | undefined {
  const incompatible = categories
    .filter((category) => selectedCatIds.includes(category.id))
    .filter((category) => !allowedCatIds.has(category.id));
  if (incompatible.length === 0) return undefined;
  return `Quita ${incompatible.map((c) => c.nameEs).join(', ')}: no aplica${
    incompatible.length > 1 ? 'n' : ''
  } al tipo de variante elegido.`;
}

export interface ValidateProductFormArgs {
  readonly form: typeof EMPTY_FORM;
  readonly variantRows: VariantRow[];
  readonly variantSchema: VariantSchema;
  readonly categories: CategoryDto[];
  readonly selectedCatIds: string[];
  readonly allowedCatIds: Set<string>;
  readonly primaryAttribute: CategoryAttributeDefinition;
  readonly secondaryAttribute: CategoryAttributeDefinition;
}

export function validateProductForm(args: ValidateProductFormArgs): Record<string, string> {
  const errors = validateBasicFields(args.form);

  const combinationsError = validateVariantRows(args.variantRows, args.variantSchema, args.primaryAttribute, args.secondaryAttribute);
  if (combinationsError) errors.combinations = combinationsError;

  const categoriesError = validateCategoryCompatibility(args.categories, args.selectedCatIds, args.allowedCatIds);
  if (categoriesError) errors.categories = categoriesError;

  return errors;
}
```

Replace with:

```typescript
export interface ValidateProductFormArgs {
  readonly form: typeof EMPTY_FORM;
  readonly variantRows: VariantRow[];
  readonly variantSchema: VariantSchema;
  readonly primaryAttribute: CategoryAttributeDefinition;
  readonly secondaryAttribute: CategoryAttributeDefinition;
}

export function validateProductForm(args: ValidateProductFormArgs): Record<string, string> {
  const errors = validateBasicFields(args.form);

  const combinationsError = validateVariantRows(args.variantRows, args.variantSchema, args.primaryAttribute, args.secondaryAttribute);
  if (combinationsError) errors.combinations = combinationsError;

  return errors;
}
```

- [ ] **Step 6: Replace the category-resolution state block with template state**

Find:

```typescript
  const resolvedConfig = useMemo(
    () => resolveVariantFieldConfig({ categoryIds: selectedCatIds, categories }),
    [selectedCatIds, categories],
  );
  /*
   * Computed once for the whole tree rather than per node: the rule walks descendants, so asking
   * each node in isolation would rewalk the same branches on every render.
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
```

Replace with:

```typescript
  const [variantTemplates, setVariantTemplates] = useState<VariantTemplateDto[]>([]);
  const [selectedVariantTemplateId, setSelectedVariantTemplateId] = useState<string | null>(null);
  const selectedTemplate = useMemo(
    () => variantTemplates.find((t) => t.id === selectedVariantTemplateId) ?? null,
    [variantTemplates, selectedVariantTemplateId],
  );
  const variantSchema = useMemo(
    () => buildVariantSchema(selectedTemplate?.config ?? null, selectedVariantTemplateId ?? 'GENERIC'),
    [selectedTemplate, selectedVariantTemplateId]
  );
```

- [ ] **Step 7: Fetch the template list once a token is available**

Find (the `getSystemSettings` effect, unchanged, used here only as an anchor):

```typescript
  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    getSystemSettings(token)
```

Insert immediately **before** that `useEffect` block:

```typescript
  useEffect(() => {
    if (!token) return;
    getVariantTemplates(token).then(setVariantTemplates).catch(() => {});
  }, [token]);

```

(leave the `getSystemSettings` effect itself untouched).

- [ ] **Step 8: Seed selectedVariantTemplateId from the product and thread it through makeSnapshot**

Find:

```typescript
  useEffect(() => {
    if (product) {
      const existingFlatVariants: FlatVariantRow[] = (product.variants ?? []).map((variant) => ({
        color: variant.color,
        size: variant.size,
        stock: String(variant.stock),
      }));
      const seededRows = existingFlatVariants.length > 0
        ? existingFlatVariants
        : [{ color: 'Base', size: 'UNICO', stock: String(product.stock) }];
      /* The rows are the truth; products.stock is derived from them since the resync landed. */
      const reconciledRows = seededRows;
      const nextForm = {
        name: product.name,
        description: product.description,
        amount: String(product.price.amount),
        listAmount: product.listPrice?.amount != null ? String(product.listPrice.amount) : '',
        currency: product.price.currency,
        imageUrl: product.imageUrl,
        condition: product.condition,
        brand: product.brand,
        stock: String(product.stock),
        active: product.active,
      };
      const nextRows = toVariantRows(reconciledRows, currentSchemaRef.current);
      setForm(nextForm);
      setVariantRows(nextRows);
      previousSchemaRef.current = currentSchemaRef.current;
      /*
       * The warning that the rows had been rewritten to match products.stock. Nothing rewrites
       * them any more — the aggregate is recomputed from the variants on every movement — so
       * there is nothing left to warn about.
       */
      setStockSyncHint('');
      // Categories are initialized by the separate categories useEffect.
      // Do not reset them here — doing so would undo interactive toggles when the schema changes.
      setInitialSnapshot(makeSnapshot(nextForm, nextRows, [], currentSchemaRef.current));
    } else {
      const nextForm = { ...EMPTY_FORM };
      const nextRows = [createVariantRow(currentSchemaRef.current, EMPTY_FORM.stock)];
      setForm(nextForm);
      setVariantRows(nextRows);
      previousSchemaRef.current = currentSchemaRef.current;
      setStockSyncHint('');
      // Categories for new products are cleared by the dedicated product-change effect below.
      setInitialSnapshot(makeSnapshot(nextForm, nextRows, [], currentSchemaRef.current));
    }
    setErrors({});
```

Replace with:

```typescript
  useEffect(() => {
    if (product) {
      const existingFlatVariants: FlatVariantRow[] = (product.variants ?? []).map((variant) => ({
        color: variant.color,
        size: variant.size,
        stock: String(variant.stock),
      }));
      const seededRows = existingFlatVariants.length > 0
        ? existingFlatVariants
        : [{ color: 'Base', size: 'UNICO', stock: String(product.stock) }];
      /* The rows are the truth; products.stock is derived from them since the resync landed. */
      const reconciledRows = seededRows;
      const nextForm = {
        name: product.name,
        description: product.description,
        amount: String(product.price.amount),
        listAmount: product.listPrice?.amount != null ? String(product.listPrice.amount) : '',
        currency: product.price.currency,
        imageUrl: product.imageUrl,
        condition: product.condition,
        brand: product.brand,
        stock: String(product.stock),
        active: product.active,
      };
      const nextRows = toVariantRows(reconciledRows, currentSchemaRef.current);
      setForm(nextForm);
      setVariantRows(nextRows);
      setSelectedVariantTemplateId(product.variantTemplateId ?? null);
      previousSchemaRef.current = currentSchemaRef.current;
      /*
       * The warning that the rows had been rewritten to match products.stock. Nothing rewrites
       * them any more — the aggregate is recomputed from the variants on every movement — so
       * there is nothing left to warn about.
       */
      setStockSyncHint('');
      // Categories are initialized by the separate categories useEffect.
      // Do not reset them here — doing so would undo interactive toggles when the schema changes.
      setInitialSnapshot(makeSnapshot(nextForm, nextRows, [], product.variantTemplateId ?? null, currentSchemaRef.current));
    } else {
      const nextForm = { ...EMPTY_FORM };
      const nextRows = [createVariantRow(currentSchemaRef.current, EMPTY_FORM.stock)];
      setForm(nextForm);
      setVariantRows(nextRows);
      setSelectedVariantTemplateId(null);
      previousSchemaRef.current = currentSchemaRef.current;
      setStockSyncHint('');
      // Categories for new products are cleared by the dedicated product-change effect below.
      setInitialSnapshot(makeSnapshot(nextForm, nextRows, [], null, currentSchemaRef.current));
    }
    setErrors({});
```

- [ ] **Step 9: Thread the template id through the categories-seeding effect's snapshot call**

Find:

```typescript
      const fixedIds = withAncestors(ids, categories);
      setSelectedCatIds(fixedIds);
      // Keep initialSnapshot with original ids so form is dirty when parents were auto-added,
      // forcing the user to save and persist the corrected category selection.
      setInitialSnapshot(makeSnapshot(snapshotForm, snapshotRows, ids, variantSchema));
```

Replace with:

```typescript
      const fixedIds = withAncestors(ids, categories);
      setSelectedCatIds(fixedIds);
      // Keep initialSnapshot with original ids so form is dirty when parents were auto-added,
      // forcing the user to save and persist the corrected category selection.
      setInitialSnapshot(makeSnapshot(snapshotForm, snapshotRows, ids, product.variantTemplateId ?? null, variantSchema));
```

- [ ] **Step 10: Drop categories/selectedCatIds/allowedCatIds from validate(), extend makeSnapshot**

Find:

```typescript
  function validate(): boolean {
    const e = validateProductForm({
      form, variantRows, variantSchema, categories, selectedCatIds, allowedCatIds, primaryAttribute, secondaryAttribute,
    });
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function compareIds(left: string, right: string): number {
    if (left === right) return 0;
    return left < right ? -1 : 1;
  }

  function makeSnapshot(nextForm: typeof form, nextRows: VariantRow[], nextCatIds: string[], schema: VariantSchema): string {
    const variants = normalizeVariantRows(nextRows, schema)
      .map((variant) => ({
        color: variant.color.trim().toLowerCase(),
        size: variant.size,
        stock: Number(variant.stock),
      }))
      .sort((a, b) => `${a.color}::${a.size}`.localeCompare(`${b.color}::${b.size}`));
    // Explicitly ordered, and deliberately not by locale: this is a key for comparing snapshots,
    // so it has to be the same string on every machine.
    const cats = [...nextCatIds].sort(compareIds);
    return JSON.stringify({
      name: nextForm.name.trim(),
      description: nextForm.description.trim(),
      amount: nextForm.amount.trim(),
      listAmount: nextForm.listAmount.trim(),
      imageUrl: nextForm.imageUrl.trim(),
      condition: nextForm.condition,
      brand: nextForm.brand.trim(),
      active: nextForm.active,
      categories: cats,
      variants,
    });
  }

  const currentSnapshot = makeSnapshot(form, variantRows, selectedCatIds, variantSchema);
```

Replace with:

```typescript
  function validate(): boolean {
    const e = validateProductForm({
      form, variantRows, variantSchema, primaryAttribute, secondaryAttribute,
    });
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function compareIds(left: string, right: string): number {
    if (left === right) return 0;
    return left < right ? -1 : 1;
  }

  function makeSnapshot(
    nextForm: typeof form,
    nextRows: VariantRow[],
    nextCatIds: string[],
    nextTemplateId: string | null,
    schema: VariantSchema,
  ): string {
    const variants = normalizeVariantRows(nextRows, schema)
      .map((variant) => ({
        color: variant.color.trim().toLowerCase(),
        size: variant.size,
        stock: Number(variant.stock),
      }))
      .sort((a, b) => `${a.color}::${a.size}`.localeCompare(`${b.color}::${b.size}`));
    // Explicitly ordered, and deliberately not by locale: this is a key for comparing snapshots,
    // so it has to be the same string on every machine.
    const cats = [...nextCatIds].sort(compareIds);
    return JSON.stringify({
      name: nextForm.name.trim(),
      description: nextForm.description.trim(),
      amount: nextForm.amount.trim(),
      listAmount: nextForm.listAmount.trim(),
      imageUrl: nextForm.imageUrl.trim(),
      condition: nextForm.condition,
      brand: nextForm.brand.trim(),
      active: nextForm.active,
      variantTemplateId: nextTemplateId,
      categories: cats,
      variants,
    });
  }

  const currentSnapshot = makeSnapshot(form, variantRows, selectedCatIds, selectedVariantTemplateId, variantSchema);
```

- [ ] **Step 11: Add variantTemplateId to the submit payload**

Find:

```typescript
      const payload: CreateProductRequest = {
        name: form.name.trim(),
        description: form.description.trim(),
        price: { amount: Number(form.amount), currency: form.currency },
        ...(form.listAmount.trim()
          ? { listPrice: { amount: Number(form.listAmount), currency: form.currency } }
          : {}),
        imageUrl: form.imageUrl.trim() || '/api/media/products/product-001.jpg',
        condition: form.condition,
        brand: form.brand.trim(),
        stock: variantTotalStock,
        active: form.active,
        categoryIds: selectedCatIds,
        variants: normalizedVariants,
      };
```

Replace with:

```typescript
      const payload: CreateProductRequest = {
        name: form.name.trim(),
        description: form.description.trim(),
        price: { amount: Number(form.amount), currency: form.currency },
        ...(form.listAmount.trim()
          ? { listPrice: { amount: Number(form.listAmount), currency: form.currency } }
          : {}),
        imageUrl: form.imageUrl.trim() || '/api/media/products/product-001.jpg',
        condition: form.condition,
        brand: form.brand.trim(),
        stock: variantTotalStock,
        active: form.active,
        categoryIds: selectedCatIds,
        variantTemplateId: selectedVariantTemplateId || undefined,
        variants: normalizedVariants,
      };
```

- [ ] **Step 12: Insert the "Tipo de Variante" dropdown and simplify the category tree JSX**

Find (the opening of the variant-editor section):

```typescript
          <div className="border border-pe-black/12 dark:border-[#3F2A2F] bg-pe-white dark:bg-[#1F1518] p-3 space-y-3">
            <div className="flex items-center justify-between">
              <p className={labelClass + ' mb-0'}>{variantSchema.title}</p>
```

Replace with:

```typescript
          <div>
            <label htmlFor="pf-variant-template" className={labelClass}>
              Tipo de Variante
            </label>
            <select
              id="pf-variant-template"
              className={inputClass}
              value={selectedVariantTemplateId ?? ''}
              onChange={(e) => setSelectedVariantTemplateId(e.target.value || null)}
            >
              <option value="">Generico (Variante + Detalle)</option>
              {variantTemplates.map((t) => (
                <option key={t.id} value={t.id}>{t.name}</option>
              ))}
            </select>
          </div>

          <div className="border border-pe-black/12 dark:border-[#3F2A2F] bg-pe-white dark:bg-[#1F1518] p-3 space-y-3">
            <div className="flex items-center justify-between">
              <p className={labelClass + ' mb-0'}>{variantSchema.title}</p>
```

Find (the category tree JSX):

```typescript
              <div className="border border-[#EDE3D8] dark:border-[#3F2A2F] bg-[#FDFAF6] dark:bg-[#1F1518] py-1.5 max-h-60 overflow-y-auto">
                {categoryTree.map(root => (
                  <CategoryTreeItem
                    key={root.id}
                    node={root}
                    allowedIds={allowedCatIds}
                    lockedHint={variantSchema.title}
                    depth={0}
                    selected={selectedCatIds}
                    onToggle={toggleCategory}
                    expanded={expandedCatIds}
                    onToggleExpand={toggleCategoryExpanded}
                  />
                ))}
              </div>
              {errors.categories && <p className={errorClass}>{errors.categories}</p>}
              <p className="font-sans text-[0.6rem] text-pe-muted dark:text-[#D6C8B5]/45 mt-1">
                Al seleccionar una subcategoría, su categoría padre se marca automáticamente.
                Las que no aplican al tipo de variante aparecen atenuadas.
              </p>
```

Replace with:

```typescript
              <div className="border border-[#EDE3D8] dark:border-[#3F2A2F] bg-[#FDFAF6] dark:bg-[#1F1518] py-1.5 max-h-60 overflow-y-auto">
                {categoryTree.map(root => (
                  <CategoryTreeItem
                    key={root.id}
                    node={root}
                    depth={0}
                    selected={selectedCatIds}
                    onToggle={toggleCategory}
                    expanded={expandedCatIds}
                    onToggleExpand={toggleCategoryExpanded}
                  />
                ))}
              </div>
              <p className="font-sans text-[0.6rem] text-pe-muted dark:text-[#D6C8B5]/45 mt-1">
                Al seleccionar una subcategoría, su categoría padre se marca automáticamente.
              </p>
```

- [ ] **Step 13: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/ProductForm.test.tsx src/islands/admin/__tests__/ProductFormValidation.test.ts`
Expected: PASS, 4/4 and 9/9.

- [ ] **Step 14: Commit**

```bash
git add frontend/src/islands/admin/ProductForm.tsx frontend/src/islands/admin/__tests__/ProductForm.test.tsx frontend/src/islands/admin/__tests__/ProductFormValidation.test.ts
git commit -m "feat: rewire ProductForm.tsx to the variant template dropdown"
```

---

## Task 16: Rename the type import in ProductVariantSelector.tsx

**Files:**
- Modify: `frontend/src/islands/product/ProductVariantSelector.tsx`

**Interfaces:**
- Consumes: `VariantFieldConfigDto` (Task 11, renamed from `CategoryVariantFieldConfigDto`).

This is the only storefront file that explicitly imports the type name (rather than only
referencing the field structurally) — `ProductCard.astro` and `[locale]/products/[id].astro` were
grep-confirmed during planning to reference `product.variantFieldConfig`/`buildVariantSchema` only,
with no explicit type import, so they need zero changes.

- [ ] **Step 1: Rename the import and prop type**

Find:

```typescript
import type { CategoryVariantFieldConfigDto, ProductVariantDto } from '../../lib/api';
```

Replace with:

```typescript
import type { VariantFieldConfigDto, ProductVariantDto } from '../../lib/api';
```

Find:

```typescript
  readonly variantFieldConfig?: CategoryVariantFieldConfigDto | null;
```

Replace with:

```typescript
  readonly variantFieldConfig?: VariantFieldConfigDto | null;
```

- [ ] **Step 2: Typecheck the whole frontend**

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS with zero TypeScript errors — this is the first point since Task 11 where
the frontend compiles clean end-to-end (Tasks 12-16 have now fixed every file Task 11's renames
broke).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/islands/product/ProductVariantSelector.tsx
git commit -m "refactor: rename CategoryVariantFieldConfigDto to VariantFieldConfigDto in ProductVariantSelector"
```

---

## Task 17: End-to-end verification

**Files:** none (verification only — no code changes)

**Interfaces:** none.

Mirrors the rigor of the previous cycle's final task: full backend + product-service test suites,
a full Docker stack rebuild, and a manual/Playwright walk of the actual admin flows this plan
built, plus a Sonar gate check. This is what actually proves `VariantTemplateRepositoryAdapter`'s
JSONB round-trip and `VariantTemplateController`'s RBAC — the two pieces this plan deliberately did
not give dedicated unit/IT tests to (Global Constraints test-tier ruling).

- [ ] **Step 1: Run the full backend suite**

Run: `cd backend && mvn clean verify`
Expected: BUILD SUCCESS, 0 failures — every unit test from Tasks 2-9 plus the pre-existing
`ProductControllerIT`/`ProductRepositoryAdapterIT` (which now exercise `variant_template_id`
indirectly through their existing fixtures, still defaulting to `null`/generic).

- [ ] **Step 2: Run the full product-service suite**

Run: `cd services/product-service && mvn clean verify`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 3: Run the full frontend suite**

Run: `cd frontend && npm run build && npx vitest run`
Expected: BUILD SUCCESS; all vitest suites green, including the new/rewritten
`VariantTemplateTable.test.tsx`, `ProductForm.test.tsx`, `ProductFormValidation.test.ts`,
`variantSchema.test.ts`.

- [ ] **Step 4: Stop every running Docker profile before rebuilding**

Run: `cd infra && docker compose --env-file .env --profile kafka --profile cache --profile microservices --profile observability --profile tracing down`
Expected: all containers stop cleanly. (Testcontainers used by Step 1 conflicts with a running
compose stack on the same ports — stop everything first, exactly as this repo's established
verify-before-pushing discipline requires.)

- [ ] **Step 5: Rebuild and start the full stack**

Run:
```bash
cd infra && docker compose --env-file .env \
  --profile kafka --profile cache --profile microservices \
  --profile observability --profile tracing up -d --build
```
Expected: every service (backend, product-service, frontend, postgres, kafka, redis, caddy) comes
up healthy. Confirm with `docker compose ps` — no container in a restart loop.

- [ ] **Step 6: Log in as admin and create a variant template via the new CMS page**

Open `http://localhost/admin/tipos-variante` (seed admin: `admin@pilarestilo.com` / `admin2026`).
Create a template named "Zapatos" with primary field `Color` (FREE_TEXT) and secondary field
`Numero` (RANGE, min 34, max 43). Confirm it appears in the list with the "Color / Numero" badge.

- [ ] **Step 7: Assign the template to a product and confirm the storefront reflects it**

Open `/admin/products`, edit an existing product, select "Zapatos" in the new "Tipo de Variante"
dropdown, add a variant row with Color=Blanco, Numero=38, save. Confirm:
- The admin form now shows "Color"/"Numero(s)" labels and a 34-43 range picker for that product.
- `curl http://localhost/api/products/{id}` (monolith, via Caddy's `POST/PATCH/DELETE` route —
  use the direct read route or `GET` which Caddy sends to `product-service` under the
  `microservices` profile) returns `variantFieldConfig.secondary.label == "Numero"` and
  `inputType == "RANGE"`.
- The storefront product page (`/es/products/{slug}`, or whatever route renders
  `ProductVariantSelector`) renders the Numero range picker with values 34-43.

- [ ] **Step 8: Confirm write-time rejection of an out-of-range value**

`curl -X PATCH http://localhost:8080/api/products/{id}` (direct to the monolith, bypassing
Caddy's read-routing, since writes always hit the monolith) with a variant `size: "99"` for a
product assigned to the Zapatos template (RANGE 34-43, `allowCustom: true` per the CMS default —
so first edit the template via the CMS to uncheck "Permitir un valor fuera de la lista" on the
secondary field, save, then retry). Confirm the response is a 4xx with a message naming "Numero"
and the 34-43 bounds.

- [ ] **Step 9: Confirm deleting an in-use template is refused**

`curl -X DELETE http://localhost:8080/api/variant-templates/{zapatos-template-id}` with an admin
bearer token, while the product from Step 7 still references it. Confirm a 4xx response
("Cannot delete variant template with associated products..."), then unassign the template from
that product (set "Tipo de Variante" back to Generico) and retry — confirm the delete now
succeeds (204).

- [ ] **Step 10: Confirm a non-ADMIN user cannot reach the template catalogue**

Log in as a SELLER (or any non-ADMIN) user and confirm `/admin/tipos-variante` does not appear in
the sidebar; `curl http://localhost:8080/api/variant-templates` with that user's bearer token
returns 403.

- [ ] **Step 11: Confirm the category admin page no longer shows variant-field UI**

Open `/admin/categories`, edit any category, confirm there is no "esta categoria define campos de
variante" checkbox and no variant badge on any row — the page matches its pre-feature shape.

- [ ] **Step 12: Restore the previous local Docker state**

```bash
cd infra && docker compose --env-file .env --profile kafka --profile cache --profile microservices --profile observability --profile tracing down
```
Then bring back whatever subset of profiles was running before this verification pass, matching
this repo's documented verify-before-pushing discipline.

- [ ] **Step 13: Run the Sonar scan**

Run: `SONAR_TOKEN=<token> bash scripts/quality/sonar-scan.sh`
Expected: quality gate **PilarEstilo sin smells** passes on all affected projects (backend,
product-service, frontend) — `new_violations = 0`, coverage ≥ 60%/50% (backend/service) or the
frontend's line-only threshold, hotspots reviewed. Fix anything flagged before merging.

- [ ] **Step 14: Merge and confirm CI**

Once all of the above is green, merge this branch's commits to `develop`, then to `master`
following this repo's normal flow, and confirm CI, CodeQL, and the VPS deploy workflow all report
green on the resulting commit — the same closing check every prior cycle in this project has used.

---

## Plan Self-Review

**Spec coverage:**
- Data model (new `varianttemplate` module, `VariantFieldConfig`, migrations V89/V90, delete guard)
  → Tasks 1-4.
- Backend write-time enforcement (`VariantTemplateValidator`, `Product`/use-case wiring) → Tasks 7-8.
- Category-side removal (drop `definesVariantFields`/`variantFieldConfig`/
  `ShapeCategoryResolver`/`CategoryVariantFieldValidator`, columns left in DB) → Task 9.
- RBAC ruling (`hasRole('ADMIN')` on every `VariantTemplateController` method, no new permission
  catalog entries) → recorded in Global Constraints, implemented in Task 6.
- Frontend: new CMS page, `ProductForm.tsx` dropdown, `CategoryTree.tsx` reverted,
  `allowedCategoryIdsFor` deleted → Tasks 11-15.
- Storefront unchanged in shape → Task 16 confirms the one remaining type-only touch point;
  `ProductCard.astro`/`[id].astro` need nothing (confirmed via grep during planning, not re-stated
  as a task since there is nothing to do).
- product-service parity, shipped in the same commit set → Task 10.
- Testing approach (ported `CategoryVariantFieldValidatorTest` cases, `ProductMapperTest` parity
  tests, frontend characterization tests for the deleted locking behavior and new dropdown/CMS
  page) → Tasks 2, 3, 5, 7, 10, 14, 15.
- End-to-end verification checklist (create template, assign, storefront+admin reflect it via both
  product-service and the monolith, write-time rejection, delete-in-use refusal) → Task 17.

**Placeholder scan:** every task's code blocks are complete, compilable Java/TypeScript — no
"TBD", no "similar to Task N", no prose standing in for code. The two places this plan deliberately
narrates rather than codes (the Docker/curl verification steps in Task 17, and the "no dedicated
test" rulings in the Global Constraints) are process steps and rulings, not implementation steps
with missing code.

**Type consistency:** `VariantFieldConfig`/`VariantFieldConfig.FieldConfig`/`InputType` (Task 2) are
used with identical shape in Tasks 3-10; `VariantTemplateDto`/`VariantFieldRequest` (Task 5) match
what Task 6's controller and Task 11's frontend types consume; `CreateProductUseCase`/
`UpdateProductUseCase`'s `variantTemplateId` parameter position (Task 8) matches
`CreateProductRequest`/`UpdateProductRequest`'s field position and `ProductController`'s call
sites; `VariantFieldDto`/`VariantFieldConfigDto` (Task 11) are consumed identically by Tasks 12-16.
