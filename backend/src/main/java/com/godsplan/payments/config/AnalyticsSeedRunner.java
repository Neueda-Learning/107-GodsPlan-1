package com.godsplan.payments.config;

import com.godsplan.payments.service.AnalyticsSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("analytics-seed")
public class AnalyticsSeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsSeedRunner.class);
    private final AnalyticsSeedService seeds;
    private final ConfigurableApplicationContext context;

    public AnalyticsSeedRunner(AnalyticsSeedService seeds, ConfigurableApplicationContext context) {
        this.seeds = seeds;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = seeds.seed();
        log.info("Analytics seed complete: {} payments, {} refunds and {} FX snapshots inserted; {} total payments",
                result.paymentsInserted(), result.refundsInserted(), result.exchangeRatesInserted(), result.totalPayments());
        context.close();
    }
}
