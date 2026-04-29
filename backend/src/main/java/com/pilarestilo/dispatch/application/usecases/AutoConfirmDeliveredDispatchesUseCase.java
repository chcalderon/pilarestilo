package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AutoConfirmDeliveredDispatchesUseCase {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmDeliveredDispatchesUseCase.class);

    private final DispatchRepository dispatchRepository;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final Clock clock;

    @Autowired
    public AutoConfirmDeliveredDispatchesUseCase(DispatchRepository dispatchRepository,
                                                 UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this(dispatchRepository, updateOrderStatusUseCase, Clock.systemUTC());
    }

    AutoConfirmDeliveredDispatchesUseCase(DispatchRepository dispatchRepository,
                                          UpdateOrderStatusUseCase updateOrderStatusUseCase,
                                          Clock clock) {
        this.dispatchRepository = dispatchRepository;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.clock = clock;
    }

    @Transactional
    public int execute() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(15);
        List<Dispatch> candidates = dispatchRepository.findByStatusAndDispatchedAtBefore(DispatchStatus.DISPATCHED, cutoff);
        int updated = 0;
        for (Dispatch dispatch : candidates) {
            try {
                dispatch.deliver();
                dispatchRepository.save(dispatch);
                updateOrderStatusUseCase.execute(dispatch.getOrderId(), OrderStatus.DELIVERED);
                updated += 1;
            } catch (Exception ex) {
                log.warn("Auto-delivery skipped for dispatch {} (order {}): {}", dispatch.getId(), dispatch.getOrderId(), ex.getMessage());
            }
        }
        return updated;
    }
}
