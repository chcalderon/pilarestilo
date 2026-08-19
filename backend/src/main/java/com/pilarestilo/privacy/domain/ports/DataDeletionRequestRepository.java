package com.pilarestilo.privacy.domain.ports;

import com.pilarestilo.privacy.domain.model.DataDeletionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DataDeletionRequestRepository {

    DataDeletionRequest save(DataDeletionRequest request);

    Optional<DataDeletionRequest> findById(UUID id);

    /** The one still waiting for an answer, if any. Asking twice is the same ask. */
    Optional<DataDeletionRequest> findOpenByUserId(UUID userId);

    /** Oldest first: the queue is read by how long somebody has been waiting. */
    Page<DataDeletionRequest> findOpen(Pageable pageable);

    Page<DataDeletionRequest> findAll(Pageable pageable);
}
