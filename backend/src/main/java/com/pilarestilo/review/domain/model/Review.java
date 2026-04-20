package com.pilarestilo.review.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

public class Review {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private UUID id;
    private UUID productId;
    private UUID userId;
    private int rating;
    private String title;
    private String comment;
    private boolean approved;
    private Instant createdAt;

    private Review() {}

    public static Review create(UUID productId, UUID userId, int rating, String title, String comment) {
        if (productId == null) throw new DomainException("Review productId cannot be null");
        if (userId == null) throw new DomainException("Review userId cannot be null");
        if (rating < 1 || rating > 5) throw new DomainException("Rating must be between 1 and 5");
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new DomainException("Comment exceeds maximum length of " + MAX_COMMENT_LENGTH);
        }

        Review review = new Review();
        review.id = UUID.randomUUID();
        review.productId = productId;
        review.userId = userId;
        review.rating = rating;
        review.title = (title != null) ? title.trim() : null;
        review.comment = comment;
        review.approved = false;
        review.createdAt = Instant.now();
        return review;
    }

    public void approve() {
        this.approved = true;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getUserId() { return userId; }
    public int getRating() { return rating; }
    public String getTitle() { return title; }
    public String getComment() { return comment; }
    public boolean isApproved() { return approved; }
    public Instant getCreatedAt() { return createdAt; }

    // Setters for persistence rehydration
    public void setId(UUID id) { this.id = id; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
