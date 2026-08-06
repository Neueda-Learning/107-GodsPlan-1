package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.PaymentAccountOptionResponse;
import com.godsplan.payments.api.dto.PaymentCustomerOptionResponse;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentOptionsController.class)
@WithMockUser
class PaymentOptionsControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private CustomerService customers;

    // ── GET /api/v1/payment-options/customers ────────────────────────────────

    @Test
    void customers_returnsListOfCustomerOptions() throws Exception {
        List<PaymentCustomerOptionResponse> options = List.of(
                new PaymentCustomerOptionResponse(1L, "Alice Smith", "US"),
                new PaymentCustomerOptionResponse(2L, "Bob Jones", "GB"));
        when(customers.paymentOptions()).thenReturn(options);

        mvc.perform(get("/api/v1/payment-options/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Alice Smith"))
                .andExpect(jsonPath("$[0].country").value("US"));
    }

    @Test
    void customers_emptyList_returnsEmptyArray() throws Exception {
        when(customers.paymentOptions()).thenReturn(List.of());

        mvc.perform(get("/api/v1/payment-options/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /api/v1/payment-options/customers/{id}/accounts ──────────────────

    @Test
    void accounts_validCustomer_returnsAccountList() throws Exception {
        List<PaymentAccountOptionResponse> options = List.of(
                new PaymentAccountOptionResponse(10L, "Checking Account", "ACC-001",
                        "USD", new BigDecimal("1000.00"),
                        "Checking Account · ACC-001 · USD"));
        when(customers.accountOptions(1L)).thenReturn(options);

        mvc.perform(get("/api/v1/payment-options/customers/1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].availableBalance").value(1000.00));
    }

    @Test
    void accounts_customerNotFound_returns404() throws Exception {
        when(customers.accountOptions(99L))
                .thenThrow(new ApiException(ErrorCode.CUSTOMER_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Customer 99 was not found"));

        mvc.perform(get("/api/v1/payment-options/customers/99/accounts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void accounts_noActiveAccounts_returnsEmptyList() throws Exception {
        when(customers.accountOptions(1L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/payment-options/customers/1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /api/v1/payment-options/customers/{id}/accounts/{accountId} ───────

    @Test
    void account_validCustomerAndAccount_returnsAccountOption() throws Exception {
        PaymentAccountOptionResponse option = new PaymentAccountOptionResponse(
                10L, "Savings Account", "ACC-002", "EUR", new BigDecimal("500.00"),
                "Savings Account · ACC-002 · EUR");
        when(customers.accountOption(1L, 10L)).thenReturn(option);

        mvc.perform(get("/api/v1/payment-options/customers/1/accounts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.accountType").value("Savings Account"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.label").value("Savings Account · ACC-002 · EUR"));
    }

    @Test
    void account_accountNotFound_returns404() throws Exception {
        when(customers.accountOption(1L, 99L))
                .thenThrow(new ApiException(ErrorCode.INVALID_ACCOUNT,
                        HttpStatus.NOT_FOUND, "The selected account does not exist or is inactive"));

        mvc.perform(get("/api/v1/payment-options/customers/1/accounts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT"));
    }

    @Test
    void account_customerNotFound_returns404() throws Exception {
        when(customers.accountOption(99L, 10L))
                .thenThrow(new ApiException(ErrorCode.CUSTOMER_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Customer 99 was not found"));

        mvc.perform(get("/api/v1/payment-options/customers/99/accounts/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }
}
