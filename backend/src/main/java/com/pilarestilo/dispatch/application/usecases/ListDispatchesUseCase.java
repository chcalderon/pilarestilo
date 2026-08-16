package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.DispatchOrderSummaryService;
import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ListDispatchesUseCase {
    private final DispatchRepository dispatchRepository;
    private final DispatchOrderSummaryService orderSummaryService;

    public ListDispatchesUseCase(DispatchRepository dispatchRepository,
                                 DispatchOrderSummaryService orderSummaryService) {
        this.dispatchRepository = dispatchRepository;
        this.orderSummaryService = orderSummaryService;
    }

    public List<DispatchDto> executeForDispatcher(UUID dispatcherId) {
        List<DispatchDto> pending = dispatchRepository.findByStatus(DispatchStatus.PENDING)
                .stream().map(DispatchDto::from).toList();
        List<DispatchDto> inProgress = dispatchRepository
                .findByDispatcherIdAndStatus(dispatcherId, DispatchStatus.IN_PROGRESS)
                .stream().map(DispatchDto::from).toList();
        return orderSummaryService.enrichWorkable(Stream.concat(inProgress.stream(), pending.stream()).toList());
    }

    public Page<DispatchDto> executeForAdmin(Pageable pageable) {
        Page<DispatchDto> page = dispatchRepository.findAll(pageable).map(DispatchDto::from);
        List<DispatchDto> enriched = orderSummaryService.enrich(page.getContent());
        return new PageImpl<>(enriched, pageable, page.getTotalElements());
    }
}
