package com.pilarestilo.product.application;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.usecases.CreateProductUseCase;
import com.pilarestilo.product.domain.events.ProductCreated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    DomainEventPublisher eventPublisher;

    @InjectMocks
    CreateProductUseCase useCase;

    @Test
    void creates_product_and_publishes_event() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDto dto = useCase.execute(
                "Bolso LV", "Desc auténtico",
                BigDecimal.valueOf(300000),
                "CLP",
                "http://img.example.com/bolso.jpg",
                "USED", "Louis Vuitton", 3, true, null
        );

        assertNotNull(dto.id());
        assertEquals("Bolso LV", dto.name());
        assertEquals("Louis Vuitton", dto.brand());
        assertEquals(3, dto.stock());
        verify(eventPublisher).publish(any(ProductCreated.class));
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void throws_when_price_is_zero() {
        assertThrows(Exception.class, () ->
                useCase.execute("Bolso", "desc", BigDecimal.ZERO, "CLP", "http://img", "USED", "LV", 1, true, null)
        );
    }

    @Test
    void throws_when_name_is_blank() {
        assertThrows(Exception.class, () ->
                useCase.execute("   ", "desc", BigDecimal.valueOf(100000), "CLP", "http://img", "USED", "LV", 1, true, null)
        );
    }
}
