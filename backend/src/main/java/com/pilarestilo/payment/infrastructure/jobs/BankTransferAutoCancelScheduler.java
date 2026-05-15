package com.pilarestilo.payment.infrastructure.jobs;

import com.pilarestilo.payment.application.usecases.AutoCancelPendingBankTransferUseCase;
import com.pilarestilo.systemsettings.domain.events.BankTransferSettingsChangedEvent;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

@Component
public class BankTransferAutoCancelScheduler {

    private static final Logger log = LoggerFactory.getLogger(BankTransferAutoCancelScheduler.class);

    private final TaskScheduler taskScheduler;
    private final SystemSettingsRepository settingsRepo;
    private final AutoCancelPendingBankTransferUseCase useCase;

    private ScheduledFuture<?> current;

    public BankTransferAutoCancelScheduler(TaskScheduler taskScheduler,
                                           SystemSettingsRepository settingsRepo,
                                           AutoCancelPendingBankTransferUseCase useCase) {
        this.taskScheduler = taskScheduler;
        this.settingsRepo = settingsRepo;
        this.useCase = useCase;
    }

    @PostConstruct
    public synchronized void schedule() {
        if (current != null) {
            current.cancel(false);
            current = null;
        }
        var settings = settingsRepo.get();
        if (!settings.isBankTransferAutoCancelEnabled()) {
            log.info("Bank transfer auto-cancel scheduler disabled");
            return;
        }
        String cron = settings.getBankTransferAutoCancelCron();
        current = taskScheduler.schedule(this::runSafely, new CronTrigger(cron));
        log.info("Bank transfer auto-cancel scheduled with cron: {}", cron);
    }

    @EventListener
    public void onSettingsChanged(BankTransferSettingsChangedEvent event) {
        log.info("Bank transfer settings changed (enabled={}, cron={}), rescheduling", event.enabled(), event.cron());
        schedule();
    }

    private void runSafely() {
        try {
            int cancelled = useCase.execute();
            if (cancelled > 0) {
                log.info("Auto-cancelled {} bank transfer payment(s)", cancelled);
            }
        } catch (Exception ex) {
            log.error("Bank transfer auto-cancel run failed", ex);
        }
    }
}
