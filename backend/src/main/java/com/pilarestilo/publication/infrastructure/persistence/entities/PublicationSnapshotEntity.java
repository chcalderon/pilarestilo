package com.pilarestilo.publication.infrastructure.persistence.entities;

import com.pilarestilo.publication.domain.enums.PublicationSnapshotType;
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
@Table(name = "publication_snapshots")
public class PublicationSnapshotEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private PublicationEntity publication;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_type", nullable = false, length = 32)
    private PublicationSnapshotType snapshotType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PublicationEntity getPublication() { return publication; }
    public void setPublication(PublicationEntity publication) { this.publication = publication; }
    public PublicationSnapshotType getSnapshotType() { return snapshotType; }
    public void setSnapshotType(PublicationSnapshotType snapshotType) { this.snapshotType = snapshotType; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
