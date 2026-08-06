package com.godsplan.payments.service;

import com.godsplan.payments.config.PaymentProperties;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.ExchangeRateSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock private ExchangeRateSnapshotRepository snapshots;

    private ExchangeRateService service;
    private HttpClient mockHttpClient;
    private PaymentProperties propertiesWithKey;
    private PaymentProperties propertiesNoKey;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        propertiesWithKey = new PaymentProperties(
                new BigDecimal("1000000.00"),
                Set.of("USD", "EUR"),
                new PaymentProperties.ExchangeRate(
                        "https://api.exchangerate.host/convert", "test-api-key",
                        Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofSeconds(2)));

        propertiesNoKey = new PaymentProperties(
                new BigDecimal("1000000.00"),
                Set.of("USD", "EUR"),
                new PaymentProperties.ExchangeRate(
                        "https://api.exchangerate.host/convert", "",
                        Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofSeconds(2)));

        service = new ExchangeRateService(propertiesWithKey, new ObjectMapper(), snapshots);
        mockHttpClient = mock(HttpClient.class);
        ReflectionTestUtils.setField(service, "httpClient", mockHttpClient);
    }

    // ── identity (same currency) ──────────────────────────────────────────────

    @Test
    void getRate_sameCurrency_returnsIdentityRate() {
        RateQuote rate = service.getRate("USD", "USD", BigDecimal.ONE);

        assertThat(rate.rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(rate.source()).isEqualTo("identity");
        assertThat(rate.fetchedAt()).isNotNull();
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    void getRate_sameCurrencyDifferentCase_returnsIdentityRate() {
        RateQuote rate = service.getRate("usd", "USD", BigDecimal.ONE);

        assertThat(rate.rate()).isEqualByComparingTo(BigDecimal.ONE);
        verifyNoInteractions(mockHttpClient);
    }

    // ── blank / null API key ──────────────────────────────────────────────────

    @Test
    void getRate_blankApiKey_throwsExchangeRateUnavailableFailure() {
        ExchangeRateService serviceNoKey = new ExchangeRateService(
                propertiesNoKey, new ObjectMapper(), snapshots);
        ReflectionTestUtils.setField(serviceNoKey, "httpClient", mockHttpClient);

        assertThatThrownBy(() -> serviceNoKey.getRate("USD", "EUR", BigDecimal.ONE))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> {
                    BusinessFailure bf = (BusinessFailure) e;
                    assertThat(bf.getCode()).isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE);
                    assertThat(bf.getMessage()).contains("not configured");
                });

        verifyNoInteractions(mockHttpClient);
    }

    // ── successful HTTP fetch ─────────────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getRate_successfulHttpResponse_returnsQuote() throws Exception {
        // Arrange
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"success":true,
                 "query":{"from":"USD","to":"EUR","amount":1},
                 "info":{"timestamp":1700000000,"quote":1.2500},
                 "result":1.25}
                """);
        doReturn(response).when(mockHttpClient).send(any(), any());
        when(snapshots.existsByBaseCurrencyAndQuoteCurrencyAndSourceAndFetchedAt(
                any(), any(), any(), any())).thenReturn(false);

        // Act
        RateQuote quote = service.getRate("USD", "EUR", BigDecimal.ONE);

        // Assert
        assertThat(quote.rate()).isEqualByComparingTo(new BigDecimal("1.2500"));
        assertThat(quote.source()).isEqualTo("exchangerate.host");
        assertThat(quote.fetchedAt()).isEqualTo(Instant.ofEpochSecond(1700000000));
        verify(snapshots).save(any());
    }

    // ── provider error responses ──────────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getRate_providerNon200_throwsExchangeRateUnavailableAfterRetry() throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        doReturn(response).when(mockHttpClient).send(any(), any());

        assertThatThrownBy(() -> service.getRate("USD", "EUR", BigDecimal.ONE))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE));

        // Verifies retry: send() called twice
        verify(mockHttpClient, times(2)).send(any(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getRate_providerSuccessFalse_throwsExchangeRateUnavailableAfterRetry() throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"success\":false}");
        doReturn(response).when(mockHttpClient).send(any(), any());

        assertThatThrownBy(() -> service.getRate("USD", "EUR", BigDecimal.ONE))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE));

        verify(mockHttpClient, times(2)).send(any(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getRate_providerMismatchedCurrencyPair_throwsUnavailable() throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"success":true,
                 "query":{"from":"GBP","to":"JPY","amount":1},
                 "info":{"timestamp":1700000000,"quote":1.25},
                 "result":1.25}
                """);
        doReturn(response).when(mockHttpClient).send(any(), any());

        assertThatThrownBy(() -> service.getRate("USD", "EUR", BigDecimal.ONE))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getRate_providerReturnsZeroRate_throwsUnavailable() throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"success":true,
                 "query":{"from":"USD","to":"EUR","amount":1},
                 "info":{"timestamp":1700000000,"quote":0},
                 "result":0}
                """);
        doReturn(response).when(mockHttpClient).send(any(), any());

        assertThatThrownBy(() -> service.getRate("USD", "EUR", BigDecimal.ONE))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getRate_networkException_throwsUnavailableAfterRetry() throws Exception {
        doThrow(new java.io.IOException("Connection refused")).when(mockHttpClient).send(any(), any());

        assertThatThrownBy(() -> service.getRate("USD", "EUR", BigDecimal.ONE))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE));

        verify(mockHttpClient, times(2)).send(any(), any());
    }

    // ── snapshot deduplication ────────────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getRate_snapshotAlreadyExists_doesNotSaveDuplicate() throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"success":true,
                 "query":{"from":"USD","to":"EUR","amount":1},
                 "info":{"timestamp":1700000000,"quote":1.25},
                 "result":1.25}
                """);
        doReturn(response).when(mockHttpClient).send(any(), any());
        // Snapshot already persisted — service should NOT save again
        when(snapshots.existsByBaseCurrencyAndQuoteCurrencyAndSourceAndFetchedAt(
                any(), any(), any(), any())).thenReturn(true);

        service.getRate("USD", "EUR", BigDecimal.ONE);

        verify(snapshots, never()).save(any());
    }
}
