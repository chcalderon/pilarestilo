package com.pilarestilo.payment.application.usecases;

import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AutoCancelPendingBankTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(AutoCancelPendingBankTransferUseCase.class);
    private static final int BATCH_LIMIT = 100;
    private static final String CANCELLATION_REASON = "Cierre por sistema: comprobante no recibido";

    private final PaymentRepository paymentRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final DomainEventPublisher eventPublisher;

    public AutoCancelPendingBankTransferUseCase(PaymentRepository paymentRepository,
                                                SystemSettingsRepository systemSettingsRepository,
                                                DomainEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.systemSettingsRepository = systemSettingsRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public int execute() {
        var settings = systemSettingsRepository.get();
        if (!settings.isBankTransferAutoCancelEnabled()) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(Duration.ofMinutes(settings.getBankTransferAutoCancelTimeoutMinutes()));
        List<Payment> stale = paymentRepository.findPendingBankTransfersOlderThan(cutoff, BATCH_LIMIT);

        int cancelled = 0;
        for (Payment p : stale) {
            try {
                p.systemCancel(CANCELLATION_REASON);
                paymentRepository.save(p);
                eventPublisher.publish(new PaymentRejected(
                        p.getId(), p.getOrderId(), Payment.SYSTEM_REVIEWER_ID, Instant.now()));
                cancelled++;
            } catch (Exception ex) {
                log.error("Failed to auto-cancel payment {}: {}", p.getId(), ex.getMessage());
            }
        }
        return cancelled;
    }
}
