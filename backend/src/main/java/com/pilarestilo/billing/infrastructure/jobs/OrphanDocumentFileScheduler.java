package com.pilarestilo.billing.infrastructure.jobs;

import com.pilarestilo.billing.application.usecases.SweepOrphanDocumentFilesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrphanDocumentFileScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrphanDocumentFileScheduler.class);

    private final SweepOrphanDocumentFilesUseCase sweepOrphanDocumentFilesUseCase;

    public OrphanDocumentFileScheduler(SweepOrphanDocumentFilesUseCase sweepOrphanDocumentFilesUseCase) {
        this.sweepOrphanDocumentFilesUseCase = sweepOrphanDocumentFilesUseCase;
    }

    /** Nightly. Orphans arrive one abandoned drawer at a time; nothing here is urgent. */
    @Scheduled(cron = "${app.documents.orphan-sweep.cron:0 30 4 * * *}")
    public void run() {
        int removed = sweepOrphanDocumentFilesUseCase.execute();
        if (removed > 0) {
            log.info("Removed {} unclaimed sales document files", removed);
        }
    }
}
