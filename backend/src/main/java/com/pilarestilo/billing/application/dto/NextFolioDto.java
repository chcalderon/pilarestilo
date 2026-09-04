package com.pilarestilo.billing.application.dto;

/** Null, not zero, when the document type has never had a numeric-looking folio to suggest from. */
public record NextFolioDto(Long nextFolio) {
}
