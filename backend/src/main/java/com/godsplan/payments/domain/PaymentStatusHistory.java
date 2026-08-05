package com.godsplan.payments.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "payment_status_history")
@Getter
@NoArgsConstructor
public class PaymentStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private PaymentStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private PaymentStatus toStatus;
    @Column(name = "error_code", length = 40)
    private String errorCode;
    @Column(name = "error_description", length = 300)
    private String errorDescription;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentStatusHistory(Payment payment, PaymentStatus fromStatus, PaymentStatus toStatus,
                                String errorCode, String errorDescription) {
        this.payment = payment;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }
}

