package com.pilarestilo.review.application.usecases;

import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.domain.model.Review;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListReviewsForProductUseCaseTest {

    private ReviewRepository reviewRepository;
    private ListReviewsForProductUseCase useCase;

    private final UUID productId = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private Review approvedFromSomebodyElse;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        useCase = new ListReviewsForProductUseCase(reviewRepository);

        approvedFromSomebodyElse = Review.create(productId, UUID.randomUUID(), 5, "Bonito", "Buen corte");
        approvedFromSomebodyElse.approve();
        when(reviewRepository.findApprovedByProductId(productId))
                .thenReturn(List.of(approvedFromSomebodyElse));
    }

    @Test
    @DisplayName("anonymous readers see only approved reviews")
    void anonymousSeesOnlyApproved() {
        List<ReviewDto> shown = useCase.execute(productId, null);

        assertThat(shown).extracting(ReviewDto::id).containsExactly(approvedFromSomebodyElse.getId());
        // No point asking for the reader's own review when there is no reader.
        verify(reviewRepository, never()).findLiveByProductIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("the reader also sees their own review while it waits for a moderator")
    void readerSeesTheirOwnPendingReview() {
        Review mineAwaitingApproval = Review.create(productId, viewer, 3, "Se destiñe", "A la segunda lavada");
        when(reviewRepository.findLiveByProductIdAndUserId(productId, viewer))
                .thenReturn(Optional.of(mineAwaitingApproval));

        List<ReviewDto> shown = useCase.execute(productId, viewer);

        assertThat(shown).extracting(ReviewDto::id)
                .containsExactlyInAnyOrder(approvedFromSomebodyElse.getId(), mineAwaitingApproval.getId());
        assertThat(shown).filteredOn(r -> r.id().equals(mineAwaitingApproval.getId()))
                .allMatch(r -> !r.approved());
    }

    @Test
    @DisplayName("an already-approved review of the reader's own is not listed twice")
    void doesNotDuplicateTheReadersApprovedReview() {
        Review mine = Review.create(productId, viewer, 4, "Rico", "Cae muy bien");
        mine.approve();
        when(reviewRepository.findApprovedByProductId(productId))
                .thenReturn(List.of(approvedFromSomebodyElse, mine));
        when(reviewRepository.findLiveByProductIdAndUserId(productId, viewer))
                .thenReturn(Optional.of(mine));

        List<ReviewDto> shown = useCase.execute(productId, viewer);

        assertThat(shown).hasSize(2);
        assertThat(shown).extracting(ReviewDto::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a reader with no review of their own just sees the approved ones")
    void readerWithoutAReviewSeesTheRest() {
        when(reviewRepository.findLiveByProductIdAndUserId(productId, viewer))
                .thenReturn(Optional.empty());

        assertThat(useCase.execute(productId, viewer))
                .extracting(ReviewDto::id)
                .containsExactly(approvedFromSomebodyElse.getId());
    }
}
