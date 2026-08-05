package com.godsplan.payments.api.dto;

import com.godsplan.payments.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusTransitionRequest(
        @NotNull PaymentStatus toStatus,
        @Size(max = 40) String errorCode,
        @Size(max = 300) String errorDescription) {}

