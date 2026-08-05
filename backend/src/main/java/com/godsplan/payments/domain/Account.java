package com.godsplan.payments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_number", nullable = false, unique = true, length = 34)
    private String accountNumber;
    @Column(name = "account_type", nullable = false, length = 40)
    private String accountType;
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;
    @Column(nullable = false)
    private boolean active;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerUser customer;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public void debit(BigDecimal amount) {
        availableBalance = availableBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        availableBalance = availableBalance.add(amount);
    }
}
