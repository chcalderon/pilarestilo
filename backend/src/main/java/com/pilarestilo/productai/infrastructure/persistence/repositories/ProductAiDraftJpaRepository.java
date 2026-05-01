package com.pilarestilo.productai.infrastructure.persistence.repositories;

import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductAiDraftJpaRepository extends JpaRepository<ProductAiDraftEntity, UUID> {
}
