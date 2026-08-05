package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.AnalyticsResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.config.AnalyticsProperties;
import com.godsplan.payments.service.AnalyticsFilter;
import com.godsplan.payments.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final AnalyticsProperties properties;

    public AnalyticsController(AnalyticsService analytics, AnalyticsProperties properties) {
        this.analytics = analytics;
        this.properties = properties;
    }

    @GetMapping("/overview")
    @Operation(summary = "Return database-aggregated analytics for the selected filters")
    public AnalyticsResponse.Overview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) BigDecimal minimumAmount,
            @RequestParam(required = false) BigDecimal maximumAmount,
            @RequestParam(required = false) String baseCurrency,
            @RequestParam(required = false) String grouping) {
        return analytics.overview(filter(from, to, status, currency, paymentMethod, customerId,
                minimumAmount, maximumAmount, baseCurrency, grouping));
    }

    @GetMapping("/recent-transactions")
    @Operation(summary = "Return a paginated, privacy-safe recent transaction list")
    public PageResponse<AnalyticsResponse.RecentTransaction> recent(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) BigDecimal minimumAmount,
            @RequestParam(required = false) BigDecimal maximumAmount,
            @RequestParam(required = false) String baseCurrency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return analytics.recent(filter(from, to, status, currency, paymentMethod, customerId,
                minimumAmount, maximumAmount, baseCurrency, "AUTO"), page, size);
    }

    @GetMapping("/exchange-rates")
    @Operation(summary = "Return stored exchange-rate history for a currency pair")
    public AnalyticsResponse.ExchangeRateHistory exchangeRates(
            @RequestParam String sourceCurrency,
            @RequestParam String targetCurrency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.exchangeRates(sourceCurrency, targetCurrency, from, to);
    }

    private AnalyticsFilter filter(LocalDate from, LocalDate to, String status, String currency,
                                   String paymentMethod, Long customerId, BigDecimal minimumAmount,
                                   BigDecimal maximumAmount, String baseCurrency, String grouping) {
        return AnalyticsFilter.create(from, to, status, currency, paymentMethod, customerId,
                minimumAmount, maximumAmount, baseCurrency, grouping, properties);
    }
}
