package com.pilarestilo.dispatch.infrastructure.jobs;

import com.pilarestilo.dispatch.application.usecases.AutoConfirmDeliveredDispatchesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DispatchAutoDeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchAutoDeliveryScheduler.class);

    private final AutoConfirmDeliveredDispatchesUseCase autoConfirmDeliveredDispatchesUseCase;

    public DispatchAutoDeliveryScheduler(AutoConfirmDeliveredDispatchesUseCase autoConfirmDeliveredDispatchesUseCase) {
        this.autoConfirmDeliveredDispatchesUseCase = autoConfirmDeliveredDispatchesUseCase;
    }

    @Scheduled(cron = "${app.dispatch.auto-delivery.cron:0 */30 * * * *}")
    public void run() {
        int updated = autoConfirmDeliveredDispatchesUseCase.execute();
        if (updated > 0) {
            log.info("Auto-confirmed {} dispatched orders as delivered", updated);
        }
    }
}
