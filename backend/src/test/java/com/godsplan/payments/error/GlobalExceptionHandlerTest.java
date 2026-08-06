package com.godsplan.payments.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for GlobalExceptionHandler — each test exercises a distinct exception type
 * by hitting ExceptionHandlerProbeController in the same MVC slice.
 */
@WebMvcTest(ExceptionHandlerProbeController.class)
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mvc;

    // ── ApiException → correct status + code ─────────────────────────────────

    @Test
    void apiException_returnsCorrectStatusAndCode() throws Exception {
        mvc.perform(get("/test-probe/api-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Payment not found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ── MethodArgumentNotValidException → 400 with field errors ──────────────

    @Test
    void methodArgumentNotValid_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/test-probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("value"));
    }

    @Test
    void methodArgumentNotValid_nullBody_returns400() throws Exception {
        mvc.perform(post("/test-probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── MissingServletRequestParameterException → 400 ────────────────────────

    @Test
    void missingRequiredParam_returns400WithParamName() throws Exception {
        mvc.perform(get("/test-probe/missing-param")) // 'required' param absent
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Required parameter 'required' is missing"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("required"));
    }

    // ── MethodArgumentTypeMismatchException → 400 ────────────────────────────

    @Test
    void typeMismatch_returns400WithParamName() throws Exception {
        mvc.perform(get("/test-probe/type-mismatch").param("num", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("num")));
    }

    // ── HttpMessageNotReadableException → 400 ────────────────────────────────

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/test-probe/unreadable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── Unhandled exception → 500 with traceId ────────────────────────────────

    @Test
    void unhandledException_returns500WithTraceId() throws Exception {
        // BusinessFailure is not a registered @ExceptionHandler, so it falls to catch-all
        mvc.perform(get("/test-probe/business-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PROCESSING_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ── ApiError structure ────────────────────────────────────────────────────

    @Test
    void apiError_alwaysIncludesPathAndTimestamp() throws Exception {
        mvc.perform(get("/test-probe/api-exception"))
                .andExpect(jsonPath("$.path").value("/test-probe/api-exception"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
