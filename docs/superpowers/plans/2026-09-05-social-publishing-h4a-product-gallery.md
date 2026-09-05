# Product Image Gallery + SEO Wiring — Implementation Plan (H-4a)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `Product` an ordered list of additional images, an admin editor for it, and feed those images to the `Product` JSON-LD and the Google Merchant feed.

**Architecture:** `products.image_url` stays the cover, unchanged. A new `product_images` JPA `@ElementCollection` (collection table, `@OrderColumn`) holds the additional images, full-replace on save — the same pattern as `product_variants` / `product_size_stocks`. The domain `Product` normalizes the list (trim, drop blank, dedupe, cap 9). The value plumbs through the request records, `ProductDto`, `ProductMapper`, the use cases and the controller, then out to `frontend/src/lib/seo.ts` (`image: [cover, ...gallery]`) and `frontend/src/lib/merchantFeed.ts` (`<g:additional_image_link>` per gallery URL).

**Tech Stack:** Spring Boot 4 (Java 25), JPA/Hibernate, Flyway, Testcontainers + MockMvc, JUnit 5 + Mockito; Astro 5 SSR + React islands, Vitest + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-05-social-publishing-h4a-product-gallery-design.md`

## Global Constraints

- Hexagonal: domain objects carry no framework annotations; one use-case class per action; JPA entities separate from domain models.
- Flyway only; never edit an applied migration. Current highest is **V101** → this adds **V102**.
- `products.image_url` is unchanged in meaning and value. Every existing reader keeps working untouched.
- `notification-service` does not map products or product images — a new `product_images` table needs no `*RoEntity` change. `ReadOnlyMappingIT` still runs V102, so V102 must be valid on its own.
- Gallery cap: **9 additional images** (cover + 9 = 10, the Instagram carousel limit, within Google's 10-additional-image feed limit).
- Jackson 3: `tools.jackson.databind.ObjectMapper`, never `com.fasterxml`.
- Frontend `ProductDto` post-core fields are optional (`sizeStocks?`, `variants?`); `galleryImageUrls?: string[]` follows that, and consumers use `?? []`.
- Backend build/test: `cd backend && mvn test -Dtest=<Class>`. Frontend: `cd frontend && npx vitest run <path>` and `npx tsc --noEmit`.
- Out of scope: visible storefront gallery/carousel, IG/FB carousel posting (that is H-4b), the AI pipeline writing the gallery, per-image alt text, variant-specific images.

---

## Task 1: Domain — `Product.galleryImageUrls` + normalization

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/product/domain/model/Product.java`
- Test: `backend/src/test/java/com/pilarestilo/product/domain/model/ProductGalleryImagesTest.java` (create)

