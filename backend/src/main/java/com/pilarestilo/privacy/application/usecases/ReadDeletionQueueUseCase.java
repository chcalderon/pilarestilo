package com.pilarestilo.privacy.application.usecases;

import com.pilarestilo.privacy.application.dto.DeletionQueueItemDto;
import com.pilarestilo.privacy.domain.model.DataDeletionRequest;
import com.pilarestilo.privacy.domain.ports.DataDeletionRequestRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the deletion queue with the person attached to each row.
 *
 * <p>Lives here rather than in the controller because it crosses two aggregates, and here rather
 * than in a mapper because it reads from the database - a mapper that queries is a mapper nobody
 * can call twice safely.
 */
@Service
public class ReadDeletionQueueUseCase {

    private final DataDeletionRequestRepository deletionRepository;
    private final UserRepository userRepository;

    public ReadDeletionQueueUseCase(DataDeletionRequestRepository deletionRepository,
                                    UserRepository userRepository) {
        this.deletionRepository = deletionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<DeletionQueueItemDto> page(boolean openOnly, Pageable pageable) {
        Page<DataDeletionRequest> requests = openOnly
                ? deletionRepository.findOpen(pageable)
                : deletionRepository.findAll(pageable);
        return requests.map(this::describe);
    }

    /**
     * Also used for the answer to resolving one, so the screen can replace the row it just acted on
     * instead of reloading the page under the operator.
     */
    @Transactional(readOnly = true)
    public DeletionQueueItemDto describe(DataDeletionRequest request) {
        return DeletionQueueItemDto.from(request, userRepository.findById(request.getUserId()));
    }
}
