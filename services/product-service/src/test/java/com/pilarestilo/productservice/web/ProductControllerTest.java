package com.pilarestilo.productservice.web;

import com.pilarestilo.productservice.application.ProductQueryService;
import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.web.dto.ProductDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductQueryService queryService;

    @Test
    void list_and_search_map_results() {
        ProductController controller = new ProductController(queryService);
        ProductEntity product = product(UUID.randomUUID(), "Vestido");
        when(queryService.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(queryService.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        Page<ProductDto> listed = controller.list(
                null, null, null, null, null, null, null, null, null, PageRequest.of(0, 10));
        Page<ProductDto> searched = controller.search(
                "vestido", true, true, "NUEVO", "vestidos",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), PageRequest.of(0, 10));

        assertEquals(1, listed.getTotalElements());
        assertEquals(product.getId(), listed.getContent().get(0).id());
        assertEquals(1, searched.getTotalElements());
        assertEquals(product.getId(), searched.getContent().get(0).id());
    }

    @Test
    void get_by_id_maps_not_found_to_404() {
        ProductController controller = new ProductController(queryService);
        UUID id = UUID.randomUUID();
        when(queryService.getById(id)).thenThrow(new NoSuchElementException("Product not found: " + id));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.getById(id));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    private ProductEntity product(UUID id, String name) {
        ProductEntity entity = new ProductEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "name", name);
        ReflectionTestUtils.setField(entity, "description", "desc");
        ReflectionTestUtils.setField(entity, "priceAmount", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(entity, "priceCurrency", "CLP");
        ReflectionTestUtils.setField(entity, "condition", "NUEVO");
        ReflectionTestUtils.setField(entity, "brand", "Brand");
        ReflectionTestUtils.setField(entity, "stock", 1);
        ReflectionTestUtils.setField(entity, "active", true);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.now());
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
        ReflectionTestUtils.setField(entity, "shippingOriginZone", "LOCAL");
        return entity;
    }
}
