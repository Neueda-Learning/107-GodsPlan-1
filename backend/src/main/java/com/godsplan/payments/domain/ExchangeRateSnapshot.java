package com.godsplan.payments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "exchange_rate_history", uniqueConstraints = @UniqueConstraint(
        name = "uk_exchange_snapshot", columnNames = {"base_currency", "quote_currency", "source", "fetched_at"}))
@Getter
@NoArgsConstructor
public class ExchangeRateSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "base_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String baseCurrency;
    @Column(name = "quote_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String quoteCurrency;
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;
    @Column(nullable = false, length = 60)
    private String source;
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ExchangeRateSnapshot(String baseCurrency, String quoteCurrency, BigDecimal rate,
                                String source, Instant fetchedAt) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rate = rate;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }
}
