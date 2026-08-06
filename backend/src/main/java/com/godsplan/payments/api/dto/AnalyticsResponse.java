package com.godsplan.payments.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AnalyticsResponse {
    private AnalyticsResponse() {}

    public record Overview(
            Instant generatedAt,
            String timeZone,
            String baseCurrency,
            String grouping,
            long unconvertedTransactions,
            List<Kpi> kpis,
            List<StatusMetric> paymentStatus,
            List<RatePoint> paymentRates,
            List<TransactionPoint> transactionsOverTime,
            List<VolumePoint> paymentVolume,
            List<MethodMetric> paymentMethods,
            List<CurrencyMetric> currencies,
            List<CustomerGrowthPoint> customerGrowth,
            List<TopCustomer> topCustomers,
            List<HeatmapCell> activityHeatmap,
            List<FailureMetric> failureReasons,
            FilterOptions filterOptions) {}

    public record Kpi(String key, String label, BigDecimal value, BigDecimal previousValue,
                      String unit, BigDecimal changePercent, String trend, List<BigDecimal> sparkline) {}

    public record StatusMetric(String status, long count, BigDecimal percentage) {}

    public record RatePoint(String period, long successful, long failed, long completedAttempts,
                            BigDecimal successRate, BigDecimal failureRate) {}

    public record TransactionPoint(String period, long transactions) {}

    public record VolumePoint(String period, BigDecimal grossVolume, BigDecimal successfulVolume,
                              BigDecimal failedAmount, BigDecimal refundedAmount,
                              BigDecimal averageTransactionValue) {}

    public record MethodMetric(String paymentMethod, long transactionCount, BigDecimal totalAmount,
                               BigDecimal successRate) {}

    public record CurrencyMetric(String currency, long transactionCount, BigDecimal paymentVolume,
                                 BigDecimal successfulVolume) {}

    public record CustomerGrowthPoint(String period, long newCustomers, long cumulativeCustomers,
                                      long activeCustomers, long returningCustomers) {}

        public record TopCustomer(Long customerId, String customerName, String cardNumber,
                              long transactionCount, BigDecimal successfulVolume,
                              BigDecimal averageTransactionValue, BigDecimal successRate) {}

    public record HeatmapCell(int dayOfWeek, String day, int hour, long transactions) {}

    public record FailureMetric(String code, String reason, long count) {}

    public record FilterOptions(List<String> statuses, List<String> currencies,
                                List<String> paymentMethods, List<CustomerOption> customers) {}

    public record CustomerOption(Long id, String name) {}

    public record ExchangeRateHistory(
            String sourceCurrency,
            String targetCurrency,
            BigDecimal currentRate,
            BigDecimal highestRate,
            BigDecimal lowestRate,
            BigDecimal percentageChange,
            List<ExchangeRatePoint> history) {}

    public record ExchangeRatePoint(Instant fetchedAt, BigDecimal rate, String source) {}

    public record RecentTransaction(
            Long transactionId,
            Long customerId,
            String customerName,
            String cardNumber,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String paymentStatus,
            Instant paymentDate,
            String failureReason) {}
}
