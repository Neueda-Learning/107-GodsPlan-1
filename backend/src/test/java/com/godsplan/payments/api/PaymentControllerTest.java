package com.godsplan.payments.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.godsplan.payments.api.dto.HistoryResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.api.dto.PaymentResponse;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.service.AuditService;
import com.godsplan.payments.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@WithMockUser
class PaymentControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PaymentService payments;
    @MockBean private AuditService audit;

    private static final String BASE_URL = "/api/v1/payments";

    // ── POST /api/v1/payments ─────────────────────────────────────────────────

    @Test
    void create_newPayment_returns201WithPaymentBody() throws Exception {
        PaymentResponse response = buildPaymentResponse(1L, PaymentStatus.COMPLETED);
        when(payments.create(eq("IK-001"), any()))
                .thenReturn(new PaymentService.CreateResult(response, true));

        mvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "IK-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/payments/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void create_idempotentReplay_returns200() throws Exception {
        PaymentResponse response = buildPaymentResponse(2L, PaymentStatus.COMPLETED);
        when(payments.create(eq("IK-REPLAY"), any()))
                .thenReturn(new PaymentService.CreateResult(response, false));

        mvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "IK-REPLAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void create_missingIdempotencyKeyHeader_returns400() throws Exception {
        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_missingRequiredFields_returns400() throws Exception {
        mvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "IK-BAD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_malformedAmount_returns400() throws Exception {
        mvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "IK-BAD-AMT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senderCustomerId":1,"sourceAccountId":1,
                                 "receiverCustomerId":2,"destinationAccountId":2,
                                 "amount":"not-a-number","currency":"USD"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_insufficientFunds_returns409() throws Exception {
        when(payments.create(eq("IK-INSUF"), any()))
                .thenThrow(new ApiException(ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.CONFLICT,
                        "Insufficient funds"));

        mvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "IK-INSUF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void create_currencyLengthConstraint_returns400() throws Exception {
        mvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "IK-CURR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senderCustomerId":1,"sourceAccountId":1,
                                 "receiverCustomerId":2,"destinationAccountId":2,
                                 "amount":100.00,"currency":"US"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── GET /api/v1/payments/{id} ─────────────────────────────────────────────

    @Test
    void getById_existingPayment_returns200() throws Exception {
        when(payments.get(1L)).thenReturn(buildPaymentResponse(1L, PaymentStatus.COMPLETED));

        mvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(payments.get(99L))
                .thenThrow(new ApiException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Payment 99 was not found"));

        mvc.perform(get(BASE_URL + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    // ── GET /api/v1/payments ──────────────────────────────────────────────────

    @Test
    void list_noParams_returns200WithPage() throws Exception {
        PageResponse<PaymentResponse> page = new PageResponse<>(
                List.of(buildPaymentResponse(1L, PaymentStatus.COMPLETED)), 0, 20, 1L, 1);
        when(payments.list(isNull(), any())).thenReturn(page);

        mvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_withStatusFilter_passesStatusToService() throws Exception {
        when(payments.list(eq(PaymentStatus.FAILED), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0));

        mvc.perform(get(BASE_URL).param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        verify(payments).list(eq(PaymentStatus.FAILED), any());
    }

    @Test
    void list_invalidStatusValue_returns400() throws Exception {
        mvc.perform(get(BASE_URL).param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_negativePage_clampsToZero() throws Exception {
        when(payments.list(isNull(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0));

        mvc.perform(get(BASE_URL).param("page", "-5"))
                .andExpect(status().isOk());

        // Verify the pageable passed has page 0 (clamped)
        verify(payments).list(isNull(), argThat(p -> p.getPageNumber() == 0));
    }

    @Test
    void list_oversizedPageSize_clampsTo1000() throws Exception {
        when(payments.list(isNull(), any())).thenReturn(new PageResponse<>(List.of(), 0, 1000, 0L, 0));

        mvc.perform(get(BASE_URL).param("size", "99999"))
                .andExpect(status().isOk());

        verify(payments).list(isNull(), argThat(p -> p.getPageSize() == 1000));
    }

    // ── GET /api/v1/payments/{id}/history ────────────────────────────────────

    @Test
    void history_existingPayment_returns200WithHistory() throws Exception {
        HistoryResponse historyResponse = new HistoryResponse(1L, List.of());
        when(audit.getHistory(1L)).thenReturn(historyResponse);

        mvc.perform(get(BASE_URL + "/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1))
                .andExpect(jsonPath("$.history", hasSize(0)));
    }

    @Test
    void history_paymentNotFound_returns404() throws Exception {
        when(audit.getHistory(99L))
                .thenThrow(new ApiException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Payment 99 was not found"));

        mvc.perform(get(BASE_URL + "/99/history"))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/v1/payments/{id}/status ───────────────────────────────────

    @Test
    void transition_validTransition_returns200() throws Exception {
        when(payments.transition(eq(1L), any()))
                .thenReturn(buildPaymentResponse(1L, PaymentStatus.FAILED));

        mvc.perform(patch(BASE_URL + "/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toStatus":"FAILED","errorCode":"PROCESSING_ERROR",
                                 "errorDescription":"Could not process"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void transition_missingToStatus_returns400() throws Exception {
        mvc.perform(patch(BASE_URL + "/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void transition_invalidStatusTransition_returns400() throws Exception {
        when(payments.transition(eq(1L), any()))
                .thenThrow(new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                        HttpStatus.BAD_REQUEST, "Cannot transition COMPLETED to SENT"));

        mvc.perform(patch(BASE_URL + "/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toStatus":"SENT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String validRequestBody() {
        return """
                {"senderCustomerId":1,"sourceAccountId":1,
                 "receiverCustomerId":2,"destinationAccountId":2,
                 "amount":100.00,"currency":"USD"}
                """;
    }

    private PaymentResponse buildPaymentResponse(Long id, PaymentStatus status) {
        return new PaymentResponse(id, status, new BigDecimal("100.00"), new BigDecimal("2.00"),
                "USD", 1L, 2L, "ACC-001", "ACC-002", null, null,
                null, null, null, null, null, null, null,
                Instant.now(), Instant.now());
    }
}
