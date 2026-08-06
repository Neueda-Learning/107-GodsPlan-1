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
import com.godsplan.payments.config.AnalyticsProperties;
import com.godsplan.payments.service.AnalyticsSeedService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        jdbc.update("INSERT INTO customer_users (id, full_name, email, country, role, active, created_at) VALUES (1, 'Test Staff', 'staff@godsplan.local', 'India', 'ADMIN', true, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO customer_users (id, full_name, email, country, role, active, created_at) VALUES (2, 'Test Customer', 'customer@example.com', 'India', 'CUSTOMER', true, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO customer_users (id, full_name, email, country, role, active, created_at) VALUES (3, 'Test Receiver', 'receiver@example.com', 'Singapore', 'CUSTOMER', true, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, account_type, currency, available_balance, active, customer_id, created_at) VALUES (1, 'ACC-0001', 'Checking Account', 'USD', 1000.00, true, 1, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, account_type, currency, available_balance, active, customer_id, created_at) VALUES (2, 'ACC-0002', 'Savings Account', 'USD', 1000.00, true, 2, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, account_type, currency, available_balance, active, customer_id, created_at) VALUES (3, 'ACC-0003', 'Checking Account', 'EUR', 1000.00, true, 2, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO accounts (id, account_number, account_type, currency, available_balance, active, customer_id, created_at) VALUES (4, 'ACC-0004', 'Savings Account', 'USD', 500.00, true, 3, CURRENT_TIMESTAMP)");
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
        assertEquals(0, new java.math.BigDecimal("874.50").compareTo(
                jdbc.queryForObject("SELECT available_balance FROM accounts WHERE id = 2", java.math.BigDecimal.class)));
        assertEquals(0, new java.math.BigDecimal("625.50").compareTo(
                jdbc.queryForObject("SELECT available_balance FROM accounts WHERE id = 4", java.math.BigDecimal.class)));
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
                .andExpect(jsonPath("$.content[0].cardNumber").value("1234"))
                .andExpect(jsonPath("$.content[0].cvv").doesNotExist())
                .andExpect(jsonPath("$.content[0].paymentToken").doesNotExist());
    }

    @Test
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
    void returnsOtherCustomersWithBackendUnmaskedCardsAndPaymentHistory() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-CUSTOMER-HISTORY")
                        .contentType(MediaType.APPLICATION_JSON).content(request("42.00")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].fullName").value("Test Customer"))
                .andExpect(jsonPath("$.content[0].cardNumber").value("1234"))
                .andExpect(jsonPath("$.content[0].accounts[0].accountNumber").value("ACC-0002"));

        mvc.perform(get("/api/v1/customers/2/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].outcome").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("Bank transfer"));
    }

    @Test
        void paymentOptionsArePublicDatabaseBackedAndAccountsAreUnmasked() throws Exception {
        mvc.perform(get("/api/v1/payment-options/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fullName").value("Test Customer"))
                .andExpect(jsonPath("$[0].country").value("India"));

        mvc.perform(get("/api/v1/payment-options/customers/2/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].label").value("Savings Account · ACC-0002 · USD"))
                .andExpect(jsonPath("$[0].accountNumber").value("ACC-0002"))
                .andExpect(jsonPath("$[0].availableBalance").value(1000.00));

        mvc.perform(get("/api/v1/payment-options/customers/2/accounts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(1000.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void rejectsInsufficientFundsWithoutChangingBalances() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-NO-FUNDS")
                        .contentType(MediaType.APPLICATION_JSON).content(request("1000.01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value(
                        "The selected account does not have sufficient funds to complete this transaction."));
        assertEquals(0, new java.math.BigDecimal("1000.00").compareTo(
                jdbc.queryForObject("SELECT available_balance FROM accounts WHERE id = 2", java.math.BigDecimal.class)));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE idempotency_key = 'IK-NO-FUNDS'", Long.class));
    }

    @Test
    void concurrentTransfersCannotOverdrawTheSenderAccount() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> concurrentPayment("IK-CONCURRENT-1", ready, start));
            var second = executor.submit(() -> concurrentPayment("IK-CONCURRENT-2", ready, start));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count());
            assertEquals(0, new java.math.BigDecimal("300.00").compareTo(
                    jdbc.queryForObject("SELECT available_balance FROM accounts WHERE id = 2", java.math.BigDecimal.class)));
            assertEquals(0, new java.math.BigDecimal("1200.00").compareTo(
                    jdbc.queryForObject("SELECT available_balance FROM accounts WHERE id = 4", java.math.BigDecimal.class)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsAnAccountThatDoesNotBelongToTheSubmittedCustomer() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "IK-OWNERSHIP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderCustomerId\":2,\"sourceAccountId\":1,\"receiverCustomerId\":1,"
                                + "\"destinationAccountId\":2,\"amount\":10,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT"));
    }

    private String request(String amount) {
        return "{\"senderCustomerId\":2,\"sourceAccountId\":2,\"receiverCustomerId\":3,"
                + "\"destinationAccountId\":4,\"amount\":" + amount + ",\"currency\":\"USD\","
                + "\"intermediaryBank\":\"Correspondent Bank\",\"reference\":\"Test payment\"}";
    }

    private int concurrentPayment(String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return mvc.perform(post("/api/v1/payments").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(request("700.00")))
                .andReturn().getResponse().getStatus();
    }

    private long analyticsPayment(String key, String amount, String currency, String status,
                                  String errorCode, String errorDescription, Instant created) {
        jdbc.update("INSERT INTO payments (idempotency_key, amount, currency, source_account_id, destination_account_id, "
                        + "reference, status, error_code, error_description, payment_method, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, 2, 1, 'Analytics test', ?, ?, ?, 'Credit card', ?, ?, 0)",
                key, new java.math.BigDecimal(amount), currency, status, errorCode, errorDescription,
                Timestamp.from(created), Timestamp.from(created));
        return jdbc.queryForObject("SELECT id FROM payments WHERE idempotency_key = ?", Long.class, key);
    }
}
