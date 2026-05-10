package com.pilarestilo.productservice.application;

import com.pilarestilo.productservice.persistence.ProductEntity;
import com.pilarestilo.productservice.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Test
    void list_builds_spec_and_delegates_to_repository() {
        ProductQueryService service = new ProductQueryService(productRepository);
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<ProductEntity> expected = new PageImpl<>(List.of(new ProductEntity()));
        when(productRepository.findAll(any(Specification.class), eq(pageRequest))).thenReturn(expected);

        Page<ProductEntity> result = service.list(
                "NUEVO",
                "Prada",
                new BigDecimal("10.00"),
                new BigDecimal("100.00"),
                true,
                true,
                "vestidos",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                pageRequest
        );

        assertSame(expected, result);
        ArgumentCaptor<Specification<ProductEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(productRepository).findAll(specCaptor.capture(), eq(pageRequest));
        assertEquals(true, specCaptor.getValue() != null);
    }

    @Test
    void search_builds_spec_and_delegates_to_repository() {
        ProductQueryService service = new ProductQueryService(productRepository);
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<ProductEntity> expected = new PageImpl<>(List.of(new ProductEntity()));
        when(productRepository.findAll(any(Specification.class), eq(pageRequest))).thenReturn(expected);

        Page<ProductEntity> result = service.search(
                "cartera",
                true,
                true,
                "USADO",
                "accesorios",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                pageRequest
        );

        assertSame(expected, result);
        verify(productRepository).findAll(any(Specification.class), eq(pageRequest));
    }

    @Test
    void get_by_id_returns_or_throws() {
        ProductQueryService service = new ProductQueryService(productRepository);
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertSame(product, service.getById(productId));

        when(productRepository.findById(productId)).thenReturn(Optional.empty());
        NoSuchElementException ex = assertThrows(NoSuchElementException.class, () -> service.getById(productId));
        assertEquals("Product not found: " + productId, ex.getMessage());
    }
}
