package com.pilarestilo.billing.application.usecases;

import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Boletas and facturas are typed in by hand from the SII's own eBoleta app — see the boleta-gate
 * note in CLAUDE.md — so the shop still assigns each folio itself, but one issued a moment ago is
 * the best clue for what the next one probably is, and retyping it fresh every time is where a
 * transposed digit lives.
 */
@Service
public class SuggestNextFolioUseCase {

    private final SalesDocumentRepository salesDocumentRepository;

    public SuggestNextFolioUseCase(SalesDocumentRepository salesDocumentRepository) {
        this.salesDocumentRepository = salesDocumentRepository;
    }

    /** Empty when this type has never had a numeric-looking folio — nothing useful to suggest,
     * not zero. */
    @Transactional(readOnly = true)
    public Optional<Long> execute(SalesDocumentType type) {
        return salesDocumentRepository.findMaxNumericFolio(type)
                .map(Long::parseLong)
                .map(max -> max + 1);
    }
}
