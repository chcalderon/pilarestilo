package com.pilarestilo.publication.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publication_metrics")
public class PublicationMetricsEntity {

    @Id
    @Column(name = "publication_id")
    private UUID publicationId;

    private Long impressions;
    private Long reach;
    private Long likes;
    private Long comments;
    private Long shares;
    private Long saved;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "fetch_error", columnDefinition = "text")
    private String fetchError;

    public UUID getPublicationId() { return publicationId; }
    public void setPublicationId(UUID publicationId) { this.publicationId = publicationId; }
    public Long getImpressions() { return impressions; }
    public void setImpressions(Long impressions) { this.impressions = impressions; }
    public Long getReach() { return reach; }
    public void setReach(Long reach) { this.reach = reach; }
    public Long getLikes() { return likes; }
    public void setLikes(Long likes) { this.likes = likes; }
    public Long getComments() { return comments; }
    public void setComments(Long comments) { this.comments = comments; }
    public Long getShares() { return shares; }
    public void setShares(Long shares) { this.shares = shares; }
    public Long getSaved() { return saved; }
    public void setSaved(Long saved) { this.saved = saved; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
    public String getFetchError() { return fetchError; }
    public void setFetchError(String fetchError) { this.fetchError = fetchError; }
}
