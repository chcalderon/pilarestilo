package com.pilarestilo.location.application.dto;

import java.util.List;

public record LocationRegionDto(
        Integer id,
        String name,
        List<LocationCityDto> cities
) {
}

