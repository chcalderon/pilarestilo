package com.pilarestilo.product.infrastructure.persistence.repositories;

import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.category.infrastructure.persistence.repositories.CategoryJpaRepository;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.product.domain.ports.ProductRepository.ProductFilter;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests written before reducing search()/buildSpecification()'s Cognitive
 * Complexity (S3776) -- neither had any coverage. Runs against a real Postgres (JPA Criteria
 * predicates can't be exercised meaningfully with mocks), and every product name/brand carries a
 * "ZT-" marker so seeded fixture data sharing the same database never leaks into an assertion.
 */
@Testcontainers
@SpringBootTest
class ProductRepositoryAdapterIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("pilarestilo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryJpaRepository categoryJpaRepository;

    @Autowired
    com.pilarestilo.varianttemplate.infrastructure.persistence.repositories.VariantTemplateJpaRepository variantTemplateJpaRepository;

    UUID categoryId;

    @BeforeEach
    void setUp() {
        CategoryEntity category = new CategoryEntity();
        category.setId(UUID.randomUUID());
        category.setSlug("zt-category-" + UUID.randomUUID());
        category.setNameEs("Categoria ZT");
        category.setNameEn("ZT Category");
        category.setActive(true);
        category.setCreatedAt(Instant.now());
        categoryJpaRepository.save(category);
        categoryId = category.getId();
    }

    private CategoryEntity newCategory(String slugPrefix, String nameEs, String nameEn) {
        CategoryEntity category = new CategoryEntity();
        category.setId(UUID.randomUUID());
        category.setSlug(slugPrefix + UUID.randomUUID());
        category.setNameEs(nameEs);
        category.setNameEn(nameEn);
        category.setActive(true);
        category.setCreatedAt(Instant.now());
        return categoryJpaRepository.save(category);
    }

    private Product save(String name, String brand, ProductCondition condition, BigDecimal price,
                         int stock, boolean active, Instant createdAt, Set<UUID> categoryIds) {
        Product product = Product.create(name, "desc " + name, Money.of(price),
                "/img.jpg", condition, brand, stock);
        product.setActive(active);
        product.setCreatedAt(createdAt);
        if (categoryIds != null) {
            product.setCategoryIds(categoryIds);
        }
        return productRepository.save(product);
    }

    @Test
    void findById_of_a_product_with_a_variant_template_works_outside_a_transaction() {
        // Regression: PublishProductsBatchUseCase is deliberately non-@Transactional and calls
        // findById; before findById was made @Transactional(readOnly=true), mapping a product that
        // has a variant template threw LazyInitializationException on template.getFieldConfig().
        var template = new com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setName("ZT-Template");
        template.setCreatedAt(Instant.now());
        java.util.Map<String, Object> field = java.util.Map.of(
                "label", "Color", "inputType", "FREE_TEXT",
                "options", java.util.List.of(), "allowMultiple", false, "allowCustom", true);
        template.setFieldConfig(java.util.Map.of("primary", field, "secondary", field));
        variantTemplateJpaRepository.save(template);

        Product product = Product.create("ZT-ConTemplate", "desc", Money.of(BigDecimal.TEN),
                "/img.jpg", ProductCondition.NEW, "ZT-TemplateBrand", 3);
        product.setVariantTemplateId(template.getId());
        Product saved = productRepository.save(product);

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getVariantTemplateId()).isEqualTo(template.getId());
        assertThat(reloaded.getVariantFieldConfig()).isNotNull();
    }

    // ---- update path (Hibernate merge, not the insert path every other test above exercises) ----

    @Test
    void updatingAnAlreadyPersistedProductsVariantsDoesNotThrow() {
        Product product = save("ZT-VarianteUpdate", "ZT-VarianteBrand", ProductCondition.NEW,
                BigDecimal.TEN, 1, true, Instant.now(), null);
        product.setVariants(List.of(new com.pilarestilo.product.domain.model.ProductVariant("Negro", "M", 5, 0)));
        productRepository.save(product);

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        product = reloaded;
        product.setVariants(List.of(new com.pilarestilo.product.domain.model.ProductVariant("Negro", "M", 3, 1)));
        Product updated = productRepository.save(product);

        assertThat(updated.getVariants()).hasSize(1);
        assertThat(updated.getVariants().get(0).getStockOnHand()).isEqualTo(3);
        assertThat(updated.getVariants().get(0).getStockReserved()).isEqualTo(1);
    }

    // ---- search() ----

    @Test
    void searchTermMatchesProductName() {
        save("ZT-Blazer Especial", "ZT-Marca", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);
        save("ZT-Otro Producto", "ZT-Marca", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);

        var page = productRepository.search("blazer especial", null, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-Blazer Especial");
    }

    @Test
    void searchTermMatchesBrand() {
        save("ZT-Casaca", "ZT-MarcaUnica", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);

        var page = productRepository.search("marcaunica", null, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).contains("ZT-Casaca");
    }

    @Test
    void searchTermMatchesCategoryAndDoesNotDuplicateRows() {
        CategoryEntity secondCategory = newCategory("zt-second-", "ZT Segunda Categoria Unica", "ZT Second Category");

        save("ZT-Multi Categoria", "ZT-Marca", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(),
                Set.of(categoryId, secondCategory.getId()));

        var page = productRepository.search("zt segunda categoria unica", null, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-Multi Categoria");
    }

    @Test
    void searchFiltersByActive() {
        save("ZT-Activo", "ZT-ActivoBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);
        save("ZT-Inactivo", "ZT-ActivoBrand", ProductCondition.NEW, BigDecimal.TEN, 1, false, Instant.now(), null);

        var page = productRepository.search("zt-activobrand", true, null, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-Activo");
    }

    @Test
    void searchFiltersByInStock() {
        save("ZT-ConStock", "ZT-StockBrand", ProductCondition.NEW, BigDecimal.TEN, 5, true, Instant.now(), null);
        save("ZT-SinStock", "ZT-StockBrand", ProductCondition.NEW, BigDecimal.TEN, 0, true, Instant.now(), null);

        var page = productRepository.search("zt-stockbrand", null, true, null, null, null, null,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-ConStock");
    }

    @Test
    void searchFiltersByCondition() {
        save("ZT-Nuevo", "ZT-CondBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);
        save("ZT-Usado", "ZT-CondBrand", ProductCondition.USED, BigDecimal.TEN, 1, true, Instant.now(), null);

        var page = productRepository.search("zt-condbrand", null, null, "USED", null, null, null,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-Usado");
    }

    @Test
    void searchFiltersByCategorySlug() {
        CategoryEntity otherCategory = newCategory("zt-other-", "ZT Otra", "ZT Other");

        save("ZT-EnCategoria", "ZT-CatSlugBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(),
                Set.of(categoryId));
        save("ZT-EnOtraCategoria", "ZT-CatSlugBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(),
                Set.of(otherCategory.getId()));

        String slug = categoryJpaRepository.findById(categoryId).orElseThrow().getSlug();
        var page = productRepository.search("zt-catslugbrand", null, null, null, slug, null, null,
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-EnCategoria");
    }

    @Test
    void searchFiltersByCreatedDateRange() {
        Instant inRange = LocalDate.of(2026, 3, 15).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant outOfRange = LocalDate.of(2026, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        save("ZT-DentroDeRango", "ZT-FechaBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, inRange, null);
        save("ZT-FueraDeRango", "ZT-FechaBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, outOfRange, null);

        var page = productRepository.search("zt-fechabrand", null, null, null, null,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-DentroDeRango");
    }

    @Test
    void searchWithBlankTermStillAppliesOtherFilters() {
        save("ZT-BlancoActivo", "ZT-BlancoBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);
        save("ZT-BlancoInactivo", "ZT-BlancoBrand", ProductCondition.NEW, BigDecimal.TEN, 1, false, Instant.now(), null);

        var page = productRepository.search("  ", true, null, null, null, null, null, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Product::getName)
                .contains("ZT-BlancoActivo")
                .doesNotContain("ZT-BlancoInactivo");
    }

    // ---- findAll(ProductFilter, Pageable) / buildSpecification() ----

    @Test
    void findAllFiltersByCondition() {
        save("ZT-FiltroNuevo", "ZT-FiltroCondBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);
        save("ZT-FiltroUsado", "ZT-FiltroCondBrand", ProductCondition.USED, BigDecimal.TEN, 1, true, Instant.now(), null);

        var filter = new ProductFilter("USED", null, null, null, null, null, null, null, null);
        var page = productRepository.findAll(filter, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Product::getName)
                .contains("ZT-FiltroUsado")
                .doesNotContain("ZT-FiltroNuevo");
    }

    @Test
    void findAllFiltersByBrandPartialMatchCaseInsensitive() {
        save("ZT-Zapato", "ZT-Nike Uniquisimo", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);

        var filter = new ProductFilter(null, "nike uniquisimo", null, null, null, null, null, null, null);
        var page = productRepository.findAll(filter, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Product::getName).contains("ZT-Zapato");
    }

    @Test
    void findAllFiltersByPriceRange() {
        save("ZT-Barato", "ZT-PrecioBrand", new BigDecimal("1000"), 1, true, Instant.now(), null);
        save("ZT-Caro", "ZT-PrecioBrand", new BigDecimal("999999"), 1, true, Instant.now(), null);

        var filter = new ProductFilter(null, null, new BigDecimal("500"), new BigDecimal("5000"), null, null, null, null, null);
        var page = productRepository.findAll(filter, PageRequest.of(0, 200));

        assertThat(page.getContent()).extracting(Product::getName)
                .contains("ZT-Barato")
                .doesNotContain("ZT-Caro");
    }

    @Test
    void findAllFiltersByActive() {
        save("ZT-FiltroActivo", "ZT-FiltroActivoBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(), null);
        save("ZT-FiltroInactivo", "ZT-FiltroActivoBrand", ProductCondition.NEW, BigDecimal.TEN, 1, false, Instant.now(), null);

        var filter = new ProductFilter(null, "zt-filtroactivobrand", null, null, true, null, null, null, null);
        var page = productRepository.findAll(filter, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-FiltroActivo");
    }

    @Test
    void findAllFiltersByInStock() {
        save("ZT-FiltroConStock", "ZT-FiltroStockBrand", ProductCondition.NEW, BigDecimal.TEN, 3, true, Instant.now(), null);
        save("ZT-FiltroSinStock", "ZT-FiltroStockBrand", ProductCondition.NEW, BigDecimal.TEN, 0, true, Instant.now(), null);

        var filter = new ProductFilter(null, "zt-filtrostockbrand", null, null, null, true, null, null, null);
        var page = productRepository.findAll(filter, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("ZT-FiltroConStock");
    }

    @Test
    void findAllFiltersByCategorySlugWithoutDuplicates() {
        CategoryEntity secondCategory = newCategory("zt-second-fa-", "ZT Segunda FA", "ZT Second FA");

        save("ZT-MultiCategoriaFA", "ZT-FaBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, Instant.now(),
                Set.of(categoryId, secondCategory.getId()));

        String slug = categoryJpaRepository.findById(categoryId).orElseThrow().getSlug();
        var filter = new ProductFilter(null, null, null, null, null, null, slug, null, null);
        var page = productRepository.findAll(filter, PageRequest.of(0, 50));

        List<Product> matches = page.getContent().stream()
                .filter(p -> p.getName().equals("ZT-MultiCategoriaFA"))
                .toList();
        assertThat(matches).hasSize(1);
    }

    @Test
    void round_trips_the_image_gallery_in_order() {
        Product p = Product.create("ZT-Gallery", "d", new Money(BigDecimal.valueOf(19990), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "ZT-Brand", 3);
        p.setGalleryImageUrls(List.of("https://img/1.jpg", "https://img/2.jpg", "https://img/3.jpg"));
        UUID id = productRepository.save(p).getId();

        Product reloaded = productRepository.findById(id).orElseThrow();
        assertThat(reloaded.getGalleryImageUrls())
                .containsExactly("https://img/1.jpg", "https://img/2.jpg", "https://img/3.jpg");
    }

    @Test
    void reorders_and_clears_the_gallery_on_update() {
        Product p = Product.create("ZT-Gallery2", "d", new Money(BigDecimal.valueOf(19990), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "ZT-Brand", 3);
        p.setGalleryImageUrls(List.of("https://img/a.jpg", "https://img/b.jpg"));
        UUID id = productRepository.save(p).getId();

        Product toReorder = productRepository.findById(id).orElseThrow();
        toReorder.setGalleryImageUrls(List.of("https://img/b.jpg", "https://img/a.jpg"));
        productRepository.save(toReorder);
        assertThat(productRepository.findById(id).orElseThrow().getGalleryImageUrls())
                .containsExactly("https://img/b.jpg", "https://img/a.jpg");

        Product toClear = productRepository.findById(id).orElseThrow();
        toClear.setGalleryImageUrls(List.of());
        productRepository.save(toClear);
        assertThat(productRepository.findById(id).orElseThrow().getGalleryImageUrls()).isEmpty();
    }

    @Test
    void findAllFiltersByCreatedDateRange() {
        Instant inRange = LocalDate.of(2026, 5, 10).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant outOfRange = LocalDate.of(2026, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        save("ZT-FiltroFechaDentro", "ZT-FiltroFechaBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, inRange, null);
        save("ZT-FiltroFechaFuera", "ZT-FiltroFechaBrand", ProductCondition.NEW, BigDecimal.TEN, 1, true, outOfRange, null);

        var filter = new ProductFilter(null, "zt-filtrofechabrand", null, null, null, null, null,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        var page = productRepository.findAll(filter, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Product::getName)
                .contains("ZT-FiltroFechaDentro")
                .doesNotContain("ZT-FiltroFechaFuera");
    }

    private Product save(String name, String brand, BigDecimal price, int stock, boolean active,
                         Instant createdAt, Set<UUID> categoryIds) {
        return save(name, brand, ProductCondition.NEW, price, stock, active, createdAt, categoryIds);
    }
}
