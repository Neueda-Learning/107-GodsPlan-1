package com.godsplan.payments.service;

import com.godsplan.payments.config.AnalyticsProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsSeedService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsSeedService.class);
    private static final Set<String> ALLOWED_ENVIRONMENTS = Set.of(
            "development", "dev", "local", "test", "staging", "demo");
    private static final int MEANINGFUL_PAYMENT_COUNT = 60;
    private static final String SEED_SOURCE = "analytics-demo-seed";

    private final JdbcTemplate jdbc;
    private final AnalyticsProperties properties;
    private final Clock clock;

    @Autowired
    public AnalyticsSeedService(JdbcTemplate jdbc, AnalyticsProperties properties) {
        this(jdbc, properties, Clock.systemUTC());
    }

    AnalyticsSeedService(JdbcTemplate jdbc, AnalyticsProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SeedResult seed() {
        ensureAllowed();
        int rates = seedExchangeRates();
        long before = count("SELECT COUNT(*) FROM payments");
        int customers = 0;
        int payments = 0;
        int refunds = 0;
        if (before < MEANINGFUL_PAYMENT_COUNT) {
            List<SeedAccount> accounts = seedCustomersAndAccounts();
            customers = accounts.size();
            SeedCounts counts = seedPayments(accounts);
            payments = counts.payments;
            refunds = counts.refunds;
        } else {
            log.info("Analytics payment seed skipped: database already contains {} payments", before);
        }
        return new SeedResult(customers, payments, refunds, rates, count("SELECT COUNT(*) FROM payments"));
    }

    void ensureAllowed() {
        String environment = properties.environment() == null ? "" : properties.environment().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ENVIRONMENTS.contains(environment)) {
            throw new IllegalStateException("Analytics demonstration seeding is disabled in environment '"
                    + (environment.isBlank() ? "unspecified" : environment) + "'");
        }
    }

    private List<SeedAccount> seedCustomersAndAccounts() {
        String[] names = {"Aarav Demo", "Diya Demo", "Kabir Demo", "Ishita Demo",
                "Arjun Demo", "Maya Demo", "Reyansh Demo", "Zoya Demo"};
        String[] currencies = {"USD", "EUR", "INR", "GBP", "USD", "EUR", "INR", "GBP"};
        String[] lastFour = {"1101", "2202", "3303", "4404", "5505", "6606", "7707", "8808"};
        List<SeedAccount> accounts = new ArrayList<>();
        Instant now = clock.instant();
        for (int index = 0; index < names.length; index++) {
            String email = String.format("analytics.demo%02d@example.test", index + 1);
            if (!exists("SELECT COUNT(*) FROM customer_users WHERE email = ?", email)) {
                jdbc.update("INSERT INTO customer_users (full_name, email, country, role, active, created_at) VALUES (?, ?, 'India', 'CUSTOMER', TRUE, ?)",
                        names[index], email, Timestamp.from(now.minus(210L - index * 12L, ChronoUnit.DAYS)));
            }
            Long customerId = jdbc.queryForObject("SELECT id FROM customer_users WHERE email = ?", Long.class, email);
            String accountNumber = String.format("ANL-%04d", index + 1);
            if (!exists("SELECT COUNT(*) FROM accounts WHERE account_number = ?", accountNumber)) {
                jdbc.update("INSERT INTO accounts (account_number, account_type, currency, active, customer_id, created_at) VALUES (?, ?, ?, TRUE, ?, ?)",
                        accountNumber, index % 2 == 0 ? "Checking Account" : "Savings Account", currencies[index], customerId,
                        Timestamp.from(now.minus(200L - index * 10L, ChronoUnit.DAYS)));
            }
            Long accountId = jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", Long.class, accountNumber);
            if (!exists("SELECT COUNT(*) FROM payment_cards WHERE customer_id = ? AND last_four = ?", customerId, lastFour[index])) {
                jdbc.update("INSERT INTO payment_cards (customer_id, account_id, brand, last_four, expiry_month, expiry_year, active, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)", customerId, accountId,
                        index % 2 == 0 ? "Visa" : "Mastercard", lastFour[index], 1 + index, 2030,
                        Timestamp.from(now.minus(190L - index * 9L, ChronoUnit.DAYS)));
            }
            accounts.add(new SeedAccount(customerId, accountId, currencies[index]));
        }
        return accounts;
    }

    private SeedCounts seedPayments(List<SeedAccount> accounts) {
        String[] methods = {"Credit card", "Debit card", "Bank transfer", "Wallet", "UPI"};
        String[] failureCodes = {"ISSUER_DECLINED", "INSUFFICIENT_FUNDS", "PROCESSOR_TIMEOUT", "INVALID_ACCOUNT"};
        String[] failureReasons = {"The issuer declined the payment", "Insufficient funds",
                "The processor timed out", "The destination account was unavailable"};
        Long destinationId = jdbc.queryForObject("SELECT id FROM accounts ORDER BY id LIMIT 1", Long.class);
        Instant now = clock.instant();
        int insertedPayments = 0;
        int insertedRefunds = 0;
        for (int index = 0; index < 120; index++) {
            String key = String.format("analytics-demo-payment-%03d", index + 1);
            if (exists("SELECT COUNT(*) FROM payments WHERE idempotency_key = ?", key)) continue;
            SeedAccount account = accounts.get(index % accounts.size());
            BigDecimal amount = BigDecimal.valueOf(35L + (index * 47L) % 2400L)
                    .add(BigDecimal.valueOf(index % 100, 2)).setScale(2, RoundingMode.HALF_UP);
            int statusIndex = index % 10;
            String status = statusIndex <= 5 ? "COMPLETED" : statusIndex <= 7 ? "FAILED" : statusIndex == 8 ? "SENT" : "CREATED";
            String errorCode = null;
            String errorDescription = null;
            if ("FAILED".equals(status)) {
                int reason = index % failureCodes.length;
                errorCode = failureCodes[reason];
                errorDescription = failureReasons[reason];
            }
            Instant created = now.minus(index % 120L, ChronoUnit.DAYS)
                    .minus((index * 7L) % 24L, ChronoUnit.HOURS)
                    .minus((index * 13L) % 60L, ChronoUnit.MINUTES);
            jdbc.update("INSERT INTO payments (idempotency_key, amount, currency, source_account_id, destination_account_id, "
                            + "reference, status, error_code, error_description, payment_method, created_at, updated_at, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)", key, amount, account.currency,
                    account.accountId, destinationId, "Analytics demonstration transaction", status,
                    errorCode, errorDescription, methods[index % methods.length], Timestamp.from(created), Timestamp.from(created));
            Long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE idempotency_key = ?", Long.class, key);
            seedHistory(paymentId, status, errorCode, errorDescription, created);
            insertedPayments++;
            if ("COMPLETED".equals(status) && index % 17 == 0) {
                String refundKey = String.format("analytics-demo-refund-%03d", index + 1);
                if (!exists("SELECT COUNT(*) FROM refunds WHERE idempotency_key = ?", refundKey)) {
                    jdbc.update("INSERT INTO refunds (idempotency_key, payment_id, amount, currency, status, reason, created_at) "
                                    + "VALUES (?, ?, ?, ?, 'COMPLETED', ?, ?)", refundKey, paymentId,
                            amount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP), account.currency,
                            "Customer-requested partial refund", Timestamp.from(created.plus(2, ChronoUnit.DAYS)));
                    insertedRefunds++;
                }
            }
        }
        return new SeedCounts(insertedPayments, insertedRefunds);
    }

    private void seedHistory(Long paymentId, String status, String errorCode, String errorDescription, Instant created) {
        insertHistory(paymentId, null, "CREATED", null, null, created);
        if ("CREATED".equals(status)) return;
        insertHistory(paymentId, "CREATED", "VALIDATED", null, null, created.plus(1, ChronoUnit.MINUTES));
        if ("FAILED".equals(status)) {
            insertHistory(paymentId, "VALIDATED", "FAILED", errorCode, errorDescription, created.plus(2, ChronoUnit.MINUTES));
            return;
        }
        insertHistory(paymentId, "VALIDATED", "SENT", null, null, created.plus(2, ChronoUnit.MINUTES));
        if ("COMPLETED".equals(status)) {
            insertHistory(paymentId, "SENT", "COMPLETED", null, null, created.plus(3, ChronoUnit.MINUTES));
        }
    }

    private void insertHistory(Long paymentId, String from, String to, String errorCode,
                               String errorDescription, Instant created) {
        jdbc.update("INSERT INTO payment_status_history (payment_id, from_status, to_status, error_code, error_description, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", paymentId, from, to, errorCode, errorDescription, Timestamp.from(created));
    }

    private int seedExchangeRates() {
        record Pair(String base, String quote, BigDecimal rate) {}
        List<Pair> pairs = List.of(
                new Pair("EUR", "USD", new BigDecimal("1.08000000")),
                new Pair("INR", "USD", new BigDecimal("0.01200000")),
                new Pair("GBP", "USD", new BigDecimal("1.27000000")),
                new Pair("USD", "EUR", new BigDecimal("0.92590000")),
                new Pair("USD", "INR", new BigDecimal("83.00000000")),
                new Pair("USD", "GBP", new BigDecimal("0.78740000")));
        Instant now = clock.instant().truncatedTo(ChronoUnit.DAYS);
        int inserted = 0;
        for (int day = 0; day <= 120; day++) {
            Instant fetched = now.minus(day, ChronoUnit.DAYS);
            for (Pair pair : pairs) {
                if (exists("SELECT COUNT(*) FROM exchange_rate_history WHERE base_currency = ? AND quote_currency = ? "
                        + "AND source = ? AND fetched_at = ?", pair.base, pair.quote, SEED_SOURCE, Timestamp.from(fetched))) continue;
                BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf((day % 11L) - 5L, 4));
                BigDecimal rate = pair.rate.multiply(factor).setScale(8, RoundingMode.HALF_UP);
                jdbc.update("INSERT INTO exchange_rate_history (base_currency, quote_currency, rate, source, fetched_at, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)", pair.base, pair.quote, rate, SEED_SOURCE,
                        Timestamp.from(fetched), Timestamp.from(clock.instant()));
                inserted++;
            }
        }
        return inserted;
    }

    private boolean exists(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count != null && count > 0;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    public record SeedResult(int customersEnsured, int paymentsInserted, int refundsInserted,
                             int exchangeRatesInserted, long totalPayments) {}
    private record SeedAccount(Long customerId, Long accountId, String currency) {}
    private record SeedCounts(int payments, int refunds) {}
}
