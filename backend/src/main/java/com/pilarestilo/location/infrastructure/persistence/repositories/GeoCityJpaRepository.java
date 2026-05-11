package com.pilarestilo.location.infrastructure.persistence.repositories;

import com.pilarestilo.location.infrastructure.persistence.entities.GeoCityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GeoCityJpaRepository extends JpaRepository<GeoCityEntity, Long> {
    List<GeoCityEntity> findByRegionIdInOrderBySortOrderAscNameAsc(Collection<Integer> regionIds);

    List<GeoCityEntity> findByRegionIdOrderBySortOrderAscNameAsc(Integer regionId);
}

