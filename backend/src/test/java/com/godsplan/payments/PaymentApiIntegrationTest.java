package com.godsplan.payments;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.security.test.context.support.WithMockUser;
import com.godsplan.payments.config.AnalyticsProperties;
import com.godsplan.payments.service.AnalyticsSeedService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalyticsSeedService analyticsSeeds;

    @BeforeEach
    void seedAccounts() {
        jdbc.update("DELETE FROM refunds");
        jdbc.update("DELETE FROM exchange_rate_history");
        jdbc.update("DELETE FROM payment_cards");
        jdbc.update("DELETE FROM payment_status_history");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM customer_users");
        jdbc.update("INSERT INTO customer_users (id, full_name, email, role, active, created_at) VALUES (1, 'Test Staff', 'staff@godsplan.local', 'ADMIN', true, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO customer_users (id, full_name, email, role, active, created_at) VALUES (2, 'Test Customer', 'customer@example.com', 'CUSTOMER', true, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, currency, active, customer_id, created_at) VALUES (1, 'ACC-0001', 'USD', true, 1, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, currency, active, customer_id, created_at) VALUES (2, 'ACC-0002', 'USD', true, 2, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, currency, active, customer_id, created_at) VALUES (3, 'ACC-0003', 'EUR', true, 2, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO payment_cards (id, customer_id, account_id, brand, last_four, expiry_month, expiry_year, active, created_at) VALUES (1, 2, 2, 'Visa', '1234', 8, 2029, true, CURRENT_TIMESTAMP)");
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

    @Test
    void customerDataRequiresStaffAuthentication() throws Exception {
        mvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void analyticsRequiresStaffAuthentication() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "staff@godsplan.local", roles = "ADMIN")
    void analyticsCalculationsFiltersAndPrivacyMatchDatabaseRecords() throws Exception {
        Instant created = Instant.now().minusSeconds(3600);
        long completed = analyticsPayment("ANALYTICS-COMPLETED", "100.00", "USD", "COMPLETED", null, null, created);
        analyticsPayment("ANALYTICS-FAILED", "200.00", "USD", "FAILED", "ISSUER_DECLINED", "Issuer declined", created);
        analyticsPayment("ANALYTICS-PENDING", "300.00", "USD", "SENT", null, null, created);
        jdbc.update("INSERT INTO refunds (idempotency_key, payment_id, amount, currency, status, reason, created_at) "
                        + "VALUES ('ANALYTICS-REFUND', ?, 50.00, 'USD', 'COMPLETED', 'Requested refund', ?)",
                completed, Timestamp.from(created.plusSeconds(60)));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        mvc.perform(get("/api/v1/analytics/overview")
                        .param("from", today.toString()).param("to", today.toString()).param("baseCurrency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis[0].value").value(3.00))
                .andExpect(jsonPath("$.kpis[1].value").value(0.00))
                .andExpect(jsonPath("$.kpis[2].value").value(1.00))
                .andExpect(jsonPath("$.kpis[3].value").value(1.00))
                .andExpect(jsonPath("$.kpis[4].value").value(1.00))
                .andExpect(jsonPath("$.kpis[5].value").value(600.00))
                .andExpect(jsonPath("$.kpis[7].value").value(50.00))
                .andExpect(jsonPath("$.kpis[9].value").value(50.00))
                .andExpect(jsonPath("$.paymentStatus", hasSize(4)))
                .andExpect(jsonPath("$.failureReasons[0].code").value("ISSUER_DECLINED"));

        mvc.perform(get("/api/v1/analytics/overview").param("from", today.toString())
                        .param("to", today.toString()).param("status", "FAILED").param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis[0].value").value(1.00))
                .andExpect(jsonPath("$.kpis[2].value").value(1.00));

        mvc.perform(get("/api/v1/analytics/recent-transactions").param("from", today.toString())
                        .param("to", today.toString()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].maskedCardNumber").value("XXXX XXXX XXXX 1234"))
                .andExpect(jsonPath("$.content[0].lastFour").doesNotExist())
                .andExpect(jsonPath("$.content[0].cvv").doesNotExist())
                .andExpect(jsonPath("$.content[0].paymentToken").doesNotExist());
    }

    @Test
    @WithMockUser(username = "staff@godsplan.local", roles = "ADMIN")
    void analyticsReturnsRealEmptyResultsForAnEmptyPeriod() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview").param("from", "2020-01-01").param("to", "2020-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis[0].value").value(0.00))
                .andExpect(jsonPath("$.transactionsOverTime", hasSize(7)));
    }

    @Test
    void analyticsSeedIsIdempotentAndRefusesProduction() {
        analyticsSeeds.seed();
        long first = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE idempotency_key LIKE 'analytics-demo-payment-%'", Long.class);
        analyticsSeeds.seed();
        long second = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE idempotency_key LIKE 'analytics-demo-payment-%'", Long.class);
        assertEquals(120, first);
        assertEquals(first, second);

        var production = new AnalyticsSeedService(jdbc, new AnalyticsProperties(
                "production", "UTC", 30, 1826, 100, 250000, "USD"));
        assertThrows(IllegalStateException.class, production::seed);
    }

    @Test
    @WithMockUser(username = "staff@godsplan.local", roles = "ADMIN")
    void returnsOtherCustomersWithBackendMaskedCardsAndPaymentHistory() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-CUSTOMER-HISTORY")
                        .contentType(MediaType.APPLICATION_JSON).content(request("42.00")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Test Customer"))
                .andExpect(jsonPath("$.content[0].maskedCardNumber").value("XXXX XXXX XXXX 1234"))
                .andExpect(jsonPath("$.content[0].lastFour").doesNotExist());

        mvc.perform(get("/api/v1/customers/2/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].outcome").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("Bank transfer"));
    }

    private String request(String amount) {
        return "{\"amount\":" + amount + ",\"currency\":\"USD\",\"sourceAccountId\":1,"
                + "\"destinationAccountId\":2,\"reference\":\"Test payment\"}";
    }

    private long analyticsPayment(String key, String amount, String currency, String status,
                                  String errorCode, String errorDescription, Instant created) {
        java.math.BigDecimal testAmount = new java.math.BigDecimal(amount);
        java.math.BigDecimal testFee = testAmount.multiply(new java.math.BigDecimal("0.02")).setScale(2, java.math.RoundingMode.HALF_EVEN);
        java.math.BigDecimal testTotal = testAmount.add(testFee);
        jdbc.update("INSERT INTO payments (idempotency_key, amount, fee_amount, total_debit_amount, currency, source_account_id, destination_account_id, "
                        + "reference, status, error_code, error_description, payment_method, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, ?, 2, 1, 'Analytics test', ?, ?, ?, 'Credit card', ?, ?, 0)",
                key, testAmount, testFee, testTotal, currency, status, errorCode, errorDescription,
                Timestamp.from(created), Timestamp.from(created));
        return jdbc.queryForObject("SELECT id FROM payments WHERE idempotency_key = ?", Long.class, key);
    }
}
