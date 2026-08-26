package com.pilarestilo.varianttemplate.infrastructure.persistence.repositories;

import com.pilarestilo.varianttemplate.infrastructure.persistence.entities.VariantTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VariantTemplateJpaRepository extends JpaRepository<VariantTemplateEntity, UUID> {
}
