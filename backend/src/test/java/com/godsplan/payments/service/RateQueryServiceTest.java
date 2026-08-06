package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.ExchangeRateResponse;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateQueryServiceTest {

    @Mock private ExchangeRateService exchangeRates;

    @InjectMocks private RateQueryService service;

    @Test
    void get_validCurrencyPair_returnsExchangeRateResponse() {
        // Arrange
        RateQuote quote = new RateQuote(new BigDecimal("1.2500"), Instant.now(), "exchangerate.host");
        when(exchangeRates.getRate("USD", "EUR", BigDecimal.ONE)).thenReturn(quote);

        // Act
        ExchangeRateResponse result = service.get("usd", "eur");

        // Assert
        assertThat(result.base()).isEqualTo("USD");
        assertThat(result.quote()).isEqualTo("EUR");
        assertThat(result.rate()).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(result.source()).isEqualTo("exchangerate.host");
        assertThat(result.fetchedAt()).isNotNull();
    }

    @Test
    void get_normalizesCurrencyToUppercase() {
        // Arrange
        RateQuote quote = new RateQuote(new BigDecimal("83.50"), Instant.now(), "test-source");
        when(exchangeRates.getRate("USD", "INR", BigDecimal.ONE)).thenReturn(quote);

        // Act
        ExchangeRateResponse result = service.get("USD", "inr");

        // Assert
        assertThat(result.base()).isEqualTo("USD");
        assertThat(result.quote()).isEqualTo("INR");
        verify(exchangeRates).getRate("USD", "INR", BigDecimal.ONE);
    }

    @Test
    void get_alreadyUppercase_noDoubleNormalization() {
        // Arrange
        RateQuote quote = new RateQuote(new BigDecimal("0.75"), Instant.now(), "test");
        when(exchangeRates.getRate("GBP", "USD", BigDecimal.ONE)).thenReturn(quote);

        // Act
        ExchangeRateResponse result = service.get("GBP", "USD");

        // Assert
        assertThat(result.base()).isEqualTo("GBP");
        assertThat(result.quote()).isEqualTo("USD");
    }

    @Test
    void get_exchangeRateUnavailableFailure_throwsApiExceptionWith503() {
        // Arrange
        when(exchangeRates.getRate(any(), any(), any()))
                .thenThrow(new BusinessFailure(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                        "Exchange-rate service is not configured"));

        // Act & Assert
        assertThatThrownBy(() -> service.get("USD", "EUR"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void get_staleExchangeRateFailure_throwsApiExceptionWith400() {
        // Arrange
        when(exchangeRates.getRate(any(), any(), any()))
                .thenThrow(new BusinessFailure(ErrorCode.STALE_EXCHANGE_RATE,
                        "The cached exchange rate is too old"));

        // Act & Assert
        assertThatThrownBy(() -> service.get("USD", "EUR"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("The cached exchange rate is too old");
                });
    }

    @Test
    void get_conversionLimitFailure_throwsApiExceptionWith400() {
        // Arrange
        when(exchangeRates.getRate(any(), any(), any()))
                .thenThrow(new BusinessFailure(ErrorCode.CONVERSION_LIMIT_EXCEEDED,
                        "Converted amount exceeds limit"));

        // Act & Assert
        assertThatThrownBy(() -> service.get("USD", "EUR"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
