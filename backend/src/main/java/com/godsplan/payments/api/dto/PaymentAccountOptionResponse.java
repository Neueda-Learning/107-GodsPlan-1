package com.godsplan.payments.api.dto;

import java.math.BigDecimal;

public record PaymentAccountOptionResponse(
        Long id,
        String accountType,
        String accountNumber,
        String currency,
        BigDecimal availableBalance,
        String label) {}
