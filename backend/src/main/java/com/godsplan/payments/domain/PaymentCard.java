package com.godsplan.payments.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_cards")
@Getter
@NoArgsConstructor
public class PaymentCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerUser customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Column(nullable = false, length = 30)
    private String brand;
    @Column(name = "last_four", nullable = false, length = 4, columnDefinition = "char(4)")
    private String lastFour;
    @Column(name = "expiry_month", nullable = false)
    private byte expiryMonth;
    @Column(name = "expiry_year", nullable = false)
    private short expiryYear;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
