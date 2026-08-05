package com.godsplan.payments.error;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, String path, String code, String message,
                       List<FieldError> fieldErrors, String traceId) {
    public record FieldError(String field, String message) {}
}
