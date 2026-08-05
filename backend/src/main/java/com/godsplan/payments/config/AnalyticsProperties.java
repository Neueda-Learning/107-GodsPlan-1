package com.godsplan.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics")
public record AnalyticsProperties(
        String environment,
        String timeZone,
        int defaultRangeDays,
        int maxRangeDays,
        int maxPageSize,
        int maxQueryRows,
        String defaultBaseCurrency) {}
