package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.CustomerResponse;
import com.godsplan.payments.api.dto.CustomerTransactionResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.domain.PaymentStatus;
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
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@WithMockUser
class CustomerControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private CustomerService customers;

    // ── GET /api/v1/customers ─────────────────────────────────────────────────

    @Test
    void list_returnsPageOfCustomers() throws Exception {
        CustomerResponse customer = new CustomerResponse(
                1L, "Alice Smith", "alice@example.com", "1234", "Visa", List.of());
        PageResponse<CustomerResponse> page = new PageResponse<>(
                List.of(customer), 0, 8, 1L, 1);
        when(customers.list(any())).thenReturn(page);

        mvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_emptyResult_returnsEmptyPage() throws Exception {
        when(customers.list(any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 8, 0L, 0));

        mvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void list_pageAndSizeParams_passedToService() throws Exception {
        when(customers.list(any())).thenReturn(new PageResponse<>(List.of(), 2, 5, 0L, 0));

        mvc.perform(get("/api/v1/customers").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(customers).list(argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 5));
    }

    @Test
    void list_negativePage_clampsToZero() throws Exception {
        when(customers.list(any())).thenReturn(new PageResponse<>(List.of(), 0, 8, 0L, 0));

        mvc.perform(get("/api/v1/customers").param("page", "-3"))
                .andExpect(status().isOk());

        verify(customers).list(argThat(p -> p.getPageNumber() == 0));
    }

    @Test
    void list_sizeExceedsMax_clampsTo50() throws Exception {
        when(customers.list(any())).thenReturn(new PageResponse<>(List.of(), 0, 50, 0L, 0));

        mvc.perform(get("/api/v1/customers").param("size", "500"))
                .andExpect(status().isOk());

        verify(customers).list(argThat(p -> p.getPageSize() == 50));
    }

    @Test
    void list_sizeZero_clampsTo1() throws Exception {
        when(customers.list(any())).thenReturn(new PageResponse<>(List.of(), 0, 1, 0L, 0));

        mvc.perform(get("/api/v1/customers").param("size", "0"))
                .andExpect(status().isOk());

        verify(customers).list(argThat(p -> p.getPageSize() == 1));
    }

    // ── GET /api/v1/customers/{id}/transactions ───────────────────────────────

    @Test
    void transactions_validCustomer_returnsTransactionPage() throws Exception {
        CustomerTransactionResponse tx = new CustomerTransactionResponse(
                10L, new BigDecimal("100.00"), "USD",
                Instant.now(), "Bank transfer", PaymentStatus.COMPLETED, "SUCCESSFUL");
        PageResponse<CustomerTransactionResponse> page = new PageResponse<>(
                List.of(tx), 0, 5, 1L, 1);
        when(customers.transactions(eq(1L), any())).thenReturn(page);

        mvc.perform(get("/api/v1/customers/1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].transactionId").value(10));
    }

    @Test
    void transactions_customerNotFound_returns404() throws Exception {
        when(customers.transactions(eq(99L), any()))
                .thenThrow(new ApiException(ErrorCode.CUSTOMER_NOT_FOUND,
                        HttpStatus.NOT_FOUND, "Customer 99 was not found"));

        mvc.perform(get("/api/v1/customers/99/transactions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void transactions_paginationApplied() throws Exception {
        when(customers.transactions(eq(1L), any()))
                .thenReturn(new PageResponse<>(List.of(), 1, 5, 0L, 0));

        mvc.perform(get("/api/v1/customers/1/transactions")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        verify(customers).transactions(eq(1L), argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5));
    }

    @Test
    void transactions_sizeExceedsMax_clampsTo50() throws Exception {
        when(customers.transactions(eq(1L), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0L, 0));

        mvc.perform(get("/api/v1/customers/1/transactions").param("size", "1000"))
                .andExpect(status().isOk());

        verify(customers).transactions(eq(1L), argThat(p -> p.getPageSize() == 50));
    }
}
