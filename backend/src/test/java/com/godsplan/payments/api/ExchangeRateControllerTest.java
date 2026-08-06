package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.ExchangeRateResponse;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.service.RateQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeRateController.class)
@WithMockUser
class ExchangeRateControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private RateQueryService rates;

    // ── GET /api/v1/exchange-rates ────────────────────────────────────────────

    @Test
    void get_validCurrencyPair_returns200WithRate() throws Exception {
        ExchangeRateResponse response = new ExchangeRateResponse(
                "USD", "EUR", new BigDecimal("1.2500"), "exchangerate.host", Instant.now());
        when(rates.get("USD", "EUR")).thenReturn(response);

        mvc.perform(get("/api/v1/exchange-rates")
                        .param("base", "USD")
                        .param("quote", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("USD"))
                .andExpect(jsonPath("$.quote").value("EUR"))
                .andExpect(jsonPath("$.rate").value(1.25));
    }

    @Test
    void get_missingBaseParam_returns400() throws Exception {
        mvc.perform(get("/api/v1/exchange-rates").param("quote", "EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Required parameter 'base' is missing"));
    }

    @Test
    void get_missingQuoteParam_returns400() throws Exception {
        mvc.perform(get("/api/v1/exchange-rates").param("base", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void get_invalidBasePattern_returns400() throws Exception {
        // Pattern requires exactly 3 letters; digits or special chars are rejected
        mvc.perform(get("/api/v1/exchange-rates")
                        .param("base", "US1")
                        .param("quote", "EUR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_baseWithTwoLetters_returns400() throws Exception {
        mvc.perform(get("/api/v1/exchange-rates")
                        .param("base", "US")
                        .param("quote", "EUR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_baseWithFourLetters_returns400() throws Exception {
        mvc.perform(get("/api/v1/exchange-rates")
                        .param("base", "USDD")
                        .param("quote", "EUR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_exchangeRateUnavailable_returns503() throws Exception {
        when(rates.get("USD", "EUR"))
                .thenThrow(new ApiException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE, "Exchange rate service is unavailable"));

        mvc.perform(get("/api/v1/exchange-rates")
                        .param("base", "USD")
                        .param("quote", "EUR"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXCHANGE_RATE_UNAVAILABLE"));
    }

    @Test
    void get_lowercaseParams_delegatedToService() throws Exception {
        ExchangeRateResponse response = new ExchangeRateResponse(
                "USD", "GBP", new BigDecimal("0.75"), "test", Instant.now());
        when(rates.get("usd", "gbp")).thenReturn(response);

        mvc.perform(get("/api/v1/exchange-rates")
                        .param("base", "usd")
                        .param("quote", "gbp"))
                .andExpect(status().isOk());

        verify(rates).get("usd", "gbp");
    }
}
