package com.godsplan.payments;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedAccounts() {
        jdbc.update("DELETE FROM payment_status_history");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("INSERT INTO accounts (id, account_number, currency, active, created_at) VALUES (1, 'ACC-0001', 'USD', true, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, currency, active, created_at) VALUES (2, 'ACC-0002', 'USD', true, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, currency, active, created_at) VALUES (3, 'ACC-0003', 'EUR', true, CURRENT_TIMESTAMP)");
    }

    @Test
    void createsCompletesAndAuditsPayment() throws Exception {
        String response = mvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "IK-HAPPY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("125.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(125.50))
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":(\\d+).*", "$1");

        mvc.perform(get("/api/v1/payments/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history", hasSize(4)))
                .andExpect(jsonPath("$.history[0].toStatus").value("CREATED"))
                .andExpect(jsonPath("$.history[3].toStatus").value("COMPLETED"));
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingPayment() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-RETRY")
                        .contentType(MediaType.APPLICATION_JSON).content(request("10.00")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-RETRY")
                        .contentType(MediaType.APPLICATION_JSON).content(request("999.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(10.00));
    }

    @Test
    void businessValidationFailureIsAnAuditableResource() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-INVALID")
                        .contentType(MediaType.APPLICATION_JSON).content(request("-1.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"));
    }

    @Test
    void malformedAmountAndMissingHeaderUseStableErrorEnvelope() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-BAD-NUMBER")
                        .contentType(MediaType.APPLICATION_JSON).content(request("\"abc\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(request("10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsInvalidTransitionWithoutChangingCompletedPayment() throws Exception {
        String response = mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-TRANSITION")
                        .contentType(MediaType.APPLICATION_JSON).content(request("5.00")))
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\\\"id\\\":(\\d+).*", "$1");
        mvc.perform(patch("/api/v1/payments/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"CREATED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        mvc.perform(get("/api/v1/payments/{id}", id)).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private String request(String amount) {
        return "{\"amount\":" + amount + ",\"currency\":\"USD\",\"sourceAccountId\":1,"
                + "\"destinationAccountId\":2,\"reference\":\"Test payment\"}";
    }
}

