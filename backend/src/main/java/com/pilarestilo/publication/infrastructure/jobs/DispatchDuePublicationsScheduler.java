package com.pilarestilo.publication.infrastructure.jobs;

import com.pilarestilo.publication.application.usecases.DispatchDuePublicationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DispatchDuePublicationsScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchDuePublicationsScheduler.class);

    private final DispatchDuePublicationsUseCase useCase;

    public DispatchDuePublicationsScheduler(DispatchDuePublicationsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(cron = "${app.social-publishing.dispatch.cron:*/20 * * * * *}")
    public void run() {
        int handled = useCase.execute();
        if (handled > 0) {
            log.info("Dispatch worker handled {} publications", handled);
        }
    }
}
