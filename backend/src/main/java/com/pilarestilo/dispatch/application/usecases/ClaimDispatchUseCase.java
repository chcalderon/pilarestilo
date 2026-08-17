package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.dispatch.domain.ports.SalesDocumentGate;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ClaimDispatchUseCase {
    private final DispatchRepository dispatchRepository;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final SalesDocumentGate salesDocumentGate;

    public ClaimDispatchUseCase(DispatchRepository dispatchRepository,
                                UpdateOrderStatusUseCase updateOrderStatusUseCase,
                                SalesDocumentGate salesDocumentGate) {
        this.dispatchRepository = dispatchRepository;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.salesDocumentGate = salesDocumentGate;
    }

    @Transactional
    public DispatchDto execute(UUID dispatchId, UUID dispatcherId) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        if (salesDocumentGate.blocksDispatch(d.getOrderId())) {
            throw new DomainException(
                    "This order has no registered boleta. Register it before preparing the parcel.");
        }
        d.claim(dispatcherId);
        Dispatch saved = dispatchRepository.save(d);
        updateOrderStatusUseCase.execute(saved.getOrderId(), OrderStatus.PREPARING_ORDER);
        return DispatchDto.from(saved);
    }
}
