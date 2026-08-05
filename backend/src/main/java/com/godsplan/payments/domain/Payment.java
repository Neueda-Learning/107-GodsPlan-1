package com.godsplan.payments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 80, updatable = false)
    private String idempotencyKey;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private Account destinationAccount;
    @Column(length = 200)
    private String reference;
    @Column(name = "intermediary_bank", length = 120)
    private String intermediaryBank;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;
    @Column(name = "error_code", length = 40)
    private String errorCode;
    @Column(name = "error_description", length = 300)
    private String errorDescription;
    @Column(name = "destination_amount", precision = 15, scale = 2)
    private BigDecimal destinationAmount;
    @Column(name = "exchange_rate", precision = 18, scale = 8)
    private BigDecimal exchangeRate;
    @Column(name = "exchange_rate_source", length = 60)
    private String exchangeRateSource;
    @Column(name = "exchange_rate_fetched_at")
    private Instant exchangeRateFetchedAt;
    @Column(name = "payment_method", nullable = false, length = 80)
    private String paymentMethod = "Bank transfer";
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;
}
