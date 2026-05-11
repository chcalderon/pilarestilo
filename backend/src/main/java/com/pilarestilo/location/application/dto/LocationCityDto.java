package com.pilarestilo.location.application.dto;

import java.util.List;

public record LocationCityDto(
        Long id,
        Integer regionId,
        String name,
        List<LocationCommuneDto> communes
) {
}

