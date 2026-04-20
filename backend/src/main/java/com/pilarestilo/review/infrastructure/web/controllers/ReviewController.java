package com.pilarestilo.review.infrastructure.web.controllers;

import com.pilarestilo.review.application.dto.ReviewDto;
import com.pilarestilo.review.application.dto.ReviewSummaryDto;
import com.pilarestilo.review.application.usecases.*;
import com.pilarestilo.review.domain.ports.ReviewRepository;
import com.pilarestilo.review.infrastructure.web.requests.CreateReviewRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ReviewController {

    private final CreateReviewUseCase createReviewUseCase;
    private final ListReviewsForProductUseCase listReviewsForProductUseCase;
    private final GetReviewSummaryUseCase getReviewSummaryUseCase;
    private final DeleteReviewUseCase deleteReviewUseCase;
    private final ApproveReviewUseCase approveReviewUseCase;
    private final ListMyReviewsUseCase listMyReviewsUseCase;
    private final ReviewRepository reviewRepository;

    public ReviewController(CreateReviewUseCase createReviewUseCase,
                            ListReviewsForProductUseCase listReviewsForProductUseCase,
                            GetReviewSummaryUseCase getReviewSummaryUseCase,
                            DeleteReviewUseCase deleteReviewUseCase,
                            ApproveReviewUseCase approveReviewUseCase,
                            ListMyReviewsUseCase listMyReviewsUseCase,
                            ReviewRepository reviewRepository) {
        this.createReviewUseCase = createReviewUseCase;
        this.listReviewsForProductUseCase = listReviewsForProductUseCase;
        this.getReviewSummaryUseCase = getReviewSummaryUseCase;
        this.deleteReviewUseCase = deleteReviewUseCase;
        this.approveReviewUseCase = approveReviewUseCase;
        this.listMyReviewsUseCase = listMyReviewsUseCase;
        this.reviewRepository = reviewRepository;
    }

    @PostMapping("/api/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ReviewDto create(@PathVariable UUID productId,
                            @RequestBody @Valid CreateReviewRequest req,
                            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return createReviewUseCase.execute(productId, currentUser.id(), req.rating(), req.title(), req.comment());
    }

    @GetMapping("/api/products/{productId}/reviews")
    public List<ReviewDto> list(@PathVariable UUID productId) {
        return listReviewsForProductUseCase.execute(productId);
    }

    @GetMapping("/api/products/{productId}/reviews/summary")
    public ReviewSummaryDto summary(@PathVariable UUID productId) {
        return getReviewSummaryUseCase.execute(productId);
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void delete(@PathVariable UUID reviewId) {
        deleteReviewUseCase.execute(reviewId);
    }

    @PatchMapping("/api/reviews/{reviewId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ReviewDto approve(@PathVariable UUID reviewId) {
        return approveReviewUseCase.execute(reviewId);
    }

    @GetMapping("/api/reviews/mine")
    @PreAuthorize("isAuthenticated()")
    public List<ReviewDto> mine(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return listMyReviewsUseCase.execute(currentUser.id());
    }

    @GetMapping("/api/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReviewDto> listAll(@RequestParam(required = false) Boolean approved) {
        List<com.pilarestilo.review.domain.model.Review> reviews =
                approved != null ? reviewRepository.findByApproved(approved) : reviewRepository.findAll();
        return reviews.stream().map(r -> new ReviewDto(
                r.getId(), r.getProductId(), r.getUserId(),
                r.getRating(), r.getTitle(), r.getComment(),
                r.isApproved(), r.getCreatedAt()
        )).toList();
    }
}
