package com.pilarestilo.location.application;

import com.pilarestilo.location.application.dto.LocationCityDto;
import com.pilarestilo.location.application.dto.LocationCommuneDto;
import com.pilarestilo.location.application.dto.LocationRegionDto;
import com.pilarestilo.location.infrastructure.persistence.entities.GeoCityEntity;
import com.pilarestilo.location.infrastructure.persistence.entities.GeoCommuneEntity;
import com.pilarestilo.location.infrastructure.persistence.entities.GeoRegionEntity;
import com.pilarestilo.location.infrastructure.persistence.repositories.GeoCityJpaRepository;
import com.pilarestilo.location.infrastructure.persistence.repositories.GeoCommuneJpaRepository;
import com.pilarestilo.location.infrastructure.persistence.repositories.GeoRegionJpaRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LocationCatalogService {

    private final GeoRegionJpaRepository regionRepository;
    private final GeoCityJpaRepository cityRepository;
    private final GeoCommuneJpaRepository communeRepository;

    public LocationCatalogService(
            GeoRegionJpaRepository regionRepository,
            GeoCityJpaRepository cityRepository,
            GeoCommuneJpaRepository communeRepository
    ) {
        this.regionRepository = regionRepository;
        this.cityRepository = cityRepository;
        this.communeRepository = communeRepository;
    }

    @Transactional(readOnly = true)
    public List<LocationRegionDto> listTree() {
        List<GeoRegionEntity> regions = regionRepository.findAllByOrderBySortOrderAscNameAsc();
        if (regions.isEmpty()) {
            return List.of();
        }

        List<Integer> regionIds = regions.stream()
                .map(GeoRegionEntity::getId)
                .toList();

        List<GeoCityEntity> cities = cityRepository.findByRegionIdInOrderBySortOrderAscNameAsc(regionIds);
        List<Long> cityIds = cities.stream().map(GeoCityEntity::getId).toList();
        List<GeoCommuneEntity> communes = cityIds.isEmpty()
                ? List.of()
                : communeRepository.findByCityIdInOrderBySortOrderAscNameAsc(cityIds);

        Map<Long, List<LocationCommuneDto>> communesByCity = communes.stream()
                .collect(Collectors.groupingBy(
                        GeoCommuneEntity::getCityId,
                        Collectors.mapping(
                                c -> new LocationCommuneDto(c.getId(), c.getRegionId(), c.getCityId(), c.getName()),
                                Collectors.collectingAndThen(Collectors.toList(), list -> {
                                    list.sort(Comparator.comparing(LocationCommuneDto::name, String.CASE_INSENSITIVE_ORDER));
                                    return list;
                                })
                        )
                ));

        Map<Integer, List<LocationCityDto>> citiesByRegion = new HashMap<>();
        for (GeoCityEntity city : cities) {
            List<LocationCommuneDto> cityCommunes = new ArrayList<>(communesByCity.getOrDefault(city.getId(), List.of()));
            citiesByRegion.computeIfAbsent(city.getRegionId(), unused -> new ArrayList<>())
                    .add(new LocationCityDto(city.getId(), city.getRegionId(), city.getName(), cityCommunes));
        }

        for (List<LocationCityDto> regionCities : citiesByRegion.values()) {
            regionCities.sort(Comparator.comparing(LocationCityDto::name, String.CASE_INSENSITIVE_ORDER));
        }

        return regions.stream()
                .map(r -> new LocationRegionDto(
                        r.getId(),
                        r.getName(),
                        citiesByRegion.getOrDefault(r.getId(), List.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationCityDto> listCitiesByRegion(Integer regionId) {
        if (regionId == null) return List.of();
        return cityRepository.findByRegionIdOrderBySortOrderAscNameAsc(regionId)
                .stream()
                .map(c -> new LocationCityDto(c.getId(), c.getRegionId(), c.getName(), List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationCommuneDto> listCommunesByCity(Long cityId) {
        if (cityId == null) return List.of();
        return communeRepository.findByCityIdOrderBySortOrderAscNameAsc(cityId)
                .stream()
                .map(c -> new LocationCommuneDto(c.getId(), c.getRegionId(), c.getCityId(), c.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationCommuneDto> searchCommunes(String q, Integer regionId, Long cityId, Integer limit) {
        String term = q == null ? "" : q.trim();
        if (term.length() < 2) {
            return List.of();
        }
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 60));
        PageRequest page = PageRequest.of(0, safeLimit);

        List<GeoCommuneEntity> rows;
        if (cityId != null) {
            rows = communeRepository.findByCityIdAndNameContainingIgnoreCaseOrderByNameAsc(cityId, term, page);
        } else if (regionId != null) {
            rows = communeRepository.findByRegionIdAndNameContainingIgnoreCaseOrderByNameAsc(regionId, term, page);
        } else {
            rows = communeRepository.findByNameContainingIgnoreCaseOrderByNameAsc(term, page);
        }
        return rows.stream()
                .map(c -> new LocationCommuneDto(c.getId(), c.getRegionId(), c.getCityId(), c.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResolvedLocation resolveSelection(Integer regionId, Long cityId, Long communeId) {
        if (regionId == null) {
            throw new DomainException("regionId is required");
        }
        if (cityId == null) {
            throw new DomainException("cityId is required");
        }
        if (communeId == null) {
            throw new DomainException("comunaId is required");
        }

        GeoRegionEntity region = regionRepository.findById(regionId)
                .orElseThrow(() -> new DomainException("Region not found"));
        GeoCityEntity city = cityRepository.findById(cityId)
                .orElseThrow(() -> new DomainException("City not found"));
        if (!region.getId().equals(city.getRegionId())) {
            throw new DomainException("City does not belong to selected region");
        }

        GeoCommuneEntity commune = communeRepository.findByIdAndCityIdAndRegionId(communeId, cityId, regionId)
                .orElseThrow(() -> new DomainException("Comuna does not belong to selected city/region"));

        return new ResolvedLocation(
                region.getId(),
                region.getName(),
                city.getId(),
                city.getName(),
                commune.getId(),
                commune.getName()
        );
    }

    public record ResolvedLocation(
            Integer regionId,
            String regionName,
            Long cityId,
            String cityName,
            Long communeId,
            String communeName
    ) {
    }
}

