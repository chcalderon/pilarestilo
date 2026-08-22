package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.dispatch.domain.ports.SalesDocumentGate;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
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
    private final GetOrderUseCase getOrderUseCase;

    private final SalesDocumentGate salesDocumentGate;

    public MarkDispatchedUseCase(DispatchRepository dispatchRepository,
                                 UpdateOrderStatusUseCase updateOrderStatusUseCase,
                                 GetOrderUseCase getOrderUseCase,
                                 SalesDocumentGate salesDocumentGate) {
        this.dispatchRepository = dispatchRepository;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.salesDocumentGate = salesDocumentGate;
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
        /*
         * Checked again here, not only at claim. A dispatch claimed while its boleta was live can
         * still be sitting in progress when that boleta is voided, and the parcel would then leave
         * undeclared. Guarding only the way in is how the checkout let ?paso=resumen through.
         */
        if (salesDocumentGate.blocksDispatch(d.getOrderId())) {
            throw new DomainException(
                    "This order has no registered boleta. Register it before dispatching.");
        }
        String normalizedCarrier = normalizeRequired(carrier, "carrier");
        String normalizedTrackingCode = normalizeRequired(trackingCode, "tracking code");
        String finalNotes = normalizeOptional(notes);
        String configuredCarrier = resolveConfiguredCarrier(d);
        boolean hasOverride = configuredCarrier != null && !configuredCarrier.equalsIgnoreCase(normalizedCarrier);

        d.dispatch(
                normalizedCarrier,
                normalizedTrackingCode,
                scheduledDate,
                finalNotes,
                hasOverride ? configuredCarrier : null,
                hasOverride ? normalizedCarrier : null,
                hasOverride ? dispatcherId : null
        );
        Dispatch saved = dispatchRepository.save(d);
        updateOrderStatusUseCase.execute(saved.getOrderId(), OrderStatus.SHIPPED);
        return DispatchDto.from(saved);
    }

    private String resolveConfiguredCarrier(Dispatch dispatch) {
        String fromDispatchName = normalizeOptional(dispatch.getOrderShippingCourierName());
        if (fromDispatchName != null) {
            return fromDispatchName;
        }
        String fromDispatchId = normalizeOptional(dispatch.getOrderShippingCourierId());
        if (fromDispatchId != null) {
            return fromDispatchId;
        }
        try {
            OrderDto order = getOrderUseCase.execute(dispatch.getOrderId());
            String byName = normalizeOptional(order.shippingCourierName());
            if (byName != null) {
                return byName;
            }
            return normalizeOptional(order.shippingCourierId());
        } catch (Exception _) {
            return null;
        }
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new DomainException("Missing " + field);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
