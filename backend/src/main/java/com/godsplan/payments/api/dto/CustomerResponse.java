package com.godsplan.payments.api.dto;

import java.util.List;

public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        String maskedCardNumber,
        String cardBrand,
        List<AccountDetails> accounts) {
    public record AccountDetails(Long id, String accountNumber, String currency, boolean active) {}
}

