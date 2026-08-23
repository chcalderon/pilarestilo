package com.pilarestilo.review.application.usecases;

import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshProductRatingUseCaseTest {

    private ReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private RefreshProductRatingUseCase useCase;

    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        productRepository = mock(ProductRepository.class);
        useCase = new RefreshProductRatingUseCase(reviewRepository, productRepository);
    }

    @Test
    @DisplayName("writes whatever the reviews currently say")
    void writesTheRecomputedSummary() {
        when(reviewRepository.computeSummary(productId))
                .thenReturn(new ReviewRepository.RatingSummary(BigDecimal.valueOf(4.5), 2));

        useCase.execute(productId);

        ArgumentCaptor<BigDecimal> average = ArgumentCaptor.forClass(BigDecimal.class);
        verify(productRepository).updateRatingSummary(eq(productId), average.capture(), eq(2));
        assertThat(average.getValue()).isEqualByComparingTo(BigDecimal.valueOf(4.5));
    }

    @Test
    @DisplayName("a product whose last review was deleted goes back to zero, not to null")
    void writesZeroWhenNothingIsLeft() {
        when(reviewRepository.computeSummary(productId))
                .thenReturn(new ReviewRepository.RatingSummary(null, 0));

        useCase.execute(productId);

        verify(productRepository).updateRatingSummary(productId, BigDecimal.ZERO, 0);
    }

    @Test
    @DisplayName("recomputes rather than adds, so a wrong count can heal instead of drifting further")
    void recomputesInsteadOfIncrementing() {
        when(reviewRepository.computeSummary(productId))
                .thenReturn(new ReviewRepository.RatingSummary(BigDecimal.valueOf(3), 1));

        useCase.execute(productId);
        useCase.execute(productId);

        // Twice the event, still a count of one: nothing accumulates.
        verify(productRepository, times(2))
                .updateRatingSummary(eq(productId), any(BigDecimal.class), eq(1));
        verify(productRepository, never())
                .updateRatingSummary(eq(productId), any(BigDecimal.class), eq(2));
        verify(reviewRepository, times(2)).computeSummary(productId);
        verify(productRepository, times(2))
                .updateRatingSummary(any(UUID.class), any(BigDecimal.class), anyInt());
    }
}
