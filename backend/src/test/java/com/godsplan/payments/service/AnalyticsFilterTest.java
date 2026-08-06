package com.godsplan.payments.service;

import com.godsplan.payments.config.AnalyticsProperties;
import com.godsplan.payments.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class AnalyticsFilterTest {

    private AnalyticsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AnalyticsProperties(
                "test",      // environment
                "UTC",       // timeZone
                30,          // defaultRangeDays
                365,         // maxRangeDays
                100,         // maxPageSize
                250_000,     // maxQueryRows
                "USD"        // defaultBaseCurrency
        );
    }

    // ── defaults ─────────────────────────────────────────────────────────────

    @Test
    void create_withAllNulls_usesDefaultsSuccessfully() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, null, null, null, null, null, null, null, null, null, properties);

        assertThat(filter.baseCurrency()).isEqualTo("USD");
        assertThat(filter.status()).isNull();
        assertThat(filter.currency()).isNull();
        assertThat(filter.grouping()).isEqualTo(AnalyticsFilter.Grouping.AUTO);
        assertThat(filter.auditScope()).isEqualTo(AnalyticsFilter.AuditScope.ALL);
    }

    @Test
    void create_withNullDates_derivesFromRange_defaultRangeDays() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, null, null, null, null, null, null, null, null, null, properties);

        assertThat(filter.fromDate()).isNotNull();
        assertThat(filter.toDate()).isNotNull();
        // from should be (toDate - defaultRangeDays + 1) days before to
        assertThat(filter.fromDate()).isEqualTo(filter.toDate().minusDays(29)); // 30 days inclusive
    }

    @Test
    void create_withExplicitDates_usesProvidedDates() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);

        AnalyticsFilter filter = AnalyticsFilter.create(
                from, to, null, null, null, null, null, null, null, null, null, properties);

        assertThat(filter.fromDate()).isEqualTo(from);
        assertThat(filter.toDate()).isEqualTo(to);
    }

    // ── date validation ───────────────────────────────────────────────────────

    @Test
    void create_startDateAfterEndDate_throwsApiException() {
        LocalDate from = LocalDate.of(2025, 2, 1);
        LocalDate to = LocalDate.of(2025, 1, 1); // before from

        assertThatThrownBy(() -> AnalyticsFilter.create(
                from, to, null, null, null, null, null, null, null, null, null, properties))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("start date must not be after"));
    }

    @Test
    void create_rangeTooLarge_throwsApiException() {
        LocalDate from = LocalDate.of(2020, 1, 1);
        LocalDate to = LocalDate.of(2025, 12, 31); // > 365 days

        assertThatThrownBy(() -> AnalyticsFilter.create(
                from, to, null, null, null, null, null, null, null, null, null, properties))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("date range is too large"));
    }

    @Test
    void create_singleDay_isValid() {
        LocalDate today = LocalDate.of(2025, 6, 1);

        AnalyticsFilter filter = AnalyticsFilter.create(
                today, today, null, null, null, null, null, null, null, null, null, properties);

        assertThat(filter.fromDate()).isEqualTo(today);
        assertThat(filter.toDate()).isEqualTo(today);
    }

    // ── amount filters ────────────────────────────────────────────────────────

    @Test
    void create_negativeMinimumAmount_throwsApiException() {
        assertThatThrownBy(() -> AnalyticsFilter.create(
                null, null, null, null, null, null, null,
                new BigDecimal("-1.00"), null, null, null, properties))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("Minimum amount cannot be negative"));
    }

    @Test
    void create_negativeMaximumAmount_throwsApiException() {
        assertThatThrownBy(() -> AnalyticsFilter.create(
                null, null, null, null, null, null, null,
                null, new BigDecimal("-0.01"), null, null, properties))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("Maximum amount cannot be negative"));
    }

    @Test
    void create_minimumExceedsMaximum_throwsApiException() {
        assertThatThrownBy(() -> AnalyticsFilter.create(
                null, null, null, null, null, null, null,
                new BigDecimal("500.00"), new BigDecimal("100.00"), null, null, properties))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("Minimum amount cannot exceed maximum amount"));
    }

    @Test
    void create_validAmountRange_accepted() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, null, null, null, null, null,
                new BigDecimal("10.00"), new BigDecimal("500.00"), null, null, properties);

        assertThat(filter.minimumAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(filter.maximumAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    // ── status filter ─────────────────────────────────────────────────────────

    @Test
    void create_validStatus_accepted() {
        for (String status : new String[]{"SUCCESSFUL", "FAILED", "PENDING", "REFUNDED"}) {
            AnalyticsFilter filter = AnalyticsFilter.create(
                    null, null, status, null, null, null, null, null, null, null, null, properties);
            assertThat(filter.status()).isEqualTo(status);
        }
    }

    @Test
    void create_lowercaseStatus_normalizedToUppercase() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, "successful", null, null, null, null, null, null, null, null, properties);

        assertThat(filter.status()).isEqualTo("SUCCESSFUL");
    }

    @Test
    void create_invalidStatus_throwsApiException() {
        assertThatThrownBy(() -> AnalyticsFilter.create(
                null, null, "UNKNOWN_STATUS", null, null, null, null, null, null, null, null, properties))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("Unsupported analytics status"));
    }

    // ── audit scope ───────────────────────────────────────────────────────────

    @Test
    void create_validAuditScope_accepted() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, null, null, null, "PAYMENTS_ONLY",
                null, null, null, null, null, properties);

        assertThat(filter.auditScope()).isEqualTo(AnalyticsFilter.AuditScope.PAYMENTS_ONLY);
    }

    @Test
    void create_invalidAuditScope_throwsApiException() {
        assertThatThrownBy(() -> AnalyticsFilter.create(
                null, null, null, null, null, "INVALID_SCOPE",
                null, null, null, null, null, properties))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("Unsupported audit scope"));
    }

    // ── grouping ──────────────────────────────────────────────────────────────

    @Test
    void create_validGrouping_accepted() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, null, null, null, null, null, null, null, null, "MONTHLY", properties);

        assertThat(filter.grouping()).isEqualTo(AnalyticsFilter.Grouping.MONTHLY);
    }

    @Test
    void create_lowercaseGrouping_normalizedToUppercase() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, null, null, null, null, null, null, null, null, "daily", properties);

        assertThat(filter.grouping()).isEqualTo(AnalyticsFilter.Grouping.DAILY);
    }

    @Test
    void create_invalidGrouping_throwsApiException() {
        assertThatThrownBy(() -> AnalyticsFilter.create(
                null, null, null, null, null, null, null, null, null, null, "FORTNIGHTLY", properties))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("Unsupported grouping interval"));
    }

    // ── currency normalisation ────────────────────────────────────────────────

    @Test
    void create_blankCurrencyFilter_treatedAsNull() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, null, "  ", null, null, null, null, null, null, null, properties);

        assertThat(filter.currency()).isNull();
    }

    @Test
    void create_lowercaseCurrencyFilter_normalizedToUppercase() {
        AnalyticsFilter filter = AnalyticsFilter.create(
                null, null, null, "usd", null, null, null, null, null, null, null, properties);

        assertThat(filter.currency()).isEqualTo("USD");
    }

    @Test
    void create_invalidTimezone_throwsApiException() {
        AnalyticsProperties badTz = new AnalyticsProperties(
                "test", "Invalid/Zone", 30, 365, 100, 250_000, "USD");

        assertThatThrownBy(() -> AnalyticsFilter.create(
                null, null, null, null, null, null, null, null, null, null, null, badTz))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("timezone is invalid"));
    }
}
