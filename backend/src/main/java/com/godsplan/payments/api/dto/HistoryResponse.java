package com.godsplan.payments.api.dto;

import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.domain.PaymentStatusHistory;
import java.time.Instant;
import java.util.List;

public record HistoryResponse(Long paymentId, List<HistoryItem> history) {
    public record HistoryItem(PaymentStatus fromStatus, PaymentStatus toStatus, String errorCode,
                              String errorDescription, Instant createdAt) {
        public static HistoryItem from(PaymentStatusHistory item) {
            return new HistoryItem(item.getFromStatus(), item.getToStatus(), item.getErrorCode(),
                    item.getErrorDescription(), item.getCreatedAt());
        }
    }
}

