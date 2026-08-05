package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.AnalyticsResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.config.AnalyticsProperties;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String OUTCOME = "CASE WHEN COALESCE(r.refunded, 0) = 1 THEN 'REFUNDED' "
            + "WHEN p.status = 'COMPLETED' THEN 'SUCCESSFUL' WHEN p.status = 'FAILED' THEN 'FAILED' ELSE 'PENDING' END";
    private static final String REFUND_JOIN = " LEFT JOIN (SELECT payment_id, "
            + "SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END) AS refund_amount, "
            + "MAX(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS refunded "
            + "FROM refunds GROUP BY payment_id) r ON r.payment_id = p.id ";

    private final NamedParameterJdbcTemplate jdbc;
    private final AnalyticsProperties properties;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc, AnalyticsProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse.Overview overview(AnalyticsFilter filter) {
        Grouping grouping = resolveGrouping(filter);
        Aggregate current = aggregate(filter, grouping, true);
        Aggregate previous = aggregate(filter.previousPeriod(), grouping, false);
        long currentCustomers = customerCount(filter.toExclusive(), filter.customerId());
        long previousCustomers = customerCount(filter.from(), filter.customerId());
        current.totalCustomers = currentCustomers;
        previous.totalCustomers = previousCustomers;
        buildCustomerGrowth(current, filter, grouping);
        return new AnalyticsResponse.Overview(Instant.now(), filter.zoneId().getId(), filter.baseCurrency(),
                grouping.name(), current.unconverted, kpis(current, previous), statusMetrics(current),
                ratePoints(current), transactionPoints(current), volumePoints(current), methodMetrics(current),
                currencyMetrics(current), customerGrowth(current), topCustomers(current), heatmap(current),
                failureMetrics(current), filterOptions());
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalyticsResponse.RecentTransaction> recent(AnalyticsFilter filter, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), properties.maxPageSize());
        QueryParts parts = queryParts(filter);
        String fromSql = " FROM payments p JOIN accounts a ON a.id = p.source_account_id "
                + "JOIN customer_users c ON c.id = a.customer_id " + REFUND_JOIN;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + fromSql + parts.where, parts.params, Long.class);
        parts.params.addValue("limit", safeSize).addValue("offset", safePage * safeSize);
        String sql = "SELECT p.id, c.id AS customer_id, c.full_name, "
                + "(SELECT pc.last_four FROM payment_cards pc WHERE pc.customer_id = c.id AND pc.active = TRUE ORDER BY pc.id LIMIT 1) last_four, "
                + "p.amount, p.currency, p.payment_method, " + OUTCOME + " outcome, p.created_at, p.error_description "
                + fromSql + parts.where + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset";
        List<AnalyticsResponse.RecentTransaction> content = jdbc.query(sql, parts.params, (rs, rowNum) ->
                new AnalyticsResponse.RecentTransaction(rs.getLong("id"), rs.getLong("customer_id"),
                        rs.getString("full_name"), mask(rs.getString("last_four")), rs.getBigDecimal("amount"),
                        rs.getString("currency"), rs.getString("payment_method"), rs.getString("outcome"),
                        instant(rs, "created_at"), rs.getString("error_description")));
        long count = total == null ? 0 : total;
        int pages = count == 0 ? 0 : (int) Math.ceil((double) count / safeSize);
        return new PageResponse<>(content, safePage, safeSize, count, pages);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse.ExchangeRateHistory exchangeRates(String source, String target,
                                                               LocalDate requestedFrom, LocalDate requestedTo) {
        String base = normalizeCurrency(source);
        String quote = normalizeCurrency(target);
        if (base.equals(quote)) throw invalid("Source and target currencies must be different");
        AnalyticsFilter dates = AnalyticsFilter.create(requestedFrom, requestedTo, null, null, null,
                null, null, null, properties.defaultBaseCurrency(), "DAILY", properties);
        var params = new MapSqlParameterSource().addValue("base", base).addValue("quote", quote)
                .addValue("from", Timestamp.from(dates.from())).addValue("to", Timestamp.from(dates.toExclusive()));
        List<AnalyticsResponse.ExchangeRatePoint> points = jdbc.query("SELECT fetched_at, rate, source "
                + "FROM exchange_rate_history WHERE base_currency = :base AND quote_currency = :quote "
                + "AND fetched_at >= :from AND fetched_at < :to ORDER BY fetched_at", params,
                (rs, rowNum) -> new AnalyticsResponse.ExchangeRatePoint(instant(rs, "fetched_at"),
                        rs.getBigDecimal("rate"), rs.getString("source")));
        if (points.isEmpty()) return new AnalyticsResponse.ExchangeRateHistory(base, quote, null, null, null, null, points);
        BigDecimal first = points.getFirst().rate();
        BigDecimal current = points.getLast().rate();
        BigDecimal high = points.stream().map(AnalyticsResponse.ExchangeRatePoint::rate).max(BigDecimal::compareTo).orElse(current);
        BigDecimal low = points.stream().map(AnalyticsResponse.ExchangeRatePoint::rate).min(BigDecimal::compareTo).orElse(current);
        BigDecimal change = first.signum() == 0 ? null : current.subtract(first).multiply(HUNDRED)
                .divide(first, 4, RoundingMode.HALF_UP);
        return new AnalyticsResponse.ExchangeRateHistory(base, quote, current, high, low, change, points);
    }

    private Aggregate aggregate(AnalyticsFilter filter, Grouping grouping, boolean detailed) {
        Aggregate aggregate = new Aggregate(filter, grouping);
        QueryParts parts = queryParts(filter);
        enforceRowLimit(parts);
        String rate = "(SELECT e.rate FROM exchange_rate_history e WHERE e.base_currency = p.currency "
                + "AND e.quote_currency = :baseCurrency AND e.fetched_at <= p.created_at "
                + "ORDER BY e.fetched_at DESC LIMIT 1)";
        String conversion = "CASE WHEN p.currency = :baseCurrency THEN p.amount ELSE p.amount * " + rate + " END";
        String refundConversion = "CASE WHEN COALESCE(r.refund_amount, 0) = 0 THEN 0 "
                + "WHEN p.currency = :baseCurrency THEN r.refund_amount ELSE r.refund_amount * " + rate + " END";
        String sql = "SELECT p.id, p.amount, p.currency, p.payment_method, p.error_code, p.error_description, "
                + "p.created_at, c.id customer_id, c.full_name, c.role customer_role, "
                + "(SELECT pc.last_four FROM payment_cards pc WHERE pc.customer_id = c.id AND pc.active = TRUE ORDER BY pc.id LIMIT 1) last_four, "
                + "(SELECT MIN(p2.created_at) FROM payments p2 JOIN accounts a2 ON a2.id = p2.source_account_id "
                + "WHERE a2.customer_id = c.id) first_payment_at, " + OUTCOME + " outcome, "
                + conversion + " normalized_amount, " + refundConversion + " refund_normalized_amount "
                + "FROM payments p JOIN accounts a ON a.id = p.source_account_id "
                + "JOIN customer_users c ON c.id = a.customer_id " + REFUND_JOIN + parts.where
                + " ORDER BY p.created_at, p.id";
        jdbc.query(sql, parts.params, (RowCallbackHandler) rs -> aggregate.accept(row(rs), detailed));
        return aggregate;
    }

    private QueryParts queryParts(AnalyticsFilter filter) {
        var params = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(filter.from()))
                .addValue("to", Timestamp.from(filter.toExclusive()))
                .addValue("baseCurrency", filter.baseCurrency());
        StringBuilder where = new StringBuilder(" WHERE p.created_at >= :from AND p.created_at < :to");
        if (filter.status() != null) {
            where.append(" AND ").append(OUTCOME).append(" = :status");
            params.addValue("status", filter.status());
        }
        if (filter.currency() != null) {
            where.append(" AND p.currency = :currency");
            params.addValue("currency", filter.currency());
        }
        if (filter.paymentMethod() != null) {
            where.append(" AND p.payment_method = :paymentMethod");
            params.addValue("paymentMethod", filter.paymentMethod());
        }
        if (filter.customerId() != null) {
            where.append(" AND c.id = :customerId");
            params.addValue("customerId", filter.customerId());
        }
        if (filter.minimumAmount() != null) {
            where.append(" AND p.amount >= :minimumAmount");
            params.addValue("minimumAmount", filter.minimumAmount());
        }
        if (filter.maximumAmount() != null) {
            where.append(" AND p.amount <= :maximumAmount");
            params.addValue("maximumAmount", filter.maximumAmount());
        }
        return new QueryParts(where.toString(), params);
    }

    private void enforceRowLimit(QueryParts parts) {
        String sql = "SELECT COUNT(*) FROM payments p JOIN accounts a ON a.id = p.source_account_id "
                + "JOIN customer_users c ON c.id = a.customer_id " + REFUND_JOIN + parts.where;
        Long rows = jdbc.queryForObject(sql, parts.params, Long.class);
        if (rows != null && rows > properties.maxQueryRows()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "The selected filters match too many records; choose a shorter date range");
        }
    }

    private PaymentRow row(ResultSet rs) throws SQLException {
        return new PaymentRow(rs.getLong("id"), rs.getBigDecimal("amount"), rs.getBigDecimal("normalized_amount"),
                rs.getBigDecimal("refund_normalized_amount"), rs.getString("currency"),
                rs.getString("payment_method"), rs.getString("outcome"), rs.getString("error_code"),
                rs.getString("error_description"), instant(rs, "created_at"), rs.getLong("customer_id"),
                rs.getString("full_name"), rs.getString("customer_role"), mask(rs.getString("last_four")),
                instant(rs, "first_payment_at"));
    }

    private void buildCustomerGrowth(Aggregate aggregate, AnalyticsFilter filter, Grouping grouping) {
        var params = new MapSqlParameterSource().addValue("to", Timestamp.from(filter.toExclusive()));
        String sql = "SELECT id, created_at FROM customer_users WHERE role = 'CUSTOMER' AND created_at < :to";
        if (filter.customerId() != null) {
            sql += " AND id = :customerId";
            params.addValue("customerId", filter.customerId());
        }
        List<CustomerCreated> customers = jdbc.query(sql, params, (rs, rowNum) ->
                new CustomerCreated(rs.getLong("id"), instant(rs, "created_at")));
        for (Bucket bucket : aggregate.buckets.values()) {
            Instant end = bucket.end(grouping, filter.zoneId()).toInstant();
            bucket.cumulativeCustomers = customers.stream().filter(c -> c.createdAt().isBefore(end)).count();
            bucket.newCustomers = customers.stream().filter(c -> !c.createdAt().isBefore(bucket.start.toInstant())
                    && c.createdAt().isBefore(end)).count();
        }
    }

    private long customerCount(Instant before, Long customerId) {
        var params = new MapSqlParameterSource().addValue("before", Timestamp.from(before));
        String sql = "SELECT COUNT(*) FROM customer_users WHERE role = 'CUSTOMER' AND created_at < :before";
        if (customerId != null) {
            sql += " AND id = :customerId";
            params.addValue("customerId", customerId);
        }
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private AnalyticsResponse.FilterOptions filterOptions() {
        List<String> currencies = jdbc.query("SELECT DISTINCT currency FROM payments ORDER BY currency",
                (rs, rowNum) -> rs.getString(1));
        List<String> methods = jdbc.query("SELECT DISTINCT payment_method FROM payments ORDER BY payment_method",
                (rs, rowNum) -> rs.getString(1));
        List<AnalyticsResponse.CustomerOption> customers = jdbc.query("SELECT id, full_name FROM customer_users "
                + "WHERE role = 'CUSTOMER' AND active = TRUE ORDER BY full_name",
                (rs, rowNum) -> new AnalyticsResponse.CustomerOption(rs.getLong(1), rs.getString(2)));
        return new AnalyticsResponse.FilterOptions(List.of("SUCCESSFUL", "FAILED", "PENDING", "REFUNDED"),
                currencies, methods, customers);
    }

    private List<AnalyticsResponse.Kpi> kpis(Aggregate current, Aggregate previous) {
        return List.of(
                kpi("totalTransactions", "Total transactions", bd(current.total), bd(previous.total), "count", current.spark("total")),
                kpi("successfulPayments", "Successful payments", bd(current.successful), bd(previous.successful), "count", current.spark("successful")),
                kpi("failedPayments", "Failed payments", bd(current.failed), bd(previous.failed), "count", current.spark("failed")),
                kpi("pendingPayments", "Pending payments", bd(current.pending), bd(previous.pending), "count", current.spark("pending")),
                kpi("refundedPayments", "Refunded payments", bd(current.refunded), bd(previous.refunded), "count", current.spark("refunded")),
                kpi("totalPaymentVolume", "Total payment volume", current.gross, previous.gross, "currency", current.spark("gross")),
                kpi("successfulPaymentVolume", "Successful payment volume", current.successVolume, previous.successVolume, "currency", current.spark("successVolume")),
                kpi("refundedAmount", "Refunded amount", current.refundAmount, previous.refundAmount, "currency", current.spark("refundAmount")),
                kpi("averageTransactionValue", "Average transaction value", average(current.gross, current.converted), average(previous.gross, previous.converted), "currency", current.spark("average")),
                kpi("paymentSuccessRate", "Payment success rate", rate(current.succeededAttempts(), current.completedAttempts()), rate(previous.succeededAttempts(), previous.completedAttempts()), "percent", current.spark("successRate")),
                kpi("paymentFailureRate", "Payment failure rate", rate(current.failed, current.completedAttempts()), rate(previous.failed, previous.completedAttempts()), "percent", current.spark("failureRate")),
                kpi("totalCustomers", "Total customers", bd(current.totalCustomers), bd(previous.totalCustomers), "count", current.sparkCustomers(false)),
                kpi("activeCustomers", "Active customers", bd(current.activeCustomers.size()), bd(previous.activeCustomers.size()), "count", current.sparkCustomers(true))
        );
    }

    private AnalyticsResponse.Kpi kpi(String key, String label, BigDecimal value, BigDecimal previous,
                                      String unit, List<BigDecimal> sparkline) {
        BigDecimal change = previous.signum() == 0 ? null : value.subtract(previous).multiply(HUNDRED)
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
        String trend = value.compareTo(previous) > 0 ? "INCREASE" : value.compareTo(previous) < 0 ? "DECREASE" : "NO_CHANGE";
        return new AnalyticsResponse.Kpi(key, label, scale(value), scale(previous), unit, change, trend, sparkline);
    }

    private List<AnalyticsResponse.StatusMetric> statusMetrics(Aggregate a) {
        return List.of(status("SUCCESSFUL", a.successful, a.total), status("FAILED", a.failed, a.total),
                status("PENDING", a.pending, a.total), status("REFUNDED", a.refunded, a.total));
    }

    private AnalyticsResponse.StatusMetric status(String status, long count, long total) {
        return new AnalyticsResponse.StatusMetric(status, count, rate(count, total));
    }

    private List<AnalyticsResponse.RatePoint> ratePoints(Aggregate a) {
        return a.buckets.values().stream().map(b -> new AnalyticsResponse.RatePoint(b.label, b.successful,
                b.failed, b.completedAttempts(), rate(b.succeededAttempts(), b.completedAttempts()),
                rate(b.failed, b.completedAttempts()))).toList();
    }

    private List<AnalyticsResponse.TransactionPoint> transactionPoints(Aggregate a) {
        return a.buckets.values().stream().map(b -> new AnalyticsResponse.TransactionPoint(b.label, b.total)).toList();
    }

    private List<AnalyticsResponse.VolumePoint> volumePoints(Aggregate a) {
        return a.buckets.values().stream().map(b -> new AnalyticsResponse.VolumePoint(b.label, scale(b.gross),
                scale(b.successVolume), scale(b.failedAmount), scale(b.refundAmount), average(b.gross, b.converted))).toList();
    }

    private List<AnalyticsResponse.MethodMetric> methodMetrics(Aggregate a) {
        return a.methods.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            Dimension d = entry.getValue();
            return new AnalyticsResponse.MethodMetric(entry.getKey(), d.total, scale(d.amount),
                    rate(d.succeededAttempts(), d.completedAttempts()));
        }).toList();
    }

    private List<AnalyticsResponse.CurrencyMetric> currencyMetrics(Aggregate a) {
        return a.currencies.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            Dimension d = entry.getValue();
            return new AnalyticsResponse.CurrencyMetric(entry.getKey(), d.total, scale(d.nativeAmount),
                    scale(d.nativeSuccessAmount));
        }).toList();
    }

    private List<AnalyticsResponse.CustomerGrowthPoint> customerGrowth(Aggregate a) {
        return a.buckets.values().stream().map(b -> new AnalyticsResponse.CustomerGrowthPoint(b.label,
                b.newCustomers, b.cumulativeCustomers, b.activeCustomers.size(), b.returningCustomers.size())).toList();
    }

    private List<AnalyticsResponse.TopCustomer> topCustomers(Aggregate a) {
        return a.customers.values().stream().sorted(Comparator.comparing((CustomerDimension c) -> c.successVolume).reversed()
                        .thenComparing(Comparator.comparingLong((CustomerDimension c) -> c.total).reversed())).limit(10)
                .map(c -> new AnalyticsResponse.TopCustomer(c.id, c.name, c.maskedCard, c.total,
                        scale(c.successVolume), average(c.amount, c.converted), rate(c.succeededAttempts(), c.completedAttempts())))
                .toList();
    }

    private List<AnalyticsResponse.HeatmapCell> heatmap(Aggregate a) {
        List<AnalyticsResponse.HeatmapCell> cells = new ArrayList<>(168);
        for (int day = 1; day <= 7; day++) for (int hour = 0; hour < 24; hour++) {
            cells.add(new AnalyticsResponse.HeatmapCell(day, DayOfWeek.of(day).name(), hour,
                    a.heatmap.getOrDefault(day + ":" + hour, 0L)));
        }
        return cells;
    }

    private List<AnalyticsResponse.FailureMetric> failureMetrics(Aggregate a) {
        return a.failures.values().stream().sorted(Comparator.comparingLong((Failure f) -> f.count).reversed())
                .limit(10).map(f -> new AnalyticsResponse.FailureMetric(f.code, f.reason, f.count)).toList();
    }

    private Grouping resolveGrouping(AnalyticsFilter filter) {
        if (filter.grouping() != AnalyticsFilter.Grouping.AUTO) return Grouping.valueOf(filter.grouping().name());
        long days = filter.days();
        if (days == 1) return Grouping.HOURLY;
        if (days <= 45) return Grouping.DAILY;
        if (days <= 180) return Grouping.WEEKLY;
        if (days <= 800) return Grouping.MONTHLY;
        return Grouping.YEARLY;
    }

    private static BigDecimal rate(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal amount, long count) {
        return count == 0 ? BigDecimal.ZERO : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(long value) { return BigDecimal.valueOf(value); }
    private static BigDecimal scale(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP); }
    private static String mask(String lastFour) { return lastFour == null ? null : "XXXX XXXX XXXX " + lastFour; }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static String normalizeCurrency(String value) {
        if (value == null || !value.trim().toUpperCase(Locale.ROOT).matches("[A-Z]{3}")) throw invalid("Invalid currency");
        return value.trim().toUpperCase(Locale.ROOT);
    }
    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
    }

    private enum Grouping { HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }
    private record QueryParts(String where, MapSqlParameterSource params) {}
    private record PaymentRow(Long id, BigDecimal amount, BigDecimal normalizedAmount, BigDecimal refundAmount,
                              String currency, String method, String outcome, String errorCode, String errorDescription,
                              Instant createdAt, Long customerId, String customerName, String customerRole, String maskedCard,
                              Instant firstPaymentAt) {}
    private record CustomerCreated(Long id, Instant createdAt) {}

    private static class Dimension {
        long total, successful, failed, refunded, converted;
        BigDecimal amount = BigDecimal.ZERO, nativeAmount = BigDecimal.ZERO, nativeSuccessAmount = BigDecimal.ZERO;
        long succeededAttempts() { return successful + refunded; }
        long completedAttempts() { return succeededAttempts() + failed; }
        void accept(PaymentRow row) {
            total++;
            nativeAmount = nativeAmount.add(row.amount);
            if (row.normalizedAmount != null) { amount = amount.add(row.normalizedAmount); converted++; }
            switch (row.outcome) {
                case "SUCCESSFUL" -> { successful++; nativeSuccessAmount = nativeSuccessAmount.add(row.amount); }
                case "FAILED" -> failed++;
                case "REFUNDED" -> refunded++;
                default -> { }
            }
        }
    }

    private static final class CustomerDimension extends Dimension {
        final long id;
        final String name;
        final String maskedCard;
        BigDecimal successVolume = BigDecimal.ZERO;
        CustomerDimension(long id, String name, String maskedCard) { this.id = id; this.name = name; this.maskedCard = maskedCard; }
        @Override void accept(PaymentRow row) {
            super.accept(row);
            if ("SUCCESSFUL".equals(row.outcome) && row.normalizedAmount != null) successVolume = successVolume.add(row.normalizedAmount);
        }
    }

    private static final class Failure {
        final String code, reason;
        long count;
        Failure(String code, String reason) { this.code = code; this.reason = reason; }
    }

    private static final class Bucket {
        final ZonedDateTime start;
        final String label;
        long total, successful, failed, pending, refunded, converted, newCustomers, cumulativeCustomers;
        BigDecimal gross = BigDecimal.ZERO, successVolume = BigDecimal.ZERO, failedAmount = BigDecimal.ZERO,
                refundAmount = BigDecimal.ZERO;
        final Set<Long> activeCustomers = new HashSet<>(), returningCustomers = new HashSet<>();
        Bucket(ZonedDateTime start, String label) { this.start = start; this.label = label; }
        long succeededAttempts() { return successful + refunded; }
        long completedAttempts() { return succeededAttempts() + failed; }
        ZonedDateTime end(Grouping grouping, ZoneId zone) {
            return switch (grouping) {
                case HOURLY -> start.plusHours(1);
                case DAILY -> start.plusDays(1);
                case WEEKLY -> start.plusWeeks(1);
                case MONTHLY -> start.plusMonths(1);
                case YEARLY -> start.plusYears(1);
            };
        }
        void accept(PaymentRow row, ZoneId zone) {
            total++;
            if ("CUSTOMER".equals(row.customerRole)) {
                activeCustomers.add(row.customerId);
                if (row.firstPaymentAt != null && row.firstPaymentAt.isBefore(start.toInstant())) returningCustomers.add(row.customerId);
            }
            if (row.normalizedAmount != null) { gross = gross.add(row.normalizedAmount); converted++; }
            switch (row.outcome) {
                case "SUCCESSFUL" -> { successful++; if (row.normalizedAmount != null) successVolume = successVolume.add(row.normalizedAmount); }
                case "FAILED" -> { failed++; if (row.normalizedAmount != null) failedAmount = failedAmount.add(row.normalizedAmount); }
                case "REFUNDED" -> { refunded++; if (row.refundAmount != null) refundAmount = refundAmount.add(row.refundAmount); }
                default -> pending++;
            }
        }
    }

    private static final class Aggregate {
        final AnalyticsFilter filter;
        final Grouping grouping;
        final LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
        final Map<String, Dimension> methods = new HashMap<>(), currencies = new HashMap<>();
        final Map<Long, CustomerDimension> customers = new HashMap<>();
        final Map<String, Long> heatmap = new HashMap<>();
        final Map<String, Failure> failures = new HashMap<>();
        final Set<Long> activeCustomers = new HashSet<>();
        long total, successful, failed, pending, refunded, converted, unconverted, totalCustomers;
        BigDecimal gross = BigDecimal.ZERO, successVolume = BigDecimal.ZERO, failedAmount = BigDecimal.ZERO,
                refundAmount = BigDecimal.ZERO;

        Aggregate(AnalyticsFilter filter, Grouping grouping) {
            this.filter = filter; this.grouping = grouping; initializeBuckets();
        }

        void accept(PaymentRow row, boolean detailed) {
            total++;
            if ("CUSTOMER".equals(row.customerRole)) activeCustomers.add(row.customerId);
            if (row.normalizedAmount == null) unconverted++; else { gross = gross.add(row.normalizedAmount); converted++; }
            switch (row.outcome) {
                case "SUCCESSFUL" -> { successful++; if (row.normalizedAmount != null) successVolume = successVolume.add(row.normalizedAmount); }
                case "FAILED" -> { failed++; if (row.normalizedAmount != null) failedAmount = failedAmount.add(row.normalizedAmount); }
                case "REFUNDED" -> { refunded++; if (row.refundAmount != null) refundAmount = refundAmount.add(row.refundAmount); }
                default -> pending++;
            }
            Bucket bucket = buckets.get(bucketKey(row.createdAt.atZone(filter.zoneId()), grouping));
            if (bucket != null) bucket.accept(row, filter.zoneId());
            if (!detailed) return;
            methods.computeIfAbsent(row.method, ignored -> new Dimension()).accept(row);
            currencies.computeIfAbsent(row.currency, ignored -> new Dimension()).accept(row);
            if ("CUSTOMER".equals(row.customerRole)) {
                customers.computeIfAbsent(row.customerId, ignored -> new CustomerDimension(row.customerId, row.customerName, row.maskedCard)).accept(row);
            }
            ZonedDateTime local = row.createdAt.atZone(filter.zoneId());
            heatmap.merge(local.getDayOfWeek().getValue() + ":" + local.getHour(), 1L, Long::sum);
            if ("FAILED".equals(row.outcome)) {
                String code = row.errorCode == null || row.errorCode.isBlank() ? "UNSPECIFIED" : row.errorCode;
                String reason = row.errorDescription == null || row.errorDescription.isBlank() ? "Unspecified failure" : row.errorDescription;
                failures.computeIfAbsent(code + "\u0000" + reason, ignored -> new Failure(code, reason)).count++;
            }
        }

        long succeededAttempts() { return successful + refunded; }
        long completedAttempts() { return succeededAttempts() + failed; }

        List<BigDecimal> spark(String metric) {
            return buckets.values().stream().map(b -> switch (metric) {
                case "total" -> bd(b.total); case "successful" -> bd(b.successful); case "failed" -> bd(b.failed);
                case "pending" -> bd(b.pending); case "refunded" -> bd(b.refunded); case "gross" -> scale(b.gross);
                case "successVolume" -> scale(b.successVolume); case "refundAmount" -> scale(b.refundAmount);
                case "average" -> average(b.gross, b.converted); case "successRate" -> rate(b.succeededAttempts(), b.completedAttempts());
                case "failureRate" -> rate(b.failed, b.completedAttempts()); default -> BigDecimal.ZERO;
            }).toList();
        }
        List<BigDecimal> sparkCustomers(boolean active) {
            return buckets.values().stream().map(b -> bd(active ? b.activeCustomers.size() : b.cumulativeCustomers)).toList();
        }

        private void initializeBuckets() {
            ZonedDateTime cursor = floor(filter.from().atZone(filter.zoneId()), grouping);
            while (cursor.toInstant().isBefore(filter.toExclusive())) {
                String key = bucketKey(cursor, grouping);
                buckets.put(key, new Bucket(cursor, label(cursor, grouping)));
                cursor = switch (grouping) {
                    case HOURLY -> cursor.plusHours(1); case DAILY -> cursor.plusDays(1); case WEEKLY -> cursor.plusWeeks(1);
                    case MONTHLY -> cursor.plusMonths(1); case YEARLY -> cursor.plusYears(1);
                };
            }
        }
    }

    private static ZonedDateTime floor(ZonedDateTime value, Grouping grouping) {
        ZonedDateTime day = value.truncatedTo(ChronoUnit.DAYS);
        return switch (grouping) {
            case HOURLY -> value.truncatedTo(ChronoUnit.HOURS);
            case DAILY -> day;
            case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> day.withDayOfMonth(1);
            case YEARLY -> day.withDayOfYear(1);
        };
    }
    private static String bucketKey(ZonedDateTime value, Grouping grouping) { return floor(value, grouping).toInstant().toString(); }
    private static String label(ZonedDateTime value, Grouping grouping) {
        return switch (grouping) {
            case HOURLY -> value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
            case DAILY, WEEKLY -> value.toLocalDate().toString();
            case MONTHLY -> value.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case YEARLY -> Integer.toString(value.getYear());
        };
    }
}
