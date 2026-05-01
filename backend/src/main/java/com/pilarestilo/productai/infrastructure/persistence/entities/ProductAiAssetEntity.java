package com.pilarestilo.productai.infrastructure.persistence.entities;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_ai_assets")
public class ProductAiAssetEntity {

    @Id
    private UUID id;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "processed_master_url", columnDefinition = "TEXT")
    private String processedMasterUrl;

    @Column(name = "processed_web_url", columnDefinition = "TEXT")
    private String processedWebUrl;

    @Column(name = "processed_thumb_url", columnDefinition = "TEXT")
    private String processedThumbUrl;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDraftId() {
        return draftId;
    }

    public void setDraftId(UUID draftId) {
        this.draftId = draftId;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getProcessedMasterUrl() {
        return processedMasterUrl;
    }

    public void setProcessedMasterUrl(String processedMasterUrl) {
        this.processedMasterUrl = processedMasterUrl;
    }

    public String getProcessedWebUrl() {
        return processedWebUrl;
    }

    public void setProcessedWebUrl(String processedWebUrl) {
        this.processedWebUrl = processedWebUrl;
    }

    public String getProcessedThumbUrl() {
        return processedThumbUrl;
    }

    public void setProcessedThumbUrl(String processedThumbUrl) {
        this.processedThumbUrl = processedThumbUrl;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
