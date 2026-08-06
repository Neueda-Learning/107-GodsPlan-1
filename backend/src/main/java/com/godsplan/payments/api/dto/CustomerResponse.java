package com.godsplan.payments.api.dto;

import java.util.List;

public record CustomerResponse(
        Long id,
        String fullName,
        String email,
        String cardNumber,
        String cardBrand,
        List<AccountDetails> accounts) {
    public record AccountDetails(Long id, String accountType, String accountNumber,
                                 String currency, boolean active) {}
}
