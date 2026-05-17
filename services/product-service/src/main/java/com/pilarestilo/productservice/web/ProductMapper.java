package com.pilarestilo.productservice.web;

import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.web.dto.ProductDto;

import java.util.Comparator;
import java.util.List;

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
                .map(c -> c.getSlug())
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> categoryTypes = entity.getCategories().stream()
                .map(c -> c.getCategoryType() != null ? c.getCategoryType() : "GENERIC")
                .distinct()
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
                categoryTypes,
                variants
        );
    }
}
