package com.pilarestilo.location.infrastructure.persistence.repositories;

import com.pilarestilo.location.infrastructure.persistence.entities.GeoRegionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeoRegionJpaRepository extends JpaRepository<GeoRegionEntity, Integer> {
    List<GeoRegionEntity> findAllByOrderBySortOrderAscNameAsc();
}

