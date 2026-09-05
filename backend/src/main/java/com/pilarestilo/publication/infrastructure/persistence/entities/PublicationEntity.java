package com.pilarestilo.publication.infrastructure.persistence.entities;

import com.pilarestilo.publication.domain.enums.PublicationApprovalStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "publications")
public class PublicationEntity {

    @Id
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private PublicationSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PublicationPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 32)
    private PublicationChannelType channelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PublicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 32)
    private PublicationApprovalStatus approvalStatus;

    @Column(columnDefinition = "text")
    private String caption;

    @Column(name = "hashtags_json", columnDefinition = "text")
    private String hashtagsJson;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(name = "campaign_label", length = 120)
    private String campaignLabel;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "external_post_id", length = 255)
    private String externalPostId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "external_permalink", columnDefinition = "text")
    private String externalPermalink;

    @Column(name = "idempotency_key", nullable = false, length = 160, unique = true)
    private String idempotencyKey;

    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    @Column(name = "snapshot_version", nullable = false)
    private int snapshotVersion;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "publication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<PublicationMediaBundleEntity> mediaBundles = new ArrayList<>();

    @OneToMany(mappedBy = "publication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attemptNumber asc")
    private List<PublicationAttemptEntity> attempts = new ArrayList<>();

    @OneToMany(mappedBy = "publication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<PublicationReviewEntity> reviews = new ArrayList<>();

    @OneToMany(mappedBy = "publication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version asc")
    private List<PublicationSnapshotEntity> snapshots = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public PublicationSourceType getSourceType() { return sourceType; }
    public void setSourceType(PublicationSourceType sourceType) { this.sourceType = sourceType; }
    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
    public PublicationPlatform getPlatform() { return platform; }
    public void setPlatform(PublicationPlatform platform) { this.platform = platform; }
    public PublicationChannelType getChannelType() { return channelType; }
    public void setChannelType(PublicationChannelType channelType) { this.channelType = channelType; }
    public PublicationStatus getStatus() { return status; }
    public void setStatus(PublicationStatus status) { this.status = status; }
    public PublicationApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(PublicationApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getHashtagsJson() { return hashtagsJson; }
    public void setHashtagsJson(String hashtagsJson) { this.hashtagsJson = hashtagsJson; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getCampaignLabel() { return campaignLabel; }
    public void setCampaignLabel(String campaignLabel) { this.campaignLabel = campaignLabel; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getExternalPostId() { return externalPostId; }
    public void setExternalPostId(String externalPostId) { this.externalPostId = externalPostId; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public String getExternalPermalink() { return externalPermalink; }
    public void setExternalPermalink(String externalPermalink) { this.externalPermalink = externalPermalink; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int contentVersion) { this.contentVersion = contentVersion; }
    public int getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(int snapshotVersion) { this.snapshotVersion = snapshotVersion; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<PublicationMediaBundleEntity> getMediaBundles() { return mediaBundles; }
    public void setMediaBundles(List<PublicationMediaBundleEntity> mediaBundles) { this.mediaBundles = mediaBundles; }
    public List<PublicationAttemptEntity> getAttempts() { return attempts; }
    public void setAttempts(List<PublicationAttemptEntity> attempts) { this.attempts = attempts; }
    public List<PublicationReviewEntity> getReviews() { return reviews; }
    public void setReviews(List<PublicationReviewEntity> reviews) { this.reviews = reviews; }
    public List<PublicationSnapshotEntity> getSnapshots() { return snapshots; }
    public void setSnapshots(List<PublicationSnapshotEntity> snapshots) { this.snapshots = snapshots; }
}
