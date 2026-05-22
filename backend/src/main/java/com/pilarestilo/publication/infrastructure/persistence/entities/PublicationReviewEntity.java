package com.pilarestilo.publication.infrastructure.persistence.entities;

import com.pilarestilo.publication.domain.enums.PublicationReviewAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publication_reviews")
public class PublicationReviewEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private PublicationEntity publication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PublicationReviewAction action;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PublicationEntity getPublication() { return publication; }
    public void setPublication(PublicationEntity publication) { this.publication = publication; }
    public PublicationReviewAction getAction() { return action; }
    public void setAction(PublicationReviewAction action) { this.action = action; }
    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
