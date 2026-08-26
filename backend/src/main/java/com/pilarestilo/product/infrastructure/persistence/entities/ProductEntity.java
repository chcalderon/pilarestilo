package com.pilarestilo.product.infrastructure.persistence.entities;

import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.enums.ShippingOriginZone;
import com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    private UUID id;

    @Version
    private Long version;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductCondition condition;

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
    private java.math.BigDecimal avgRating = java.math.BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Column(name = "shipping_origin_zone", nullable = false, length = 16)
    @Convert(converter = ShippingOriginZoneAttributeConverter.class)
    private ShippingOriginZone shippingOriginZone = ShippingOriginZone.LOCAL;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "product_size_stocks",
            joinColumns = @JoinColumn(name = "product_id")
    )
    private List<ProductSizeStockEmbeddable> sizeStocks = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "product_variants",
            joinColumns = @JoinColumn(name = "product_id")
    )
    private List<ProductVariantEmbeddable> variants = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private java.util.Set<CategoryEntity> categories = new java.util.HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_template_id")
    private VariantTemplateEntity variantTemplate;

    // Getters and setters
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public ShippingOriginZone getShippingOriginZone() { return shippingOriginZone; }

    public void setShippingOriginZone(ShippingOriginZone shippingOriginZone) {
        this.shippingOriginZone = shippingOriginZone;
    }

    public List<ProductSizeStockEmbeddable> getSizeStocks() { return sizeStocks; }
    public void setSizeStocks(List<ProductSizeStockEmbeddable> sizeStocks) { this.sizeStocks = sizeStocks; }
    public List<ProductVariantEmbeddable> getVariants() { return variants; }
    public void setVariants(List<ProductVariantEmbeddable> variants) { this.variants = variants; }

    // Existing getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPriceAmount() { return priceAmount; }
    public void setPriceAmount(BigDecimal priceAmount) { this.priceAmount = priceAmount; }

    public String getPriceCurrency() { return priceCurrency; }
    public void setPriceCurrency(String priceCurrency) { this.priceCurrency = priceCurrency; }

    public BigDecimal getListPriceAmount() { return listPriceAmount; }
    public void setListPriceAmount(BigDecimal listPriceAmount) { this.listPriceAmount = listPriceAmount; }

    public String getListPriceCurrency() { return listPriceCurrency; }
    public void setListPriceCurrency(String listPriceCurrency) { this.listPriceCurrency = listPriceCurrency; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public ProductCondition getCondition() { return condition; }
    public void setCondition(ProductCondition condition) { this.condition = condition; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public BigDecimal getAvgRating() { return avgRating; }
    public void setAvgRating(BigDecimal avgRating) { this.avgRating = avgRating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public java.util.Set<CategoryEntity> getCategories() { return categories; }
    public void setCategories(java.util.Set<CategoryEntity> categories) { this.categories = categories; }

    public VariantTemplateEntity getVariantTemplate() { return variantTemplate; }
    public void setVariantTemplate(VariantTemplateEntity variantTemplate) { this.variantTemplate = variantTemplate; }
}
