package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class MarkDispatchedUseCase {
    private final DispatchRepository dispatchRepository;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;

    public MarkDispatchedUseCase(DispatchRepository dispatchRepository,
                                 UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.dispatchRepository = dispatchRepository;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    @Transactional
    public DispatchDto execute(UUID dispatchId, UUID dispatcherId,
                                String carrier, String trackingCode,
                                LocalDate scheduledDate, String notes) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        if (!dispatcherId.equals(d.getDispatcherId())) {
            throw new DomainException("You can only dispatch orders you have claimed");
        }
        d.dispatch(carrier, trackingCode, scheduledDate, notes);
        Dispatch saved = dispatchRepository.save(d);
        updateOrderStatusUseCase.execute(saved.getOrderId(), OrderStatus.SHIPPED);
        return DispatchDto.from(saved);
    }
}
