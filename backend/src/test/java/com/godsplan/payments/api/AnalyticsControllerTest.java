package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.AnalyticsResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.config.AnalyticsProperties;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@WithMockUser
class AnalyticsControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private AnalyticsService analytics;
    @MockBean private AnalyticsProperties properties;

    @BeforeEach
    void configureProperties() {
        when(properties.timeZone()).thenReturn("UTC");
        when(properties.defaultRangeDays()).thenReturn(30);
        when(properties.maxRangeDays()).thenReturn(365);
        when(properties.defaultBaseCurrency()).thenReturn("USD");
        when(properties.maxPageSize()).thenReturn(100);
        when(properties.maxQueryRows()).thenReturn(250_000);
        when(properties.environment()).thenReturn("test");
    }

    // ── GET /api/v1/analytics/overview ───────────────────────────────────────

    @Test
    void overview_noParams_returns200() throws Exception {
        AnalyticsResponse.Overview overview = buildOverview();
        when(analytics.overview(any())).thenReturn(overview);

        mvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.grouping").value("DAILY"));
    }

    @Test
    void overview_withDateRange_delegatesFilterToService() throws Exception {
        when(analytics.overview(any())).thenReturn(buildOverview());

        mvc.perform(get("/api/v1/analytics/overview")
                        .param("from", "2025-01-01")
                        .param("to", "2025-01-31"))
                .andExpect(status().isOk());

        verify(analytics).overview(argThat(f ->
                f.fromDate().toString().equals("2025-01-01")
                        && f.toDate().toString().equals("2025-01-31")));
    }

    @Test
    void overview_withStatusFilter_passesStatusToFilter() throws Exception {
        when(analytics.overview(any())).thenReturn(buildOverview());

        mvc.perform(get("/api/v1/analytics/overview").param("status", "SUCCESSFUL"))
                .andExpect(status().isOk());

        verify(analytics).overview(argThat(f -> "SUCCESSFUL".equals(f.status())));
    }

    @Test
    void overview_invalidStatus_returns400() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview").param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overview_startDateAfterEndDate_returns400() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview")
                        .param("from", "2025-06-30")
                        .param("to", "2025-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overview_rangeTooLarge_returns400() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview")
                        .param("from", "2020-01-01")
                        .param("to", "2025-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overview_withCurrencyFilter_passedToFilter() throws Exception {
        when(analytics.overview(any())).thenReturn(buildOverview());

        mvc.perform(get("/api/v1/analytics/overview").param("currency", "EUR"))
                .andExpect(status().isOk());

        verify(analytics).overview(argThat(f -> "EUR".equals(f.currency())));
    }

    @Test
    void overview_withCustomerId_passedToFilter() throws Exception {
        when(analytics.overview(any())).thenReturn(buildOverview());

        mvc.perform(get("/api/v1/analytics/overview").param("customerId", "42"))
                .andExpect(status().isOk());

        verify(analytics).overview(argThat(f -> f.customerId() != null && f.customerId() == 42L));
    }

    // ── GET /api/v1/analytics/recent-transactions ────────────────────────────

    @Test
    void recent_noParams_returns200WithPage() throws Exception {
        AnalyticsResponse.RecentTransaction tx = new AnalyticsResponse.RecentTransaction(
                "PAYMENT", 1L, 10L, "Alice", "1234",
                new BigDecimal("100.00"), "USD", "Bank transfer",
                "SUCCESSFUL", Instant.now(), null);
        PageResponse<AnalyticsResponse.RecentTransaction> page = new PageResponse<>(
                List.of(tx), 0, 10, 1L, 1);
        when(analytics.recent(any(), eq(0), eq(10))).thenReturn(page);

        mvc.perform(get("/api/v1/analytics/recent-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].customerName").value("Alice"));
    }

    @Test
    void recent_withPagination_passedToService() throws Exception {
        when(analytics.recent(any(), eq(2), eq(5)))
                .thenReturn(new PageResponse<>(List.of(), 2, 5, 0L, 0));

        mvc.perform(get("/api/v1/analytics/recent-transactions")
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(analytics).recent(any(), eq(2), eq(5));
    }

    @Test
    void recent_withStatusFilter_passedToFilter() throws Exception {
        when(analytics.recent(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0L, 0));

        mvc.perform(get("/api/v1/analytics/recent-transactions").param("status", "FAILED"))
                .andExpect(status().isOk());

        verify(analytics).recent(argThat(f -> "FAILED".equals(f.status())), anyInt(), anyInt());
    }

    // ── GET /api/v1/analytics/exchange-rates ─────────────────────────────────

    @Test
    void exchangeRates_validPair_returns200() throws Exception {
        AnalyticsResponse.ExchangeRateHistory history = new AnalyticsResponse.ExchangeRateHistory(
                "USD", "EUR", new BigDecimal("1.25"), new BigDecimal("1.30"),
                new BigDecimal("1.20"), new BigDecimal("5.00"), List.of());
        when(analytics.exchangeRates(eq("USD"), eq("EUR"), any(), any())).thenReturn(history);

        mvc.perform(get("/api/v1/analytics/exchange-rates")
                        .param("sourceCurrency", "USD")
                        .param("targetCurrency", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("EUR"))
                .andExpect(jsonPath("$.currentRate").value(1.25));
    }

    @Test
    void exchangeRates_sameCurrency_returns400() throws Exception {
        when(analytics.exchangeRates(eq("USD"), eq("USD"), any(), any()))
                .thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                        "Source and target currencies must be different"));

        mvc.perform(get("/api/v1/analytics/exchange-rates")
                        .param("sourceCurrency", "USD")
                        .param("targetCurrency", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void exchangeRates_withDateRange_passedToService() throws Exception {
        when(analytics.exchangeRates(any(), any(), any(), any()))
                .thenReturn(new AnalyticsResponse.ExchangeRateHistory(
                        "GBP", "USD", null, null, null, null, List.of()));

        mvc.perform(get("/api/v1/analytics/exchange-rates")
                        .param("sourceCurrency", "GBP")
                        .param("targetCurrency", "USD")
                        .param("from", "2025-01-01")
                        .param("to", "2025-03-31"))
                .andExpect(status().isOk());

        verify(analytics).exchangeRates(eq("GBP"), eq("USD"), any(), any());
    }

    @Test
    void exchangeRates_missingSourceCurrency_returns400() throws Exception {
        mvc.perform(get("/api/v1/analytics/exchange-rates")
                        .param("targetCurrency", "EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AnalyticsResponse.Overview buildOverview() {
        return new AnalyticsResponse.Overview(
                Instant.now(), "UTC", "USD", "DAILY", 0L,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                new AnalyticsResponse.FilterOptions(List.of(), List.of(), List.of(), List.of(), List.of()));
    }
}
