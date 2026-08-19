package com.pilarestilo.privacy.infrastructure.persistence.repositories;

import com.pilarestilo.privacy.domain.enums.DeletionStatus;
import com.pilarestilo.privacy.domain.model.DataDeletionRequest;
import com.pilarestilo.privacy.domain.ports.DataDeletionRequestRepository;
import com.pilarestilo.privacy.infrastructure.persistence.entities.DataDeletionRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class DataDeletionRequestRepositoryAdapter implements DataDeletionRequestRepository {

    private final DataDeletionRequestJpaRepository jpaRepository;

    public DataDeletionRequestRepositoryAdapter(DataDeletionRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public DataDeletionRequest save(DataDeletionRequest request) {
        return toDomain(jpaRepository.save(toEntity(request)));
    }

    @Override
    public Optional<DataDeletionRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<DataDeletionRequest> findOpenByUserId(UUID userId) {
        return jpaRepository.findByUserIdAndStatus(userId, DeletionStatus.REQUESTED.name()).map(this::toDomain);
    }

    @Override
    public Page<DataDeletionRequest> findOpen(Pageable pageable) {
        return jpaRepository
                .findByStatusOrderByRequestedAtAsc(DeletionStatus.REQUESTED.name(), pageable)
                .map(this::toDomain);
    }

    @Override
    public Page<DataDeletionRequest> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    private DataDeletionRequestEntity toEntity(DataDeletionRequest request) {
        DataDeletionRequestEntity entity = new DataDeletionRequestEntity();
        entity.setId(request.getId());
        entity.setUserId(request.getUserId());
        entity.setStatus(request.getStatus().name());
        entity.setReason(request.getReason());
        entity.setRequestedAt(request.getRequestedAt());
        entity.setResolvedAt(request.getResolvedAt());
        entity.setResolvedBy(request.getResolvedBy());
        entity.setResolution(request.getResolution());
        entity.setCreatedAt(request.getCreatedAt());
        return entity;
    }

    private DataDeletionRequest toDomain(DataDeletionRequestEntity entity) {
        return DataDeletionRequest.reconstruct(
                entity.getId(),
                entity.getUserId(),
                DeletionStatus.valueOf(entity.getStatus()),
                entity.getReason(),
                entity.getRequestedAt(),
                entity.getResolvedAt(),
                entity.getResolvedBy(),
                entity.getResolution(),
                entity.getCreatedAt());
    }
}
