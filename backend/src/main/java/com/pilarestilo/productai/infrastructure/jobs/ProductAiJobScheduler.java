package com.pilarestilo.productai.infrastructure.jobs;

import com.pilarestilo.productai.application.ProductAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
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
