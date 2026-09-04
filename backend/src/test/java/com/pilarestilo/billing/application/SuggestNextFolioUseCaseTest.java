package com.pilarestilo.billing.application;

import com.pilarestilo.billing.application.usecases.SuggestNextFolioUseCase;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestNextFolioUseCaseTest {

    @Mock SalesDocumentRepository salesDocumentRepository;

    SuggestNextFolioUseCase useCase;

    @Test
    void suggestsOnePastTheHighestFolioOfThatType() {
        useCase = new SuggestNextFolioUseCase(salesDocumentRepository);
        when(salesDocumentRepository.findMaxNumericFolio(SalesDocumentType.BOLETA))
                .thenReturn(Optional.of("1042"));

        assertThat(useCase.execute(SalesDocumentType.BOLETA)).contains(1043L);
    }

    /** Nothing useful to suggest, not zero -- a shop's first-ever boleta of a type has no prior
     * folio to build on. */
    @Test
    void isEmptyWhenTheTypeHasNeverIssuedANumericFolio() {
        useCase = new SuggestNextFolioUseCase(salesDocumentRepository);
        when(salesDocumentRepository.findMaxNumericFolio(SalesDocumentType.FACTURA))
                .thenReturn(Optional.empty());

        assertThat(useCase.execute(SalesDocumentType.FACTURA)).isEmpty();
    }

    /** Boleta and factura draw from separate SII folio ranges -- one type's history must never
     * leak into the other's suggestion. */
    @Test
    void looksUpTheRequestedTypeOnly() {
        useCase = new SuggestNextFolioUseCase(salesDocumentRepository);
        when(salesDocumentRepository.findMaxNumericFolio(SalesDocumentType.FACTURA))
                .thenReturn(Optional.of("7"));

        useCase.execute(SalesDocumentType.FACTURA);

        org.mockito.Mockito.verify(salesDocumentRepository).findMaxNumericFolio(SalesDocumentType.FACTURA);
        org.mockito.Mockito.verify(salesDocumentRepository, org.mockito.Mockito.never())
                .findMaxNumericFolio(SalesDocumentType.BOLETA);
    }
}
