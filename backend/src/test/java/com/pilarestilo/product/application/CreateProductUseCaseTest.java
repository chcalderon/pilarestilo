package com.pilarestilo.product.application;

import com.pilarestilo.product.application.dto.ProductDto;
import com.pilarestilo.product.application.dto.ProductVariantInput;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                "Bolso LV", "Desc autentico",
                BigDecimal.valueOf(300000),
                "CLP",
                BigDecimal.valueOf(360000),
                "CLP",
                "http://img.example.com/bolso.jpg",
                "USED", "Louis Vuitton", 3, true, null
        );

        assertNotNull(dto.id());
        assertEquals("Bolso LV", dto.name());
        assertEquals("Louis Vuitton", dto.brand());
        assertEquals(3, dto.stock());
        assertEquals(0, BigDecimal.valueOf(360000).compareTo(dto.listPriceAmount()));
        verify(eventPublisher).publish(any(ProductCreated.class));
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void throws_when_price_is_zero() {
        assertThrows(Exception.class, () ->
                useCase.execute("Bolso", "desc", BigDecimal.ZERO, "CLP", null, null, "http://img", "USED", "LV", 1, true, null)
        );
    }

    @Test
    void throws_when_name_is_blank() {
        assertThrows(Exception.class, () ->
                useCase.execute("   ", "desc", BigDecimal.valueOf(100000), "CLP", null, null, "http://img", "USED", "LV", 1, true, null)
        );
    }

    @Test
    void throws_when_list_price_is_not_greater_than_sale_price() {
        assertThrows(Exception.class, () ->
                useCase.execute("Bolso", "desc", BigDecimal.valueOf(100000), "CLP",
                        BigDecimal.valueOf(90000), "CLP", "http://img", "USED", "LV", 1, true, null)
        );
    }

    @Test
    void supports_composite_sizes_and_normalizes_to_uppercase_hyphen() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDto dto = useCase.execute(
                "Abrigo", "desc",
                BigDecimal.valueOf(120000),
                "CLP",
                null,
                null,
                "http://img",
                "NEW",
                "Pilar",
                0,
                true,
                null,
                List.of(
                        new ProductVariantInput("Camel", "xl", 1),
                        new ProductVariantInput("Camel", "l-xl", 2),
                        new ProductVariantInput("Camel", "xxl", 1),
                        new ProductVariantInput("Negro", "S-M-L", 3)
                )
        );

        assertEquals(7, dto.stock());
        assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("XL")));
        assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("L-XL")));
        assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("XXL")));
        assertTrue(dto.variants().stream().anyMatch(v -> v.size().equals("S-M-L")));
        assertTrue(dto.sizeStocks().stream().anyMatch(s -> s.size().equals("L-XL") && s.stock() == 2));
    }

    @Test
    void rejects_invalid_size_token_x() {
        assertThrows(Exception.class, () -> useCase.execute(
                "Abrigo", "desc",
                BigDecimal.valueOf(120000),
                "CLP",
                null,
                null,
                "http://img",
                "NEW",
                "Pilar",
                0,
                true,
                null,
                List.of(new ProductVariantInput("Camel", "X", 1))
        ));
    }

    @Test
    void rejects_invalid_composite_format_with_double_dash() {
        assertThrows(Exception.class, () -> useCase.execute(
                "Abrigo", "desc",
                BigDecimal.valueOf(120000),
                "CLP",
                null,
                null,
                "http://img",
                "NEW",
                "Pilar",
                0,
                true,
                null,
                List.of(new ProductVariantInput("Camel", "L--XL", 1))
        ));
    }
}
