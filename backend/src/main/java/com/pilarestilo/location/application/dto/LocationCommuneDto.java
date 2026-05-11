package com.pilarestilo.location.application.dto;

public record LocationCommuneDto(
        Long id,
        Integer regionId,
        Long cityId,
        String name
) {
}

