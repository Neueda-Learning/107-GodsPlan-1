package com.godsplan.payments.service;

import com.godsplan.payments.config.AnalyticsProperties;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

public record AnalyticsFilter(
        LocalDate fromDate,
        LocalDate toDate,
        Instant from,
        Instant toExclusive,
        String status,
        String currency,
        String paymentMethod,
        AuditScope auditScope,
        Long customerId,
        BigDecimal minimumAmount,
        BigDecimal maximumAmount,
        String baseCurrency,
        Grouping grouping,
        ZoneId zoneId) {

    private static final Set<String> STATUSES = Set.of("SUCCESSFUL", "FAILED", "PENDING", "REFUNDED");

    public enum Grouping { AUTO, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }
    public enum AuditScope { ALL, PAYMENTS_ONLY, INSUFFICIENT_ONLY }

    public static AnalyticsFilter create(LocalDate requestedFrom, LocalDate requestedTo, String status,
                                         String currency, String paymentMethod, String auditScope, Long customerId,
                                         BigDecimal minimumAmount, BigDecimal maximumAmount,
                                         String baseCurrency, String grouping,
                                         AnalyticsProperties properties) {
        ZoneId zone;
        try {
            zone = ZoneId.of(properties.timeZone());
        } catch (DateTimeException exception) {
            throw invalid("The configured analytics timezone is invalid");
        }
        LocalDate today = LocalDate.now(zone);
        LocalDate to = requestedTo == null ? today : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(Math.max(properties.defaultRangeDays() - 1L, 0)) : requestedFrom;
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 0) throw invalid("The start date must not be after the end date");
        if (days > properties.maxRangeDays()) throw invalid("The selected date range is too large");
        if (minimumAmount != null && minimumAmount.signum() < 0) throw invalid("Minimum amount cannot be negative");
        if (maximumAmount != null && maximumAmount.signum() < 0) throw invalid("Maximum amount cannot be negative");
        if (minimumAmount != null && maximumAmount != null && minimumAmount.compareTo(maximumAmount) > 0) {
            throw invalid("Minimum amount cannot exceed maximum amount");
        }
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) throw invalid("Unsupported analytics status");
        String normalizedCurrency = currency == null || currency.isBlank() ? null : normalizeCurrency(currency, "currency");
        String normalizedBase = normalizeCurrency(baseCurrency == null ? properties.defaultBaseCurrency() : baseCurrency,
                "base currency");
        AuditScope normalizedAuditScope;
        try {
            normalizedAuditScope = auditScope == null || auditScope.isBlank()
                    ? AuditScope.ALL : AuditScope.valueOf(auditScope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("Unsupported audit scope");
        }
        Grouping normalizedGrouping;
        try {
            normalizedGrouping = grouping == null || grouping.isBlank()
                    ? Grouping.AUTO : Grouping.valueOf(grouping.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("Unsupported grouping interval");
        }
        return new AnalyticsFilter(from, to, from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant(), normalizedStatus, normalizedCurrency,
                blankToNull(paymentMethod), normalizedAuditScope, customerId, minimumAmount, maximumAmount,
                normalizedBase, normalizedGrouping, zone);
    }

    public AnalyticsFilter previousPeriod() {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LocalDate previousTo = fromDate.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        return new AnalyticsFilter(previousFrom, previousTo, previousFrom.atStartOfDay(zoneId).toInstant(),
                previousTo.plusDays(1).atStartOfDay(zoneId).toInstant(), status, currency, paymentMethod,
            auditScope, customerId, minimumAmount, maximumAmount, baseCurrency, grouping, zoneId);
    }

    public long days() {
        return ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCurrency(String value, String label) {
        String normalized = normalize(value);
        if (normalized == null || !normalized.matches("[A-Z]{3}")) throw invalid("Invalid " + label);
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
    }
}
