package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.ExchangeRateResponse;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.BusinessFailure;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RateQueryService {
    private final ExchangeRateService exchangeRates;

    public RateQueryService(ExchangeRateService exchangeRates) {
        this.exchangeRates = exchangeRates;
    }

    public ExchangeRateResponse get(String base, String quote) {
        String normalizedBase = base.toUpperCase(Locale.ROOT);
        String normalizedQuote = quote.toUpperCase(Locale.ROOT);
        try {
            return ExchangeRateResponse.from(normalizedBase, normalizedQuote,
                    exchangeRates.getRate(normalizedBase, normalizedQuote, BigDecimal.ONE));
        } catch (BusinessFailure failure) {
            throw new ApiException(failure.getCode(),
                    failure.getCode().name().equals("EXCHANGE_RATE_UNAVAILABLE")
                            ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST,
                    failure.getMessage());
        }
    }
}

