package com.godsplan.payments.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull Long senderCustomerId,
        @NotNull Long sourceAccountId,
        @NotNull Long receiverCustomerId,
        @NotNull Long destinationAccountId,
        @NotNull BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 120) String intermediaryBank,
        @JsonAlias("paymentReference") @Size(max = 200) String reference) {}