**Interfaces:**
- Produces:
  - `List<String> Product.getGalleryImageUrls()` — returns a copy, never null.
  - `void Product.setGalleryImageUrls(List<String> urls)` — normalizes: null→empty, trim each, drop blanks, dedupe preserving first-seen order, keep at most the first 9. Stores a fresh `ArrayList`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/pilarestilo/product/domain/model/ProductGalleryImagesTest.java`:

```java
package com.pilarestilo.product.domain.model;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductGalleryImagesTest {

    private Product newProduct() {
        return Product.create("Abrigo", "d", new Money(BigDecimal.valueOf(29990), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "Pilar", 5);
    }

    @Test
    void defaults_to_an_empty_list() {
        assertEquals(List.of(), newProduct().getGalleryImageUrls());
    }

    @Test
    void null_clears_to_empty() {
        Product p = newProduct();
        p.setGalleryImageUrls(List.of("https://img/a.jpg"));
        p.setGalleryImageUrls(null);
        assertEquals(List.of(), p.getGalleryImageUrls());
    }

    @Test
    void trims_drops_blanks_and_dedupes_preserving_order() {
        Product p = newProduct();
        p.setGalleryImageUrls(new ArrayList<>(List.of(
                "  https://img/a.jpg  ", "", "   ", "https://img/b.jpg", "https://img/a.jpg")));
        assertEquals(List.of("https://img/a.jpg", "https://img/b.jpg"), p.getGalleryImageUrls());
    }

    @Test
    void caps_at_nine_keeping_the_first_nine() {
        Product p = newProduct();
        List<String> twelve = IntStream.range(0, 12).mapToObj(i -> "https://img/" + i + ".jpg").toList();
        p.setGalleryImageUrls(twelve);
        assertEquals(twelve.subList(0, 9), p.getGalleryImageUrls());
    }

    @Test
    void getter_returns_a_copy() {
        Product p = newProduct();
        p.setGalleryImageUrls(List.of("https://img/a.jpg"));
        assertThrows(UnsupportedOperationException.class,
                () -> p.getGalleryImageUrls().add("https://img/x.jpg"));
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=ProductGalleryImagesTest`
Expected: FAIL — `getGalleryImageUrls()` / `setGalleryImageUrls(...)` do not exist (compile error).

- [ ] **Step 3: Implement**

In `Product.java`, add the field next to `variants` (around line 42):

```java
    private List<String> galleryImageUrls = new ArrayList<>();
```

Add near the other collection getters/setters (after `setVariants`, around line 239):

```java
    /** Additional images beyond the cover ({@link #getImageUrl()}), in display order. */
    public List<String> getGalleryImageUrls() {
        return List.copyOf(galleryImageUrls);
    }

    private static final int MAX_GALLERY_IMAGES = 9;

    public void setGalleryImageUrls(List<String> urls) {
        if (urls == null) {
            this.galleryImageUrls = new ArrayList<>();
            return;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String url : urls) {
            if (url == null) {
                continue;
            }
            String trimmed = url.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        this.galleryImageUrls = unique.stream().limit(MAX_GALLERY_IMAGES)
                .collect(Collectors.toCollection(ArrayList::new));
    }
```

Add the imports `java.util.LinkedHashSet` and `java.util.stream.Collectors` to `Product.java` (it already imports `java.util.ArrayList`, `java.util.List`).

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=ProductGalleryImagesTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/product/domain/model/Product.java \
        backend/src/test/java/com/pilarestilo/product/domain/model/ProductGalleryImagesTest.java
git commit -m "feat(product): Product.galleryImageUrls with trim/dedupe/cap-9 normalization"
```

---

## Task 2: Migration V102 + `ProductEntity` collection + repository adapter mapping

**Files:**
- Create: `backend/src/main/resources/db/migration/V102__product_images.sql`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/entities/ProductEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductRepositoryAdapter.java` (`applyToEntity` ~line 288, `toDomain` ~line 336)
- Test: `backend/src/test/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductRepositoryAdapterIT.java`

**Interfaces:**
- Consumes: `Product.getGalleryImageUrls()` / `setGalleryImageUrls(...)` from Task 1.
- Produces: `List<String> ProductEntity.getGalleryImageUrls()` / `setGalleryImageUrls(List<String>)`; a persisted `product_images` table; `ProductRepository.save` / `findById` round-trip the ordered list.

- [ ] **Step 1: Write the failing test**

Add to `ProductRepositoryAdapterIT.java` (it already has `@Autowired ProductRepository repository` — confirm the field name in the file; the existing tests use it):

```java
    @Test
    void round_trips_the_image_gallery_in_order() {
        Product p = Product.create("ZT-Gallery", "d", new Money(BigDecimal.valueOf(19990), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "ZT-Brand", 3);
        p.setGalleryImageUrls(List.of("https://img/1.jpg", "https://img/2.jpg", "https://img/3.jpg"));
        UUID id = repository.save(p).getId();

        Product reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getGalleryImageUrls())
                .containsExactly("https://img/1.jpg", "https://img/2.jpg", "https://img/3.jpg");
    }

    @Test
    void reorders_and_clears_the_gallery_on_update() {
        Product p = Product.create("ZT-Gallery2", "d", new Money(BigDecimal.valueOf(19990), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "ZT-Brand", 3);
        p.setGalleryImageUrls(List.of("https://img/a.jpg", "https://img/b.jpg"));
        UUID id = repository.save(p).getId();

        Product toReorder = repository.findById(id).orElseThrow();
        toReorder.setGalleryImageUrls(List.of("https://img/b.jpg", "https://img/a.jpg"));
        repository.save(toReorder);
        assertThat(repository.findById(id).orElseThrow().getGalleryImageUrls())
                .containsExactly("https://img/b.jpg", "https://img/a.jpg");

        Product toClear = repository.findById(id).orElseThrow();
        toClear.setGalleryImageUrls(List.of());
        repository.save(toClear);
        assertThat(repository.findById(id).orElseThrow().getGalleryImageUrls()).isEmpty();
    }
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=ProductRepositoryAdapterIT`
Expected: FAIL — `ProductEntity` has no `galleryImageUrls`; and/or Flyway validation fails because `product_images` does not exist.

- [ ] **Step 3: Create the migration**

`backend/src/main/resources/db/migration/V102__product_images.sql`:

```sql
-- Additional product images beyond products.image_url (the cover), in display order.
-- Populated by the admin product form; consumed by the Product JSON-LD, the Merchant feed,
-- and (later, H-4b) the social carousel. sort_order is the 0-based list index Hibernate
-- maintains from @OrderColumn.
CREATE TABLE product_images (
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_url  TEXT NOT NULL,
    sort_order INT  NOT NULL,
    PRIMARY KEY (product_id, sort_order)
);

CREATE INDEX idx_product_images_product ON product_images (product_id);
```

- [ ] **Step 4: Add the collection to `ProductEntity`**

In `ProductEntity.java`, after the `variants` collection (around line 87) add:

```java
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", nullable = false)
    private List<String> galleryImageUrls = new ArrayList<>();
```

`ProductEntity` uses `import jakarta.persistence.*;` so `@OrderColumn` needs no new import. Add the getter/setter next to `getVariants`/`setVariants` (around line 114):

```java
    public List<String> getGalleryImageUrls() { return galleryImageUrls; }
    public void setGalleryImageUrls(List<String> galleryImageUrls) { this.galleryImageUrls = galleryImageUrls; }
```

- [ ] **Step 5: Map it in `ProductRepositoryAdapter`**

In `applyToEntity` (after the `variantEmbeddables` block, ~line 288), add — a mutable list, same reasoning as the `@SuppressWarnings("java:S6204")` on the sibling collections:

```java
        @SuppressWarnings("java:S6204")
        List<String> galleryImages = new ArrayList<>(product.getGalleryImageUrls());
        entity.setGalleryImageUrls(galleryImages);
```

In `toDomain` (after `product.setVariants(variants);` and its `if (variants.isEmpty())` block, ~line 341), add:

```java
        product.setGalleryImageUrls(
                entity.getGalleryImageUrls() == null ? List.of() : entity.getGalleryImageUrls());
```

`ProductRepositoryAdapter` already imports `java.util.ArrayList` and `java.util.List`.

- [ ] **Step 6: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=ProductRepositoryAdapterIT`
Expected: PASS (all existing tests + the 2 new ones). If Hibernate throws `MultipleBagFetchException` at startup, the `@OrderColumn` was omitted — it makes the list an indexed list, not a bag; re-add it.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V102__product_images.sql \
        backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/entities/ProductEntity.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductRepositoryAdapter.java \
        backend/src/test/java/com/pilarestilo/product/infrastructure/persistence/repositories/ProductRepositoryAdapterIT.java
git commit -m "feat(product): V102 product_images collection table, mapped through the repo adapter"
```

---

## Task 3: `ProductDto` + `ProductMapper` + use cases + controller + request records

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/product/application/dto/ProductDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/mappers/ProductMapper.java:24-48`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/usecases/CreateProductUseCase.java:43-88`
- Modify: `backend/src/main/java/com/pilarestilo/product/application/usecases/UpdateProductUseCase.java:44-89`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/CreateProductRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/UpdateProductRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/product/infrastructure/web/controllers/ProductController.java:67-119`
- Test: `backend/src/test/java/com/pilarestilo/product/infrastructure/web/ProductControllerIT.java`

**Interfaces:**
- Consumes: `Product.setGalleryImageUrls` / `getGalleryImageUrls` (Task 1).
- Produces:
  - `ProductDto` gains a trailing `List<String> galleryImageUrls` component.
  - `CreateProductRequest` / `UpdateProductRequest` gain a trailing `List<String> galleryImageUrls`.
  - `CreateProductUseCase.execute(...)` fullest overload gains a trailing `List<String> galleryImageUrls`; the shorter overload passes `List.of()`.
  - `UpdateProductUseCase.execute(...)` fullest overload gains a trailing `List<String> galleryImageUrls`; the shorter overload passes `List.of()`.

- [ ] **Step 1: Write the failing test**

Add to `ProductControllerIT.java`:

```java
    @Test
    @WithMockUser(username = "admin@pilarestilo.com", roles = {"ADMIN"})
    void create_product_stores_and_returns_the_image_gallery() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Abrigo con galeria",
                "description", "d",
                "priceAmount", 45000,
                "imageUrl", "https://example.com/cover.jpg",
                "condition", "NEW",
                "brand", "Pilar",
                "stock", 2,
                "galleryImageUrls", List.of("https://example.com/1.jpg", "https://example.com/2.jpg")
        ));

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.galleryImageUrls[0]").value("https://example.com/1.jpg"))
                .andExpect(jsonPath("$.galleryImageUrls[1]").value("https://example.com/2.jpg"));
    }

    @Test
    @WithMockUser(username = "admin@pilarestilo.com", roles = {"ADMIN"})
    void omitting_the_gallery_yields_an_empty_list() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Abrigo sin galeria", "description", "d", "priceAmount", 45000,
                "imageUrl", "https://example.com/cover.jpg", "condition", "NEW", "brand", "Pilar", "stock", 2));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.galleryImageUrls").isArray())
                .andExpect(jsonPath("$.galleryImageUrls").isEmpty());
    }
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=ProductControllerIT`
Expected: FAIL — the response has no `galleryImageUrls`; the request record ignores the unknown field.

- [ ] **Step 3: `ProductDto` — add the trailing component**

In `ProductDto.java`, add after `List<VariantDto> variants` (line 31):

```java
        List<VariantDto> variants,
        List<String> galleryImageUrls
```

- [ ] **Step 4: `ProductMapper` — map it**

In `ProductMapper.toDto` (line 24-48), add as the last constructor argument after `variants`:

```java
                variants,
                product.getGalleryImageUrls()
```

- [ ] **Step 5: Request records**

`CreateProductRequest.java` — add after `List<@Valid ProductVariantRequest> variants` (line 48):

```java
        List<@Valid ProductVariantRequest> variants,

        List<String> galleryImageUrls
```

`UpdateProductRequest.java` — the same trailing `List<String> galleryImageUrls` after its `variants` component.

- [ ] **Step 6: Use cases**

`CreateProductUseCase.java` — the 13-arg overload (line 43-49) delegates; change its delegation call to pass an empty gallery:

```java
        return execute(name, description, priceAmount, priceCurrency, listPriceAmount,
                listPriceCurrency, imageUrl, condition, brand, stock, active, categoryIds,
                variantTemplateId, null, List.of());
```

The fullest overload (line 51-88) gains the trailing parameter and sets it after `Product.create`:

```java
    @SuppressWarnings("java:S107")
    @Transactional
    public ProductDto execute(String name, String description, BigDecimal priceAmount, String priceCurrency,
                               BigDecimal listPriceAmount, String listPriceCurrency,
                               String imageUrl, String condition, String brand, int stock,
                               Boolean active, Set<UUID> categoryIds, UUID variantTemplateId,
                               List<ProductVariantInput> variants, List<String> galleryImageUrls) {
        // ... unchanged up to and including Product.create(...) ...
        Product product = Product.create(name, description, price, imageUrl, productCondition, brand, stock, listPrice);
        product.setGalleryImageUrls(galleryImageUrls);
        // ... rest unchanged ...
```

`UpdateProductUseCase.java` — mirror it: the 14-arg delegating overload (line 44-50) passes `..., null, List.of()`; the fullest overload (line 52-89) gains trailing `List<String> galleryImageUrls` and adds, right after `product.update(...)` (line 74):

```java
        product.update(name, description, price, imageUrl, productCondition, brand, stock, active, listPrice);
        product.setGalleryImageUrls(galleryImageUrls);
```

- [ ] **Step 7: Controller**

`ProductController.java` — `create` (line 69-74) passes the new arg:

```java
        ProductDto dto = createProductUseCase.execute(
                request.name(), request.description(), request.priceAmount(), request.priceCurrency(),
                request.listPriceAmount(), request.listPriceCurrency(),
                request.imageUrl(), request.condition(), request.brand(), request.stock(),
                request.active(), request.categoryIds(), request.variantTemplateId(),
                toVariantInputs(request.variants()), request.galleryImageUrls()
        );
```

`update` (line 112-118) mirrors it with `request.galleryImageUrls()` as the final argument.

- [ ] **Step 8: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=ProductControllerIT`
Expected: PASS (all existing + 2 new).

- [ ] **Step 9: Run the product test suite for regressions**

Run: `cd backend && mvn test -Dtest='com.pilarestilo.product.**,com.pilarestilo.productai.**'`
Expected: PASS. `CreateProductUseCaseTest` / `CreateProductUseCaseVariantValidationTest` / `ProductAiService` call the shorter overloads and are unaffected.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/product/application/dto/ProductDto.java \
        backend/src/main/java/com/pilarestilo/product/application/mappers/ProductMapper.java \
        backend/src/main/java/com/pilarestilo/product/application/usecases/CreateProductUseCase.java \
        backend/src/main/java/com/pilarestilo/product/application/usecases/UpdateProductUseCase.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/CreateProductRequest.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/web/requests/UpdateProductRequest.java \
        backend/src/main/java/com/pilarestilo/product/infrastructure/web/controllers/ProductController.java \
        backend/src/test/java/com/pilarestilo/product/infrastructure/web/ProductControllerIT.java
git commit -m "feat(product): galleryImageUrls through the request/DTO/use-case/controller layer"
```

---

## Task 4: `api.ts` — types + normalizer

**Files:**
- Modify: `frontend/src/lib/api.ts` (`ProductDto` ~line 64, `CreateProductRequest` ~line 622, `UpdateProductRequest` ~line 637, `normalizeProduct` ~line 907)
- Test: `frontend/src/lib/__tests__/api.normalizeProduct.test.ts` (create) — or, if a normalizer test file already exists, add to it.

**Interfaces:**
- Produces: `ProductDto.galleryImageUrls?: string[]` (always an array after `normalizeProduct`); `CreateProductRequest.galleryImageUrls?: string[]`; `UpdateProductRequest.galleryImageUrls?: string[]`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/lib/__tests__/api.normalizeProduct.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { __test__normalizeProduct as normalizeProduct } from '../api';

describe('normalizeProduct — galleryImageUrls', () => {
  it('passes the backend list through', () => {
    const p = normalizeProduct({
      id: 'p1', name: 'x', description: '', priceAmount: 1000, priceCurrency: 'CLP',
      imageUrl: '/c.jpg', condition: 'NEW', brand: 'b', stock: 1, active: true,
      createdAt: '', updatedAt: '', galleryImageUrls: ['/1.jpg', '/2.jpg'],
    });
    expect(p.galleryImageUrls).toEqual(['/1.jpg', '/2.jpg']);
  });

  it('defaults a missing list to []', () => {
    const p = normalizeProduct({
      id: 'p1', name: 'x', description: '', priceAmount: 1000, priceCurrency: 'CLP',
      imageUrl: '/c.jpg', condition: 'NEW', brand: 'b', stock: 1, active: true,
      createdAt: '', updatedAt: '',
    });
    expect(p.galleryImageUrls).toEqual([]);
  });
});
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd frontend && npx vitest run src/lib/__tests__/api.normalizeProduct.test.ts`
Expected: FAIL — `__test__normalizeProduct` is not exported.

- [ ] **Step 3: Implement**

In `api.ts`:

- `ProductDto` interface — add after `variants?: ProductVariantDto[];`:

  ```ts
    galleryImageUrls?: string[];
  ```

- `CreateProductRequest` and `UpdateProductRequest` interfaces — add:

  ```ts
    galleryImageUrls?: string[];
  ```

- `normalizeProduct` (line 907) — add to the returned object:

  ```ts
  function normalizeProduct(raw: any): ProductDto {
    return {
      ...raw,
      price: raw.price ?? { amount: raw.priceAmount, currency: raw.priceCurrency ?? 'CLP' },
      listPrice: raw.listPrice
        ?? (raw.listPriceAmount != null
          ? { amount: raw.listPriceAmount, currency: raw.listPriceCurrency ?? raw.priceCurrency ?? 'CLP' }
          : undefined),
      galleryImageUrls: Array.isArray(raw.galleryImageUrls) ? raw.galleryImageUrls : [],
    };
  }

  /** @internal test seam */
  export const __test__normalizeProduct = normalizeProduct;
  ```

  `toProductMutationBody` already spreads `...rest`, so `galleryImageUrls` on a request object is forwarded to the backend body with no change there.

- [ ] **Step 4: Run the test + typecheck**

Run: `cd frontend && npx vitest run src/lib/__tests__/api.normalizeProduct.test.ts && npx tsc --noEmit`
Expected: PASS, tsc clean.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/__tests__/api.normalizeProduct.test.ts
git commit -m "feat(product): galleryImageUrls on the frontend ProductDto + request types"
```

---

## Task 5: `ProductGalleryEditor.tsx` component

**Files:**
- Create: `frontend/src/islands/admin/ProductGalleryEditor.tsx`
- Test: `frontend/src/islands/admin/__tests__/ProductGalleryEditor.test.tsx` (create)

**Interfaces:**
- Consumes: `uploadMediaFile` from `../../lib/api`, `ImageDropzone` from `./ImageDropzone`.
- Produces: default export `ProductGalleryEditor` with props
  ```ts
  interface Props {
    readonly value: string[];
    readonly onChange: (next: string[]) => void;
    readonly coverUrl: string;
    readonly onCoverChange: (url: string) => void;
    readonly token: string;
  }
  ```
  Renders a thumbnail grid; each thumb has ↑ / ↓ / ✕ / "★ portada". An `ImageDropzone` "add" control appends; hidden once `value.length >= 9`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/islands/admin/__tests__/ProductGalleryEditor.test.tsx`:

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ProductGalleryEditor from '../ProductGalleryEditor';
import { uploadMediaFile } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({ uploadMediaFile: vi.fn() }));

function setup(value: string[], overrides: Partial<Parameters<typeof ProductGalleryEditor>[0]> = {}) {
  const onChange = vi.fn();
  const onCoverChange = vi.fn();
  render(
    <ProductGalleryEditor
      value={value}
      onChange={onChange}
      coverUrl="https://img/cover.jpg"
      onCoverChange={onCoverChange}
      token="t"
      {...overrides}
    />,
  );
  return { onChange, onCoverChange };
}

beforeEach(() => vi.mocked(uploadMediaFile).mockResolvedValue('https://img/new.jpg'));

describe('ProductGalleryEditor', () => {
  it('moves an image down', async () => {
    const user = userEvent.setup();
    const { onChange } = setup(['https://img/a.jpg', 'https://img/b.jpg']);
    await user.click(screen.getAllByRole('button', { name: /bajar/i })[0]);
    expect(onChange).toHaveBeenCalledWith(['https://img/b.jpg', 'https://img/a.jpg']);
  });

  it('removes an image', async () => {
    const user = userEvent.setup();
    const { onChange } = setup(['https://img/a.jpg', 'https://img/b.jpg']);
    await user.click(screen.getAllByRole('button', { name: /quitar/i })[0]);
    expect(onChange).toHaveBeenCalledWith(['https://img/b.jpg']);
  });

  it('swaps a thumbnail with the cover', async () => {
    const user = userEvent.setup();
    const { onChange, onCoverChange } = setup(['https://img/a.jpg', 'https://img/b.jpg']);
    await user.click(screen.getAllByRole('button', { name: /portada/i })[0]);
    expect(onCoverChange).toHaveBeenCalledWith('https://img/a.jpg');
    expect(onChange).toHaveBeenCalledWith(['https://img/cover.jpg', 'https://img/b.jpg']);
  });

  it('hides the add control at 9 images', () => {
    setup(Array.from({ length: 9 }, (_, i) => `https://img/${i}.jpg`));
    expect(screen.queryByText(/agregar foto/i)).not.toBeInTheDocument();
    expect(screen.getByText(/máximo 10 fotos/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/ProductGalleryEditor.test.tsx`
Expected: FAIL — module `../ProductGalleryEditor` does not exist.

- [ ] **Step 3: Implement the component**

Create `frontend/src/islands/admin/ProductGalleryEditor.tsx`:

```tsx
import { ArrowUp, ArrowDown, X, Star } from 'lucide-react';
import ImageDropzone from './ImageDropzone';

const MAX_GALLERY = 9;

interface Props {
  readonly value: string[];
  readonly onChange: (next: string[]) => void;
  readonly coverUrl: string;
  readonly onCoverChange: (url: string) => void;
  readonly token: string;
}

export default function ProductGalleryEditor({ value, onChange, coverUrl, onCoverChange, token }: Props) {
  function move(index: number, delta: number) {
    const next = [...value];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  }

  function remove(index: number) {
    onChange(value.filter((_, i) => i !== index));
  }

  function makeCover(index: number) {
    const promoted = value[index];
    const next = [...value];
    next[index] = coverUrl;
    onCoverChange(promoted);
    onChange(next);
  }

  return (
    <div className="space-y-2">
      <span className="text-xs text-pe-muted">Más fotos (galería)</span>
      {value.length > 0 && (
        <ul className="grid grid-cols-3 gap-2 sm:grid-cols-4">
          {value.map((url, i) => (
            <li key={`${url}-${i}`} className="relative rounded-xs border border-pe-border overflow-hidden">
              <img src={url} alt="" className="aspect-4/5 w-full object-cover" />
              <div className="absolute inset-x-0 bottom-0 flex justify-between bg-pe-surface/80 p-1">
                <button type="button" aria-label="Subir foto" onClick={() => move(i, -1)} disabled={i === 0}
                        className="disabled:opacity-30"><ArrowUp size={14} /></button>
                <button type="button" aria-label="Bajar foto" onClick={() => move(i, 1)} disabled={i === value.length - 1}
                        className="disabled:opacity-30"><ArrowDown size={14} /></button>
                <button type="button" aria-label="Hacer portada" onClick={() => makeCover(i)}><Star size={14} /></button>
                <button type="button" aria-label="Quitar foto" onClick={() => remove(i)}><X size={14} /></button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {value.length < MAX_GALLERY ? (
        <ImageDropzone
          key={value.length}
          folder="products"
          token={token}
          label="Agregar foto"
          allowClear={false}
          onUpload={(url) => onChange([...value, url])}
        />
      ) : (
        <p className="text-xs text-pe-muted">Máximo 10 fotos (portada + 9).</p>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/ProductGalleryEditor.test.tsx`
Expected: PASS (4 tests). If `ImageDropzone` requires props not passed here, check its `Props` — `value` is optional, `onUploadedFile` is optional, `customUpload` is optional; only `folder`, `token`, `onUpload` are required.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/islands/admin/ProductGalleryEditor.tsx \
        frontend/src/islands/admin/__tests__/ProductGalleryEditor.test.tsx
git commit -m "feat(admin): ProductGalleryEditor — ordered multi-image picker with cover swap"
```

---

## Task 6: Wire `ProductGalleryEditor` into `ProductForm`

**Files:**
- Modify: `frontend/src/islands/admin/ProductForm.tsx` (`EMPTY_FORM` ~line 187, the seed `useEffect` ~line 566 and ~line 632, `makeSnapshot` ~line 743, `handleSubmit` payload ~line 793, the render near the main `ImageDropzone` ~line 1220)
- Test: `frontend/src/islands/admin/__tests__/ProductForm.test.tsx`

**Interfaces:**
- Consumes: `ProductGalleryEditor` (Task 5); `CreateProductRequest.galleryImageUrls` (Task 4).
- Produces: the create/update payload carries `galleryImageUrls: string[]`; on edit the editor is seeded from `product.galleryImageUrls`.

- [ ] **Step 1: Write the failing test**

Add to `ProductForm.test.tsx` (mirror an existing create test — check the file for the render helper and the mocked `createProduct`):

```tsx
  it('submits galleryImageUrls with the create payload', async () => {
    const user = userEvent.setup();
    // render the form with the same setup the other create tests use
    // ... fill the minimum required fields the other tests fill ...
    // add a gallery image through the editor's dropzone (uploadMediaFile is mocked to return a url)
    const input = screen.getByLabelText(/agregar foto/i);
    await user.upload(input, new File(['x'], 'g.jpg', { type: 'image/jpeg' }));

    await user.click(screen.getByRole('button', { name: /guardar/i }));
    await waitFor(() =>
      expect(createProduct).toHaveBeenCalledWith(
        expect.objectContaining({ galleryImageUrls: ['https://img/uploaded.jpg'] }),
        expect.anything(),
      ),
    );
  });

  it('seeds the gallery editor from an existing product on edit', async () => {
    // render with product={ ...base, galleryImageUrls: ['https://img/a.jpg'] }
    expect(await screen.findByRole('img', { name: '' })).toBeInTheDocument(); // thumbnail present
    // assert an <img> with src https://img/a.jpg is rendered
  });
```

Adjust the assertions to the file's existing mock return value for `uploadMediaFile` (the file already mocks it — reuse that value rather than inventing `https://img/uploaded.jpg`).

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/ProductForm.test.tsx`
Expected: FAIL — no "Agregar foto" control; payload has no `galleryImageUrls`.

- [ ] **Step 3: Implement**

In `ProductForm.tsx`:

1. `EMPTY_FORM` (line 187) — add `galleryImageUrls: [] as string[],`.

2. Both `nextForm` / `snapshotForm` literals that copy from `product` (around lines 566 and 632) — add `galleryImageUrls: product.galleryImageUrls ?? [],`.

3. `makeSnapshot` (the returned `JSON.stringify` object, ~line 743) — add `galleryImageUrls: nextForm.galleryImageUrls,`.

4. `handleSubmit` payload (line 793) — add to the `CreateProductRequest` object:

   ```ts
       galleryImageUrls: form.galleryImageUrls,
   ```

5. Render — directly under the main `<ImageDropzone label="Imagen del producto" ... />` block (ends ~line 1233), add:

   ```tsx
       <ProductGalleryEditor
         value={form.galleryImageUrls}
         onChange={(next) => setForm((prev) => ({ ...prev, galleryImageUrls: next }))}
         coverUrl={form.imageUrl}
         onCoverChange={(url) => setForm((prev) => ({ ...prev, imageUrl: url }))}
         token={token ?? ''}
       />
   ```

   Add the import at the top: `import ProductGalleryEditor from './ProductGalleryEditor';`.

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/ProductForm.test.tsx`
Expected: PASS (existing + 2 new).

- [ ] **Step 5: Typecheck + full admin island test run**

Run: `cd frontend && npx tsc --noEmit && npx vitest run src/islands/admin`
Expected: clean, all pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/islands/admin/ProductForm.tsx frontend/src/islands/admin/__tests__/ProductForm.test.tsx
git commit -m "feat(admin): ProductForm carries the image gallery through create/update"
```

---

## Task 7: `seo.ts` — `image[]` from cover + gallery

**Files:**
- Modify: `frontend/src/lib/seo.ts:21-40`
- Test: `frontend/src/lib/__tests__/seo.test.ts`

**Interfaces:**
- Consumes: `ProductDto.galleryImageUrls` (Task 4).
- Produces: `productJsonLd(...).image` is `[cover, ...gallery]` absolute, deduped, blanks dropped.

- [ ] **Step 1: Write the failing test**

Add to `seo.test.ts`:

```ts
  it('puts the cover first then the gallery, deduped and absolute', () => {
    const ld = productJsonLd(
      { ...base, galleryImageUrls: ['/api/media/products/g1.jpg', '/api/media/products/vestido.jpg'] },
      { locale: 'es', canonicalUrl, requestUrl: req },
    );
    expect(ld.image).toEqual([
      'https://pilarestilo.com/api/media/products/vestido.jpg',
      'https://pilarestilo.com/api/media/products/g1.jpg',
    ]);
  });

  it('falls back to a single-element image when there is no gallery', () => {
    const ld = productJsonLd(base, { locale: 'es', canonicalUrl, requestUrl: req });
    expect(ld.image).toEqual(['https://pilarestilo.com/api/media/products/vestido.jpg']);
  });
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd frontend && npx vitest run src/lib/__tests__/seo.test.ts`
Expected: FAIL — the dedupe/gallery case returns only the cover.

- [ ] **Step 3: Implement**

In `seo.ts`, replace line 26 (`image: [absoluteUrl(product.imageUrl, opts.requestUrl, opts.headers)],`) with a computed list built just above the `jsonLd` object:

```ts
  const imageUrls = [product.imageUrl, ...(product.galleryImageUrls ?? [])]
    .map((u) => (u ?? '').trim())
    .filter(Boolean);
  const uniqueImages = [...new Set(imageUrls)].map((u) =>
    absoluteUrl(u, opts.requestUrl, opts.headers),
  );
```

and in the object literal:

```ts
    image: uniqueImages,
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd frontend && npx vitest run src/lib/__tests__/seo.test.ts`
Expected: PASS (existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/seo.ts frontend/src/lib/__tests__/seo.test.ts
git commit -m "feat(seo): Product JSON-LD image[] carries the full gallery"
```

---

## Task 8: `merchantFeed.ts` — `additional_image_link` per gallery image

**Files:**
- Modify: `frontend/src/lib/merchantFeed.ts:103-132`
- Test: `frontend/src/lib/__tests__/merchantFeed.test.ts`

**Interfaces:**
- Consumes: `ProductDto.galleryImageUrls` (Task 4).
- Produces: each `<item>` carries one `<g:additional_image_link>` per gallery URL, in order, after `<g:image_link>`; none when the gallery is empty.

- [ ] **Step 1: Write the failing test**

Add to `merchantFeed.test.ts`:

```ts
describe('merchantFeedItems — image gallery', () => {
  it('emits one additional_image_link per gallery image, in order', () => {
    const [item] = merchantFeedItems(
      { ...base, galleryImageUrls: ['/api/media/products/g1.jpg', 'https://cdn.example.com/g2.jpg'] },
      opts,
    );
    expect(item).toContain('<g:image_link>https://pilarestilo.com/api/media/products/vestido.jpg</g:image_link>');
    expect(item).toContain('<g:additional_image_link>https://pilarestilo.com/api/media/products/g1.jpg</g:additional_image_link>');
    expect(item).toContain('<g:additional_image_link>https://cdn.example.com/g2.jpg</g:additional_image_link>');
    const first = item.indexOf('/g1.jpg');
    const second = item.indexOf('/g2.jpg');
    expect(first).toBeLessThan(second);
  });

  it('emits no additional_image_link when there is no gallery', () => {
    const [item] = merchantFeedItems(base, opts);
    expect(item).not.toContain('<g:additional_image_link>');
  });
});
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd frontend && npx vitest run src/lib/__tests__/merchantFeed.test.ts`
Expected: FAIL — no `additional_image_link` emitted.

- [ ] **Step 3: Implement**

In `merchantFeed.ts` `renderItem` (line 103), just after `const image = absoluteUrl(...)` (line 105), add:

```ts
  const additionalImages = (product.galleryImageUrls ?? [])
    .slice(0, 10)
    .map((u) => absoluteUrl(u, opts.siteUrl, opts.headers));
```

In the `parts` array, immediately after the `<g:image_link>` line (line 120), add:

```ts
    ...additionalImages.map((u) => `<g:additional_image_link>${xmlEscape(u)}</g:additional_image_link>`),
```

(`parts` is `string[]` with `.filter(Boolean)` at the end, so spreading a mapped array in is fine.)

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd frontend && npx vitest run src/lib/__tests__/merchantFeed.test.ts`
Expected: PASS (existing + 2 new).

- [ ] **Step 5: Full frontend + backend regression**

Run: `cd frontend && npx vitest run && npx tsc --noEmit`
Run: `cd backend && mvn test`
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/merchantFeed.ts frontend/src/lib/__tests__/merchantFeed.test.ts
git commit -m "feat(feed): Merchant feed emits additional_image_link for the product gallery"
```

---

## Self-Review

**Spec coverage:**

| Spec section | Task |
|---|---|
| §1 Migration `V102__product_images.sql` | Task 2 |
| §2 `ProductEntity` `@ElementCollection @OrderColumn` | Task 2 |
| §3 `Product` domain field + normalization (trim/blank/dedupe/cap-9, null→empty, getter copy) | Task 1 |
| §4 Use cases — trailing param on fullest overload, `List.of()` on delegate, `setGalleryImageUrls` after create/update | Task 3 |
| §5 Repository adapter `toEntity`/`toDomain` mapping, mutable list | Task 2 |
| §6 `CreateProductRequest`/`UpdateProductRequest`/`ProductDto`/`ProductMapper`/`ProductController` | Task 3 |
| §7 `api.ts` types + fixtures | Task 4 (fixtures unaffected — field is optional, normalizer defaults to `[]`) |
| §8 `ProductGalleryEditor.tsx` + `ProductForm.tsx` (↑/↓, ✕, ★ portada, add, cap-9 helper text) | Tasks 5, 6 |
| §9 `seo.ts` `image[]`; `merchantFeed.ts` `additional_image_link` | Tasks 7, 8 |
| §Testing — ProductTest, ProductRepositoryAdapterIT, ProductControllerIT, seo.test, merchantFeed.test, editor + form vitest | Tasks 1, 2, 3, 5, 6, 7, 8 |

No gaps. §7's "fixtures gain `galleryImageUrls: []`" is satisfied differently than the spec worded it: the frontend field is optional and `normalizeProduct` guarantees an array, so the 6 `FIXTURE_PRODUCTS` entries do not each need the key. This is a deliberate, smaller change — noted here so a reviewer does not flag it as a miss.

**Placeholder scan:** Task 6 Step 1's test body says "fill the minimum required fields the other tests fill" and "reuse that value" for the upload mock — this defers to the existing test file's helpers rather than reproducing them blind. Every other step has literal code. Acceptable: `ProductForm.test.tsx` already has a working create-flow test to copy, and inventing a second copy of its ~40-line setup here would more likely drift from the real file than help.

**Type consistency:**
- `Product.setGalleryImageUrls(List<String>)` / `getGalleryImageUrls(): List<String>` — Task 1, used identically in Tasks 2 and 3.
- `ProductEntity.getGalleryImageUrls(): List<String>` / `setGalleryImageUrls(List<String>)` — Task 2, used in Task 2's adapter mapping only.
- `ProductDto` trailing component `List<String> galleryImageUrls` — Task 3; frontend `ProductDto.galleryImageUrls?: string[]` — Task 4; consumed as `product.galleryImageUrls ?? []` in Tasks 5–8.
- `CreateProductRequest.galleryImageUrls` — backend `List<String>` (Task 3), frontend `string[]` (Task 4).
- `ProductGalleryEditor` prop names `value` / `onChange` / `coverUrl` / `onCoverChange` / `token` — Task 5, used verbatim in Task 6.
- Use-case fullest-overload trailing param `List<String> galleryImageUrls` — Task 3, both Create and Update, delegate passes `List.of()`.

Consistent throughout.
