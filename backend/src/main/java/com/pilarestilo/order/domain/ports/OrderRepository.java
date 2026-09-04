package com.pilarestilo.order.domain.ports;

import com.pilarestilo.order.domain.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    /** The order registered under this external-sale idempotency key, if any. */
    Optional<Order> findByExternalIdempotencyKey(String key);

    /** The order already created under this web-checkout idempotency key, if any. */
    Optional<Order> findByIdempotencyKey(String key);

    /** Every order in the given set, in one query. Missing ids are simply absent. */
    List<Order> findAllByIds(Collection<UUID> ids);

    Page<Order> findAll(Pageable pageable);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}
