package com.pilarestilo.product.application.mappers;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.domain.model.Product;

import java.util.List;

public class ProductMapper {

    private ProductMapper() {}

    public static ProductDto toDto(Product product) {
        List<ProductDto.SizeStockDto> sizeStocks = product.getSizeStocks().stream()
                .map(s -> new ProductDto.SizeStockDto(s.getSize().name(), s.getStock()))
                .toList();

        List<String> slugs = product.getCategorySlugs();

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
                sizeStocks,
                slugs
        );
    }
}
