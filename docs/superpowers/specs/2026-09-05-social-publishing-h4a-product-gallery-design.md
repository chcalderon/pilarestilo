# Increment H, Etapa H-4a — Product image gallery + SEO wiring

**Status:** design approved in chat 2026-09-05, pending written-spec review.

**Parent:** Increment H (social publishing). H-4 was split during brainstorming into two
sub-projects built in order:

- **H-4a (this spec)** — a real ordered image gallery on `Product`, an admin editor for it, and
  the SEO consumers (`Product` JSON-LD `image[]`, Merchant feed `additional_image_link`).
- **H-4b (next spec)** — carousel (multi-image) IG/FB posts, which consume the gallery H-4a adds.

Reels stays a separate future increment. H-5 (campaign reporting) follows H-4b.

## Why

Products carry exactly one image (`products.image_url`). The shop wants several photos per product.
Google benefits concretely from more images even with no visible storefront gallery:

- `schema.org/Product.image` currently emits one URL (`frontend/src/lib/seo.ts:26`). Google
  recommends multiple representative images for rich product results and free Shopping listings.
- The Merchant feed emits one `<g:image_link>` and no `<g:additional_image_link>`
  (`frontend/src/lib/merchantFeed.ts:120`). Google Shopping shows up to 10 additional images.

A visible storefront gallery/carousel (which is what would help Google **Images** indexing and
conversion) was explicitly deferred — option A in the brainstorm. This spec is model + admin +
structured-data/feed wiring only.

## Global constraints

- Backend: Spring Boot 4 (Java 25), hexagonal. Domain objects carry no framework annotations.
  One use-case class per action. Flyway only, never edit an applied migration. Current highest
  migration **V101** → this adds **V102**.
- Jackson 3 (`tools.jackson.*`). Tests: Testcontainers + MockMvc ITs, JUnit 5 + Mockito.
- Frontend: Astro 5 SSR + React islands, Zustand, Tailwind `pe-*` semantic tokens. Vitest + RTL.
- `products.image_url` is unchanged in meaning and value. Every existing reader (storefront cards,
  `hasSellableStock` in `frontend/src/lib/api.ts`, the AI pipeline, seeds, the Merchant feed's
  primary image) keeps working untouched.
- `notification-service` maps read-only views of `orders`, `order_items`, `users`, `payments`,
  `sales_documents`, `return_requests`, `system_settings` — **not** products. A new
  `product_images` table needs no `*RoEntity` change. `ReadOnlyMappingIT` still runs V102 as part
  of the monolith's full migration set, so V102 must be valid on its own.
