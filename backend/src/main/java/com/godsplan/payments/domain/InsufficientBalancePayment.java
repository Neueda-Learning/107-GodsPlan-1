package com.godsplan.payments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "insufficient_balance_payments")
@Getter
@Setter
@NoArgsConstructor
public class InsufficientBalancePayment {
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
    @Column(name = "payment_method", nullable = false, length = 80)
    private String paymentMethod = "Bank transfer";
    @Column(length = 200)
    private String reference;
    @Column(name = "intermediary_bank", length = 120)
    private String intermediaryBank;
    @Column(name = "error_code", nullable = false, length = 40)
    private String errorCode;
    @Column(name = "error_description", nullable = false, length = 300)
    private String errorDescription;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
