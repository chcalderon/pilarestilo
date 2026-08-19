package com.pilarestilo.productai.infrastructure.jobs;

import com.pilarestilo.productai.application.ProductAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for AI jobs on a cron.
 *
 * <p>{@code app.product-ai.enabled} was documented as the switch for the pipeline and read by
 * nothing, so turning it off left this worker polling every twenty seconds and calling a paid API.
 * A flag that does not do what it says is worse than no flag. Missing means on, which is how the
 * setting behaved before it meant anything.
 */
@Component
@ConditionalOnProperty(prefix = "app.product-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductAiJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProductAiJobScheduler.class);

    private final ProductAiService productAiService;

    public ProductAiJobScheduler(ProductAiService productAiService) {
        this.productAiService = productAiService;
    }

    @Scheduled(cron = "${app.product-ai.worker.cron:*/20 * * * * *}")
    public void run() {
        int processed = productAiService.processDueJobs();
        if (processed > 0) {
            log.info("product_ai_jobs_processed={}", processed);
        }
    }
}
