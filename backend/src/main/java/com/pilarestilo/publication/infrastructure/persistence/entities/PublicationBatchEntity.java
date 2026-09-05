package com.pilarestilo.publication.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publication_batches")
public class PublicationBatchEntity {

    @Id
    private UUID id;

    @Column(name = "caption_template", columnDefinition = "text", nullable = false)
    private String captionTemplate;

    @Column(name = "hashtags_json", columnDefinition = "text", nullable = false)
    private String hashtagsJson;

    @Column(name = "campaign_label", length = 120)
    private String campaignLabel;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCaptionTemplate() { return captionTemplate; }
    public void setCaptionTemplate(String captionTemplate) { this.captionTemplate = captionTemplate; }
    public String getHashtagsJson() { return hashtagsJson; }
    public void setHashtagsJson(String hashtagsJson) { this.hashtagsJson = hashtagsJson; }
    public String getCampaignLabel() { return campaignLabel; }
    public void setCampaignLabel(String campaignLabel) { this.campaignLabel = campaignLabel; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
