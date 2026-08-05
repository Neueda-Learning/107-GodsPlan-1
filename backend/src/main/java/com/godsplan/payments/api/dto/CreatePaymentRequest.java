package com.godsplan.payments.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull Long sourceAccountId,
        @NotNull Long destinationAccountId,
        @Size(max = 200) String reference) {}

