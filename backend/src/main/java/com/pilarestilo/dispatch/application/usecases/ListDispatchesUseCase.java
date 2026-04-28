package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ListDispatchesUseCase {
    private final DispatchRepository dispatchRepository;
    public ListDispatchesUseCase(DispatchRepository dispatchRepository) { this.dispatchRepository = dispatchRepository; }

    public List<DispatchDto> executeForDispatcher(UUID dispatcherId) {
        List<DispatchDto> pending = dispatchRepository.findByStatus(DispatchStatus.PENDING)
                .stream().map(DispatchDto::from).toList();
        List<DispatchDto> inProgress = dispatchRepository
                .findByDispatcherIdAndStatus(dispatcherId, DispatchStatus.IN_PROGRESS)
                .stream().map(DispatchDto::from).toList();
        return Stream.concat(inProgress.stream(), pending.stream()).toList();
    }

    public Page<DispatchDto> executeForAdmin(Pageable pageable) {
        return dispatchRepository.findAll(pageable).map(DispatchDto::from);
    }
}
