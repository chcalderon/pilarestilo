package com.pilarestilo.inventoryservice.application;

import com.pilarestilo.inventoryservice.persistence.ProductEntity;
import com.pilarestilo.inventoryservice.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Test
    void list_delegates_with_built_specification() {
        InventoryQueryService service = new InventoryQueryService(productRepository);
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<ProductEntity> expected = new PageImpl<>(List.of(new ProductEntity()));
        when(productRepository.findAll(ArgumentMatchers.<Specification<ProductEntity>>any(), eq(pageRequest)))
                .thenReturn(expected);

        Page<ProductEntity> result = service.list("NUEVO", "Prada", true, "vestidos", "search", pageRequest);

        assertSame(expected, result);
        ArgumentCaptor<Specification<ProductEntity>> specCaptor = ArgumentCaptor.captor();
        verify(productRepository).findAll(specCaptor.capture(), eq(pageRequest));
        assertEquals(true, specCaptor.getValue() != null);
    }

    @Test
    void get_by_id_returns_or_throws() {
        InventoryQueryService service = new InventoryQueryService(productRepository);
        UUID productId = UUID.randomUUID();
        ProductEntity product = new ProductEntity();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertSame(product, service.getById(productId));

        when(productRepository.findById(productId)).thenReturn(Optional.empty());
        NoSuchElementException ex = assertThrows(NoSuchElementException.class, () -> service.getById(productId));
        assertEquals("Product not found: " + productId, ex.getMessage());
    }
}
