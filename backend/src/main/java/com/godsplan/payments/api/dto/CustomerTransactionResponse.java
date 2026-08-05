package com.godsplan.payments.api.dto;

import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record CustomerTransactionResponse(
        Long transactionId,
        BigDecimal amount,
        String currency,
        Instant paymentDate,
        String paymentMethod,
        PaymentStatus paymentStatus,
        String outcome) {
    public static CustomerTransactionResponse from(Payment payment) {
        String outcome = switch (payment.getStatus()) {
            case COMPLETED -> "SUCCESSFUL";
            case FAILED -> "FAILED";
            default -> "PENDING";
        };
        return new CustomerTransactionResponse(payment.getId(), payment.getAmount(), payment.getCurrency(),
                payment.getCreatedAt(), payment.getPaymentMethod(), payment.getStatus(), outcome);
    }
}

