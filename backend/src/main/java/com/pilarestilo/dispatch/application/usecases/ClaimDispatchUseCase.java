package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ClaimDispatchUseCase {
    private final DispatchRepository dispatchRepository;
    public ClaimDispatchUseCase(DispatchRepository dispatchRepository) { this.dispatchRepository = dispatchRepository; }

    public DispatchDto execute(UUID dispatchId, UUID dispatcherId) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        d.claim(dispatcherId);
        return DispatchDto.from(dispatchRepository.save(d));
    }
}
