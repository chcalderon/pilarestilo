package com.pilarestilo.review.application.usecases;

import com.pilarestilo.review.domain.events.ReviewDeleted;
import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
 * The endpoint behind this required nothing but a session, so any signed-in customer could delete
 * anybody's review. These are the rules that closed it.
 */
class DeleteReviewUseCaseTest {

    private ReviewRepository reviewRepository;
    private DomainEventPublisher eventPublisher;
    private DeleteReviewUseCase useCase;

    private final UUID productId = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private Review review;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        useCase = new DeleteReviewUseCase(reviewRepository, eventPublisher);

        review = Review.create(productId, author, 5, "Precioso", "Llegó tal cual la foto");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
    }

    @Test
    @DisplayName("the author can delete their own review")
    void authorCanDelete() {
        useCase.execute(review.getId(), author, false);

        verify(reviewRepository).deleteById(review.getId());
        verify(eventPublisher).publish(any(ReviewDeleted.class));
    }

    @Test
    @DisplayName("a moderator can delete somebody else's review")
    void moderatorCanDelete() {
        useCase.execute(review.getId(), stranger, true);

        verify(reviewRepository).deleteById(review.getId());
    }

    @Test
    @DisplayName("a signed-in stranger cannot delete a review that is not theirs")
    void strangerCannotDelete() {
        UUID reviewId = review.getId();

        assertThatThrownBy(() -> useCase.execute(reviewId, stranger, false))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("author or a moderator");

        verify(reviewRepository, never()).deleteById(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("the deleted event names the product, so the rating can be recomputed")
    void publishesTheProductSoTheRatingCanBeRefreshed() {
        useCase.execute(review.getId(), author, false);

        ArgumentCaptor<ReviewDeleted> published = ArgumentCaptor.forClass(ReviewDeleted.class);
        verify(eventPublisher).publish(published.capture());
        assertThat(published.getValue().productId()).isEqualTo(productId);
        assertThat(published.getValue().reviewId()).isEqualTo(review.getId());
    }

    @Test
    @DisplayName("deleting a review that is not there is an error, not a silent success")
    void missingReviewFails() {
        UUID unknown = UUID.randomUUID();
        when(reviewRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknown, author, true))
                .isInstanceOf(DomainException.class);

        assertThat(review.getSupersededAt()).isNull();
    }
}
