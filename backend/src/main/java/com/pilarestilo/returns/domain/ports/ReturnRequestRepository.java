package com.pilarestilo.returns.domain.ports;

import com.pilarestilo.returns.domain.model.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnRequestRepository {

    ReturnRequest save(ReturnRequest request);

    Optional<ReturnRequest> findById(UUID id);

    /** The one that still needs resolving. Closed ones pile up behind it. */
    Optional<ReturnRequest> findOpenByOrderId(UUID orderId);

    List<ReturnRequest> findAllByOrderId(UUID orderId);

    /** Open requests first by deadline: what has to be paid soonest comes first. */
    Page<ReturnRequest> findOpenByDeadline(Pageable pageable);

    Page<ReturnRequest> findAll(Pageable pageable);

    /** Everything the customer has asked for, so Mi Cuenta can show it. */
    List<ReturnRequest> findByRequestedBy(UUID userId);
}
