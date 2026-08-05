package com.godsplan.payments.api.dto;

public record PaymentAccountOptionResponse(
        Long id,
        String accountType,
        String maskedAccountNumber,
        String currency,
        String label) {}
