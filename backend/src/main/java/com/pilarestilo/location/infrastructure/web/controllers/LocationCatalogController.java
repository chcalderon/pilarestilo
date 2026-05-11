package com.pilarestilo.location.infrastructure.web.controllers;

import com.pilarestilo.location.application.LocationCatalogService;
import com.pilarestilo.location.application.dto.LocationCityDto;
import com.pilarestilo.location.application.dto.LocationCommuneDto;
import com.pilarestilo.location.application.dto.LocationRegionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationCatalogController {

    private final LocationCatalogService locationCatalogService;

    public LocationCatalogController(LocationCatalogService locationCatalogService) {
        this.locationCatalogService = locationCatalogService;
    }

    @GetMapping("/tree")
    public List<LocationRegionDto> tree() {
        return locationCatalogService.listTree();
    }

    @GetMapping("/regions/{regionId}/cities")
    public List<LocationCityDto> cities(@PathVariable Integer regionId) {
        return locationCatalogService.listCitiesByRegion(regionId);
    }

    @GetMapping("/cities/{cityId}/comunas")
    public List<LocationCommuneDto> communes(@PathVariable Long cityId) {
        return locationCatalogService.listCommunesByCity(cityId);
    }

    @GetMapping("/comunas/search")
    public List<LocationCommuneDto> searchCommunes(
            @RequestParam("q") String q,
            @RequestParam(value = "regionId", required = false) Integer regionId,
            @RequestParam(value = "cityId", required = false) Long cityId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return locationCatalogService.searchCommunes(q, regionId, cityId, limit);
    }
}

