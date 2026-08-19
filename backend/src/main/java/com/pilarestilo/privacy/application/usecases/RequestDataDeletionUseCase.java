package com.pilarestilo.privacy.application.usecases;

import com.pilarestilo.privacy.domain.model.DataDeletionRequest;
import com.pilarestilo.privacy.domain.ports.DataDeletionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The customer asking to be forgotten.
 *
 * <p>It queues rather than acting, and that is deliberate: an order in flight still has to arrive,
 * a dispute still has to close, and the shop has to be able to say when it was asked and what it
 * did. Asking twice is the same ask — the second returns the first, so nobody ends up waiting on a
 * request that is already in the queue.
 */
@Service
public class RequestDataDeletionUseCase {

    private final DataDeletionRequestRepository deletionRepository;

    public RequestDataDeletionUseCase(DataDeletionRequestRepository deletionRepository) {
        this.deletionRepository = deletionRepository;
    }

    @Transactional
    public DataDeletionRequest execute(UUID userId, String reason) {
        return deletionRepository.findOpenByUserId(userId)
                .orElseGet(() -> deletionRepository.save(DataDeletionRequest.open(userId, reason)));
    }
}
