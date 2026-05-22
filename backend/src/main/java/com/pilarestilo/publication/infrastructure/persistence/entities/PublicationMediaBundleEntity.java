package com.pilarestilo.publication.infrastructure.persistence.entities;

import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationMediaRenderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "publication_media_bundles")
public class PublicationMediaBundleEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private PublicationEntity publication;

    @Enumerated(EnumType.STRING)
    @Column(name = "bundle_type", nullable = false, length = 32)
    private PublicationMediaBundleType bundleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asset_manifest", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> assetManifest;

    @Column(name = "primary_asset_url", columnDefinition = "text")
    private String primaryAssetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "render_status", nullable = false, length = 24)
    private PublicationMediaRenderStatus renderStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PublicationEntity getPublication() { return publication; }
    public void setPublication(PublicationEntity publication) { this.publication = publication; }
    public PublicationMediaBundleType getBundleType() { return bundleType; }
    public void setBundleType(PublicationMediaBundleType bundleType) { this.bundleType = bundleType; }
    public Map<String, Object> getAssetManifest() { return assetManifest; }
    public void setAssetManifest(Map<String, Object> assetManifest) { this.assetManifest = assetManifest; }
    public String getPrimaryAssetUrl() { return primaryAssetUrl; }
    public void setPrimaryAssetUrl(String primaryAssetUrl) { this.primaryAssetUrl = primaryAssetUrl; }
    public PublicationMediaRenderStatus getRenderStatus() { return renderStatus; }
    public void setRenderStatus(PublicationMediaRenderStatus renderStatus) { this.renderStatus = renderStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