- Gallery cap: **9 additional images** (cover + 9 = 10, the Instagram carousel limit and within
  Google's 10-additional-image feed limit).
- Caveman mode is active for chat only; code, comments, commits, spec prose stay normal.

## Architecture

`products.image_url` stays the cover. A new ordered collection `product_images` holds the
**additional** images. Nothing derives the cover from the collection and nothing syncs them; the
publication carousel (H-4b) will read `[cover] ++ galleryImageUrls`.

Storage follows the existing `product_variants` / `product_size_stocks` pattern: a JPA
`@ElementCollection` over a collection table, full-replace on save. It uses `@OrderColumn` so the
list is an indexed list (not a bag) — this both preserves order and sidesteps any
`MultipleBagFetchException` concern with the two existing eager bag collections.

```
Create/UpdateProductRequest.galleryImageUrls: List<String>   (HTTP in, optional)
  -> ProductController
  -> Create/UpdateProductUseCase       reads request, calls product.setGalleryImageUrls(...)
  -> Product (domain)                  normalizes: trim, drop blank, dedupe, cap 9, keep order
  -> ProductRepositoryAdapter          toEntity/toDomain map the ordered list
  -> product_images collection table   (product_id, image_url, sort_order)

ProductDto.galleryImageUrls: List<String>   (HTTP out, always present, [] when empty)
  -> ProductMapper (single construction site)
  -> frontend ProductDto
  -> seo.ts productJsonLd        image: [cover, ...gallery]  (absolute, deduped, no blanks)
  -> merchantFeed.ts renderItem  <g:image_link>=cover  +  one <g:additional_image_link> per gallery URL
```

## Component detail

### 1. Migration — `V102__product_images.sql`

```sql
CREATE TABLE product_images (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url  TEXT NOT NULL,
    sort_order INT  NOT NULL,
    PRIMARY KEY (product_id, sort_order)
);
CREATE INDEX idx_product_images_product ON product_images (product_id);
```

No backfill — existing products start with an empty gallery. `image_url` on `products` is not
touched.

### 2. `ProductEntity`

```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
@OrderColumn(name = "sort_order")
@Column(name = "image_url", nullable = false)
private List<String> galleryImageUrls = new ArrayList<>();
```

Getter/setter mirroring `variants` / `sizeStocks`. EAGER for consistency with the sibling
collections; the catalog is small, so the extra select per product read is negligible. (LAZY is a
possible later perf lever — safe only because every `toDomain` reads the list inside
`ProductRepositoryAdapter`'s `@Transactional(readOnly = true)` `findById`/`findAll`.)

### 3. `Product` (domain)

- New field `private List<String> galleryImageUrls = new ArrayList<>();`
- `public List<String> getGalleryImageUrls()` — returns a copy (`new ArrayList<>(...)`).
- `public void setGalleryImageUrls(List<String> urls)` — normalizes:
  1. null → empty
  2. `.map(String::trim).filter(s -> !s.isEmpty())`
  3. dedupe, preserving first-seen order (`LinkedHashSet`)
  4. if size > 9, keep the first 9
  - stores the result as a new `ArrayList`.
- `create()` / `update()` signatures are **unchanged**. The use case calls `setGalleryImageUrls`
  after constructing/updating, same as it already handles `categoryIds` separately.

### 4. Use cases

- `CreateProductUseCase.execute(...)` and `UpdateProductUseCase.execute(...)` gain a
  `List<String> galleryImageUrls` parameter on the **fullest** overload only; the shorter
  delegating overloads pass `List.of()`. After the existing `Product.create` / `product.update`
  call, add `product.setGalleryImageUrls(galleryImageUrls)`.
- Both are already `@Transactional`; the collection persists with the aggregate on `save`.

### 5. Repository adapter

`ProductRepositoryAdapter`:
- `toEntity` / `toDomain` copy `galleryImageUrls` both ways. Order is the `sort_order` column,
  which JPA maintains from list position via `@OrderColumn`.
- On update, the existing full-replace behavior (Hibernate deletes and re-inserts the collection
  rows) applies — same as `variants`.

### 6. API layer

- `CreateProductRequest` / `UpdateProductRequest` records gain `List<String> galleryImageUrls`
  (no bean-validation annotation — the domain normalizes). A client omitting the field sends
  `null`, which the domain treats as empty.
- `ProductDto` record gains `List<String> galleryImageUrls` as the **last** component. It is
  always present in responses: `[]` when empty. `ProductMapper` (the single `new ProductDto(`
  site) maps `product.getGalleryImageUrls()`.
- `ProductController` passes `request.galleryImageUrls()` into the use-case calls.

### 7. Frontend — `api.ts`

- `interface ProductDto` gains `galleryImageUrls: string[];`
- `CreateProductRequest` / `UpdateProductRequest` request types gain `galleryImageUrls?: string[];`
- Any hard-coded product fixtures/mocks in `api.ts` gain `galleryImageUrls: []`.

### 8. Frontend — `ProductGalleryEditor.tsx` (new) + `ProductForm.tsx`

New component, rendered as a "Más fotos" section directly under the existing "Imagen del producto"
`ImageDropzone` in `ProductForm`:

- Props: `value: string[]`, `onChange: (next: string[]) => void`, `coverUrl: string`,
  `onCoverChange: (url: string) => void`, `token: string`.
- Thumbnail grid of `value`. Each thumbnail has:
  - **↑ / ↓** buttons — move within the list (disabled at the ends). No drag-and-drop
    (`admin-overlays-need-a-portal` lesson: dnd is fragile here).
  - **✕** — remove from the list.
  - **★ portada** — swap this URL with `coverUrl`: calls `onCoverChange(thisUrl)` and
    `onChange` with the old cover put back at this index.
- An "+ Agregar foto" control at the end: an `ImageDropzone` in add mode (no `value`), whose
  `onUpload` appends the returned URL. It uploads through `uploadMediaFile(file, 'products', token)`
  and reuses `ImageDropzone`'s client-side compression.
- When `value.length >= 9`: hide the add control, show the helper text
  "Máximo 10 fotos (portada + 9)".

`ProductForm`:
- `form.galleryImageUrls: string[]` in form state; seeded from `product.galleryImageUrls ?? []`
  on edit, `[]` on create.
- The create/update payload includes `galleryImageUrls: form.galleryImageUrls`.
- `★ portada` wires `onCoverChange` to `setForm(p => ({ ...p, imageUrl: url }))`.

### 9. Frontend — SEO consumers

- `seo.ts` `productJsonLd`:
  ```ts
  const imageUrls = [product.imageUrl, ...(product.galleryImageUrls ?? [])]
    .map(u => (u ?? '').trim())
    .filter(Boolean);
  // dedupe, then map through absoluteUrl(...)
  image: [...new Set(imageUrls)].map(u => absoluteUrl(u, opts.requestUrl, opts.headers)),
  ```
- `merchantFeed.ts` `renderItem`: after the `<g:image_link>` line, add one
  `<g:additional_image_link>` element per `product.galleryImageUrls` entry (absolute URL,
  `xmlEscape`d), order preserved. No gallery → no extra elements. (`galleryImageUrls` is already
  ≤ 9, within Google's 10-additional limit; no extra clamp needed but a `.slice(0, 10)` guard is
  cheap.)

## Error handling

- Domain normalization never throws — it silently trims to a valid list (blank-drop, dedupe,
  cap 9). A caller sending 20 URLs gets the first 9 unique non-blank ones.
- Upload failures in `ProductGalleryEditor` surface through `ImageDropzone`'s existing error
  state; the list is unchanged on a failed upload.
- A gallery URL that later 404s is not validated here (same as `image_url` today) — it just
  renders broken. Out of scope.
- `★ portada` when the gallery is at 9 and the old cover would make 10: fine, it is a swap, the
  count is unchanged.

## Testing

**Backend**
- `ProductTest` — `setGalleryImageUrls` trims, drops blanks, dedupes preserving order, caps at 9;
  null → empty.
- `ProductRepositoryAdapterIT` — round-trips a gallery; reorder persists; setting `[]` clears the
  rows; `ON DELETE CASCADE` removes rows with the product.
- `ProductControllerIT` — `POST /api/products` and `PUT /api/products/{id}` with
  `galleryImageUrls`; response echoes the normalized list; a request omitting the field yields
  `galleryImageUrls: []`.

**Frontend**
- `seo.test.ts` — `image` is `[cover, ...gallery]` absolute; a gallery entry equal to the cover
  is deduped; empty gallery → single-element `image`.
- `merchantFeed.test.ts` — N `additional_image_link` elements in order for a product with a
  gallery; none for a product without.
- `ProductGalleryEditor` (new test file) — add appends, ↑/↓ reorder, ✕ removes, ★ swaps with
  cover, add control hidden at 9.
- `ProductForm` — existing tests still pass; the create/update payload carries
  `galleryImageUrls`.

## Out of scope (H-4a)

- Visible storefront image gallery / carousel on `/es/products/{id}` — deferred (option A).
- Carousel posting to Instagram / Facebook — that is H-4b.
- The AI image pipeline writing into the gallery.
- Per-image alt text, captions, or variant-specific images.
- Any change to `products.image_url` semantics or to how the cover is chosen on create.

## Build order

1. V102 migration + `ProductEntity` collection.
2. Domain `Product` normalization + test.
3. Repository adapter mapping + IT.
4. API records + `ProductDto` + `ProductMapper` + controller + controller IT.
5. `api.ts` types.
6. `ProductGalleryEditor` + `ProductForm` wiring + tests.
7. `seo.ts` + `merchantFeed.ts` + their tests.
