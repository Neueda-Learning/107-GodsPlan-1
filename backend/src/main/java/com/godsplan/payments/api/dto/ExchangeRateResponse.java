package com.godsplan.payments.api.dto;

import com.godsplan.payments.service.RateQuote;
import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateResponse(String base, String quote, BigDecimal rate, String source, Instant fetchedAt) {
    public static ExchangeRateResponse from(String base, String quote, RateQuote rate) {
        return new ExchangeRateResponse(base, quote, rate.rate(), rate.source(), rate.fetchedAt());
    }
}

