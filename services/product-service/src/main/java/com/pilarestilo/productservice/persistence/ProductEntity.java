package com.pilarestilo.productservice.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "price_currency", nullable = false, length = 10)
    private String priceCurrency;

    @Column(name = "list_price_amount", precision = 15, scale = 2)
    private BigDecimal listPriceAmount;

    @Column(name = "list_price_currency", length = 10)
    private String listPriceCurrency;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 10)
    private String condition;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Column(name = "shipping_origin_zone", nullable = false, length = 16)
    private String shippingOriginZone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_size_stocks", joinColumns = @JoinColumn(name = "product_id"))
    private List<ProductSizeStockEmbeddable> sizeStocks = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_variants", joinColumns = @JoinColumn(name = "product_id"))
    private List<ProductVariantEmbeddable> variants = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<CategoryEntity> categories = new HashSet<>();

    /*
     * EAGER, matching categories above: ProductMapper.toDto runs in ProductController, after
     * queryService's @Transactional method has already returned and closed the session, so any
     * lazy relationship read there throws LazyInitializationException on a real request even
     * though entity-only unit tests (no Spring context) never touch a session and can't catch it.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "variant_template_id")
    private VariantTemplateEntity variantTemplate;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public BigDecimal getListPriceAmount() {
        return listPriceAmount;
    }

    public String getListPriceCurrency() {
        return listPriceCurrency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCondition() {
        return condition;
    }

    public String getBrand() {
        return brand;
    }

    public int getStock() {
        return stock;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public String getShippingOriginZone() {
        return shippingOriginZone;
    }

    public List<ProductSizeStockEmbeddable> getSizeStocks() {
        return sizeStocks;
    }

    public List<ProductVariantEmbeddable> getVariants() {
        return variants;
    }

    public Set<CategoryEntity> getCategories() {
        return categories;
    }

    public VariantTemplateEntity getVariantTemplate() {
        return variantTemplate;
    }
}
