package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.HistoryResponse;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.PaymentHistoryRepository;
import com.godsplan.payments.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final PaymentRepository payments;
    private final PaymentHistoryRepository history;

    public AuditService(PaymentRepository payments, PaymentHistoryRepository history) {
        this.payments = payments;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public HistoryResponse getHistory(Long paymentId) {
        if (!payments.existsById(paymentId)) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Payment " + paymentId + " was not found");
        }
        return new HistoryResponse(paymentId, history.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId)
                .stream().map(HistoryResponse.HistoryItem::from).toList());
    }
}

