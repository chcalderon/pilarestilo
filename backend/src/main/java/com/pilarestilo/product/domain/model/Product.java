package com.pilarestilo.product.domain.model;

import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.enums.ShippingOriginZone;
import com.pilarestilo.product.domain.valueobjects.Brand;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;

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
    /**
     * The winning shape category's field config (or the generic fallback), resolved on every
     * read by the repository adapter -- see CategoryVariantFieldValidator for the write-time
     * validation this same config drives.
     */
    private CategoryVariantFieldConfig variantFieldConfig;

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

    public CategoryVariantFieldConfig getVariantFieldConfig() { return variantFieldConfig; }
    public void setVariantFieldConfig(CategoryVariantFieldConfig variantFieldConfig) {
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
