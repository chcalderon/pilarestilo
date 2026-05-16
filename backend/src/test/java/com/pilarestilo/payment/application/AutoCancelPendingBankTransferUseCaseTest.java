package com.pilarestilo.payment.application;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.application.usecases.AutoCancelPendingBankTransferUseCase;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoCancelPendingBankTransferUseCaseTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    SystemSettingsRepository systemSettingsRepository;

    @Mock
    DomainEventPublisher eventPublisher;

    @InjectMocks
    AutoCancelPendingBankTransferUseCase useCase;

    private SystemSettings enabledSettings() {
        return SystemSettings.createDefault();
    }

    private Payment pendingBankTransfer() {
        return Payment.create(
                UUID.randomUUID(),
                PaymentMethod.TRANSFER,
                "Pilar Estilo",
                "pagos@pilarestilo.com",
                "0000000000",
                "Banco de Chile",
                "Cuenta Corriente"
        );
    }

    @Test
    void disabled_returns_zero_without_touching_repository() {
        SystemSettings settings = mock(SystemSettings.class);
        when(settings.isBankTransferAutoCancelEnabled()).thenReturn(false);
        when(systemSettingsRepository.get()).thenReturn(settings);

        assertEquals(0, useCase.execute());

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancels_expired_payment_saves_and_publishes_event() {
        when(systemSettingsRepository.get()).thenReturn(enabledSettings());
        Payment p = pendingBankTransfer();
        when(paymentRepository.findPendingBankTransfersOlderThan(any(), anyInt()))
                .thenReturn(List.of(p));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int result = useCase.execute();

        assertEquals(1, result);
        assertEquals(PaymentStatus.REJECTED, p.getStatus());
        assertEquals(Payment.SYSTEM_REVIEWER_ID, p.getReviewedBy());
        verify(paymentRepository).save(p);
        verify(eventPublisher).publish(any(PaymentRejected.class));
    }

    @Test
    void no_stale_payments_returns_zero() {
        when(systemSettingsRepository.get()).thenReturn(enabledSettings());
        when(paymentRepository.findPendingBankTransfersOlderThan(any(), anyInt()))
                .thenReturn(List.of());

        assertEquals(0, useCase.execute());

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void per_row_failure_does_not_abort_batch() {
        when(systemSettingsRepository.get()).thenReturn(enabledSettings());

        Payment p1 = pendingBankTransfer();
        Payment p2 = pendingBankTransfer();
        when(paymentRepository.findPendingBankTransfersOlderThan(any(), anyInt()))
                .thenReturn(List.of(p1, p2));
        when(paymentRepository.save(p1)).thenThrow(new RuntimeException("DB error"));
        when(paymentRepository.save(p2)).thenAnswer(inv -> inv.getArgument(0));

        int result = useCase.execute();

        assertEquals(1, result);
        verify(eventPublisher, times(1)).publish(any(PaymentRejected.class));
    }

    @Test
    void cancels_multiple_payments_and_reports_count() {
        when(systemSettingsRepository.get()).thenReturn(enabledSettings());

        Payment p1 = pendingBankTransfer();
        Payment p2 = pendingBankTransfer();
        Payment p3 = pendingBankTransfer();
        when(paymentRepository.findPendingBankTransfersOlderThan(any(), anyInt()))
                .thenReturn(List.of(p1, p2, p3));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int result = useCase.execute();

        assertEquals(3, result);
        verify(eventPublisher, times(3)).publish(any(PaymentRejected.class));
    }
}
