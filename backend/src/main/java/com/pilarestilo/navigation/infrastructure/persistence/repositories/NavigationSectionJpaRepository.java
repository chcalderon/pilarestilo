package com.pilarestilo.navigation.infrastructure.persistence.repositories;

import com.pilarestilo.navigation.infrastructure.persistence.entities.NavigationSectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NavigationSectionJpaRepository extends JpaRepository<NavigationSectionEntity, UUID> {

    @Query("SELECT n FROM NavigationSectionEntity n WHERE n.active = true ORDER BY n.sortOrder ASC")
    List<NavigationSectionEntity> findAllActiveOrdered();
}
