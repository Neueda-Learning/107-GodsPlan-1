package com.godsplan.payments.api.dto;

import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        Long sourceAccountId,
        Long destinationAccountId,
    String sourceAccountNumber,
    String destinationAccountNumber,
        String reference,
        String intermediaryBank,
        BigDecimal destinationAmount,
        String destinationCurrency,
        BigDecimal exchangeRate,
        String exchangeRateSource,
        Instant exchangeRateFetchedAt,
        String errorCode,
        String errorDescription,
        Instant createdAt,
        Instant updatedAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getStatus(), p.getAmount(), p.getCurrency(),
                p.getSourceAccount().getId(), p.getDestinationAccount().getId(),
                p.getSourceAccount().getAccountNumber(), p.getDestinationAccount().getAccountNumber(), p.getReference(),
                p.getIntermediaryBank(),
                p.getDestinationAmount(), p.getDestinationAmount() == null ? null : p.getDestinationAccount().getCurrency(),
                p.getExchangeRate(), p.getExchangeRateSource(), p.getExchangeRateFetchedAt(),
                p.getErrorCode(), p.getErrorDescription(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
