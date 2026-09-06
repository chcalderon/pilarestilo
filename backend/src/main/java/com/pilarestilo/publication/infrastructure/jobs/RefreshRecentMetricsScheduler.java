package com.pilarestilo.publication.infrastructure.jobs;

import com.pilarestilo.publication.application.usecases.MetricsRefreshScope;
import com.pilarestilo.publication.application.usecases.RefreshMetricsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshRecentMetricsScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshRecentMetricsScheduler.class);

    private final RefreshMetricsUseCase useCase;
    private final int maxAgeDays;

    public RefreshRecentMetricsScheduler(RefreshMetricsUseCase useCase,
                                         @Value("${app.social-publishing.metrics.max-age-days:30}") int maxAgeDays) {
        this.useCase = useCase;
        this.maxAgeDays = maxAgeDays;
    }

    @Scheduled(cron = "${app.social-publishing.metrics.refresh-cron:0 0 6 * * *}")
    public void run() {
        RefreshMetricsUseCase.MetricsRefreshResult result =
                useCase.execute(new MetricsRefreshScope.RecentDays(maxAgeDays));
        if (result.refreshed() + result.failed() > 0) {
            log.info("Refreshed metrics for {} posts ({} failed)", result.refreshed(), result.failed());
        }
    }
}
