package com.pilarestilo.shared.infrastructure.web;

import com.pilarestilo.inventory.domain.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerStockTest {

    @Test
    void insufficient_stock_maps_to_409() {
        ProblemDetail pd = new GlobalExceptionHandler()
                .handleInsufficientStock(new InsufficientStockException("Stock insuficiente para Rojo / M"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).contains("Stock insuficiente");
    }
}
