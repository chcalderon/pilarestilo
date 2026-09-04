package com.pilarestilo.product.application.usecases;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Delete" a product with a real order behind it used to throw a foreign-key violation the
 * customer saw as a bare 500. A product row now stays put forever -- this only ever flips
 * {@code active} off, which is what {@link ProductRepository#deleteById} being gone verifies.
 */
class DeleteProductUseCaseTest {

    private ProductRepository productRepository;
    private DeleteProductUseCase useCase;
    private Product product;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        useCase = new DeleteProductUseCase(productRepository);

        product = Product.create(
                "Vestido de prueba", "Descripcion", Money.of(BigDecimal.valueOf(19990), "CLP"),
                "https://example.com/img.jpg", ProductCondition.NEW, "Marca", 5, null
        );
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("deleting a product deactivates it instead of removing the row")
    void deactivatesRatherThanRemoving() {
        useCase.execute(product.getId());

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(product.getId());
        assertThat(saved.getValue().isActive()).isFalse();
    }

    @Test
    @DisplayName("deleting a product that is not there is an error, not a silent success")
    void missingProductFails() {
        UUID unknown = UUID.randomUUID();
        when(productRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknown))
                .isInstanceOf(NoSuchElementException.class);

        verify(productRepository, never()).save(any());
    }
}
