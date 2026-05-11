package com.pilarestilo.location.infrastructure.persistence.repositories;

import com.pilarestilo.location.infrastructure.persistence.entities.GeoCommuneEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GeoCommuneJpaRepository extends JpaRepository<GeoCommuneEntity, Long> {
    List<GeoCommuneEntity> findByCityIdInOrderBySortOrderAscNameAsc(Collection<Long> cityIds);

    List<GeoCommuneEntity> findByCityIdOrderBySortOrderAscNameAsc(Long cityId);

    Optional<GeoCommuneEntity> findByIdAndCityIdAndRegionId(Long id, Long cityId, Integer regionId);

    List<GeoCommuneEntity> findByNameContainingIgnoreCaseOrderByNameAsc(String q, Pageable pageable);

    List<GeoCommuneEntity> findByRegionIdAndNameContainingIgnoreCaseOrderByNameAsc(
            Integer regionId,
            String q,
            Pageable pageable
    );

    List<GeoCommuneEntity> findByCityIdAndNameContainingIgnoreCaseOrderByNameAsc(
            Long cityId,
            String q,
            Pageable pageable
    );
}

