package com.pilarestilo.order.infrastructure.persistence.repositories;

import com.pilarestilo.order.infrastructure.persistence.entities.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    /**
     * OrderEntity.items is lazy, and OrderRepositoryAdapter.toDomain() always walks it, so every
     * caller outside an open Hibernate session hit LazyInitializationException — the Kafka
     * listeners for PaymentConfirmed did, and open-in-view is disabled. Fetching items with the
     * order makes this lookup independent of an ambient session.
     *
     * Deliberately not applied to findAll: a collection join plus Pageable makes Hibernate
     * paginate in memory.
     */
    @Override
    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findById(UUID id);

    Page<OrderEntity> findByCustomerId(UUID customerId, Pageable pageable);

    /**
     * Orders with their lines already loaded, for callers that map outside a transaction.
     *
     * <p>Plain findAllById returns entities whose items are lazy, and the dispatch queue maps them
     * after the session closes — which threw LazyInitializationException. The fetch join also makes
     * this one query instead of one per order.
     */
    @Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
    List<OrderEntity> findAllByIdsWithItems(@Param("ids") Collection<UUID> ids);
}
