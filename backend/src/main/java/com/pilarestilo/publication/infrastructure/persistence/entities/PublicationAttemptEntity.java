package com.pilarestilo.publication.infrastructure.persistence.entities;

import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationAttemptTriggerType;
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
@Table(name = "publication_attempts")
public class PublicationAttemptEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private PublicationEntity publication;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 24)
    private PublicationAttemptTriggerType triggerType;

    @Column(name = "request_id", length = 120)
    private String requestId;

    @Column(name = "workflow_run_id", length = 120)
    private String workflowRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PublicationAttemptStatus status;

    @Column(name = "remote_status", length = 80)
    private String remoteStatus;

    @Column(name = "remote_post_id", length = 255)
    private String remotePostId;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PublicationEntity getPublication() { return publication; }
    public void setPublication(PublicationEntity publication) { this.publication = publication; }
    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
    public PublicationAttemptTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(PublicationAttemptTriggerType triggerType) { this.triggerType = triggerType; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getWorkflowRunId() { return workflowRunId; }
    public void setWorkflowRunId(String workflowRunId) { this.workflowRunId = workflowRunId; }
    public PublicationAttemptStatus getStatus() { return status; }
    public void setStatus(PublicationAttemptStatus status) { this.status = status; }
    public String getRemoteStatus() { return remoteStatus; }
    public void setRemoteStatus(String remoteStatus) { this.remoteStatus = remoteStatus; }
    public String getRemotePostId() { return remotePostId; }
    public void setRemotePostId(String remotePostId) { this.remotePostId = remotePostId; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
