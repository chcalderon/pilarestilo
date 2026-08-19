package com.pilarestilo.privacy.application;

import com.pilarestilo.privacy.application.usecases.RecordConsentUseCase;
import com.pilarestilo.privacy.domain.enums.ConsentType;
import com.pilarestilo.privacy.domain.model.DataConsent;
import com.pilarestilo.privacy.domain.ports.DataConsentRepository;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A consent is only evidence if it names the version of the text that was shown.
 *
 * <p>Everything here is about that: the version comes from the shop's published settings rather
 * than from the caller, re-accepting the same one writes nothing, and accepting after the shop
 * published a new one is a new row so the history reads as it happened.
 */
@ExtendWith(MockitoExtension.class)
class RecordConsentUseCaseTest {

    @Mock DataConsentRepository consentRepository;
    @Mock SystemSettingsRepository systemSettingsRepository;

    RecordConsentUseCase useCase;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new RecordConsentUseCase(consentRepository, systemSettingsRepository);
        // Lenient: withdrawing never reads the published version, and neither does a consent the
        // domain refuses before the use case is reached.
        lenient().when(systemSettingsRepository.get()).thenReturn(SystemSettings.createDefault());
    }

    @Test
    void records_the_version_the_shop_is_publishing_and_where_it_came_from() {
        when(consentRepository.findLive(userId, ConsentType.TERMS)).thenReturn(Optional.empty());
        when(consentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        useCase.execute(userId, ConsentType.TERMS, "190.100.1.5", "Mozilla/5.0");

        ArgumentCaptor<DataConsent> captor = ArgumentCaptor.forClass(DataConsent.class);
        verify(consentRepository).save(captor.capture());
        DataConsent saved = captor.getValue();
        assertEquals(ConsentType.TERMS, saved.getType());
        assertEquals("2026-08", saved.getPolicyVersion());
        assertEquals("190.100.1.5", saved.getIpAddress());
        assertEquals("Mozilla/5.0", saved.getUserAgent());
        assertNotNull(saved.getAcceptedAt());
    }

    /** The same promise twice is one promise; a second row only makes the history harder to read. */
    @Test
    void accepting_the_same_version_again_writes_nothing() {
        DataConsent existing = DataConsent.accept(userId, ConsentType.PRIVACY, "2026-08", null, null);
        when(consentRepository.findLive(userId, ConsentType.PRIVACY)).thenReturn(Optional.of(existing));

        useCase.execute(userId, ConsentType.PRIVACY, null, null);

        verify(consentRepository, never()).save(any());
    }

    /**
     * A consent given under an older text is not consent to the new one. The old row stands and a
     * new one is written, so the shop can say what was agreed to and when it changed.
     */
    @Test
    void a_new_published_version_is_a_new_consent() {
        DataConsent old = DataConsent.accept(userId, ConsentType.PRIVACY, "2025-01", null, null);
        when(consentRepository.findLive(userId, ConsentType.PRIVACY)).thenReturn(Optional.of(old));
        when(consentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        useCase.execute(userId, ConsentType.PRIVACY, null, null);

        ArgumentCaptor<DataConsent> captor = ArgumentCaptor.forClass(DataConsent.class);
        verify(consentRepository).save(captor.capture());
        assertEquals("2026-08", captor.getValue().getPolicyVersion());
    }

    @Test
    void withdrawing_marketing_marks_the_row_rather_than_deleting_it() {
        DataConsent marketing = DataConsent.accept(userId, ConsentType.MARKETING, "2026-08", null, null);
        when(consentRepository.findLive(userId, ConsentType.MARKETING)).thenReturn(Optional.of(marketing));
        when(consentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        useCase.revokeMarketing(userId);

        ArgumentCaptor<DataConsent> captor = ArgumentCaptor.forClass(DataConsent.class);
        verify(consentRepository).save(captor.capture());
        assertNotNull(captor.getValue().getRevokedAt(), "the withdrawal is the fact being recorded");
        assertEquals("2026-08", captor.getValue().getPolicyVersion());
    }

    @Test
    void a_consent_without_a_version_is_refused_outright() {
        assertThrows(DomainException.class,
                () -> DataConsent.accept(userId, ConsentType.TERMS, "  ", null, null));
    }
}
