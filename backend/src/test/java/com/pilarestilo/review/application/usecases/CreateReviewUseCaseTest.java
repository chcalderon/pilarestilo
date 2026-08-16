package com.pilarestilo.review.application.usecases;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateReviewUseCaseTest {

    private ReviewRepository reviewRepository;
    private ProductRepository productRepository;
    private CreateReviewUseCase useCase;

    private final UUID productId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        productRepository = mock(ProductRepository.class);
        useCase = new CreateReviewUseCase(reviewRepository, productRepository, mock(DomainEventPublisher.class));

        when(productRepository.findById(productId)).thenReturn(Optional.of(mock(Product.class)));
        when(reviewRepository.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("a rating-only review is approved without a moderator, since there is no text to read")
    void autoApprovesRatingOnly() {
        ReviewDto saved = useCase.execute(productId, userId, 5, null, null);

        assertThat(saved.approved()).isTrue();
    }

    @Test
    @DisplayName("a review with text waits for approval")
    void leavesWrittenReviewPending() {
        ReviewDto saved = useCase.execute(productId, userId, 5, "Precioso", "Llegó tal cual la foto");

        assertThat(saved.approved()).isFalse();
    }

    @Test
    @DisplayName("reviewing again supersedes the previous review instead of being refused")
    void supersedesThePreviousReview() {
        Review previous = Review.create(productId, userId, 5, null, null);
        when(reviewRepository.findLiveByProductIdAndUserId(productId, userId)).thenReturn(Optional.of(previous));

        ReviewDto replacement = useCase.execute(productId, userId, 3, "Se destiñe", "A la segunda lavada");

        ArgumentCaptor<Review> written = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository, org.mockito.Mockito.times(2)).save(written.capture());

        List<Review> rows = written.getAllValues();
        assertThat(rows.getFirst().getSupersededAt())
                .as("the previous review is stamped, not deleted — it stays as history")
                .isNotNull();
        assertThat(rows.getFirst().getRating()).isEqualTo(5);
        assertThat(rows.get(1).getSupersededAt())
                .as("the replacement is the live one")
                .isNull();
        assertThat(replacement.rating()).isEqualTo(3);
    }

    @Test
    @DisplayName("a first review supersedes nothing")
    void writesOnlyOneRowWhenThereIsNoPreviousReview() {
        when(reviewRepository.findLiveByProductIdAndUserId(productId, userId)).thenReturn(Optional.empty());

        useCase.execute(productId, userId, 4, null, null);

        verify(reviewRepository, org.mockito.Mockito.times(1)).save(any(Review.class));
        verify(reviewRepository, never()).findById(any());
    }
}
