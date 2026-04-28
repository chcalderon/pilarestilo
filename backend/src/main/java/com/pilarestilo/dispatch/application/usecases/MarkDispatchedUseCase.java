package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class MarkDispatchedUseCase {
    private final DispatchRepository dispatchRepository;
    public MarkDispatchedUseCase(DispatchRepository dispatchRepository) { this.dispatchRepository = dispatchRepository; }

    public DispatchDto execute(UUID dispatchId, UUID dispatcherId,
                                String carrier, String trackingCode,
                                LocalDate scheduledDate, String notes) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        if (!dispatcherId.equals(d.getDispatcherId())) {
            throw new DomainException("You can only dispatch orders you have claimed");
        }
        d.dispatch(carrier, trackingCode, scheduledDate, notes);
        return DispatchDto.from(dispatchRepository.save(d));
    }
}
