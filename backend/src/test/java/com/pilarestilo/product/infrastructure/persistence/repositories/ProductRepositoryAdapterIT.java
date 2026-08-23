package com.pilarestilo.product.infrastructure.persistence.repositories;

import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.category.infrastructure.persistence.repositories.CategoryJpaRepository;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.product.domain.ports.ProductRepository.ProductFilter;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.support.NotificationsTestDatabase;
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
        NotificationsTestDatabase.register(registry, postgres);
    }

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryJpaRepository categoryJpaRepository;

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
