package com.godsplan.payments.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(
        BigDecimal maxAmount,
        Set<String> supportedCurrencies,
        ExchangeRate exchangeRate,
        Processing processing) {
    public record ExchangeRate(String url, String apiKey, Duration freshTtl, Duration maxAge, Duration timeout) {}

    public record Processing(Duration stageDelay) {}
}

