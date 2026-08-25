package com.pilarestilo.product.application.mappers;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.domain.model.Product;

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
                toConfigDto(product.getVariantFieldConfig()),
                sizeStocks,
                slugs,
                categoryTypes,
                variants
        );
    }

    private static ProductDto.ProductVariantFieldConfigDto toConfigDto(
            com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig config) {
        if (config == null) return null;
        return new ProductDto.ProductVariantFieldConfigDto(toFieldDto(config.primary()), toFieldDto(config.secondary()));
    }

    private static ProductDto.ProductVariantFieldConfigDto.FieldDto toFieldDto(
            com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig.FieldConfig field) {
        return new ProductDto.ProductVariantFieldConfigDto.FieldDto(field.label(), field.inputType().name(),
                field.options(), field.min(), field.max(), field.allowMultiple(), field.allowCustom());
    }
}
