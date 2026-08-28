package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReturnRequestRoRepository extends JpaRepository<ReturnRequestRoEntity, UUID> {
}
