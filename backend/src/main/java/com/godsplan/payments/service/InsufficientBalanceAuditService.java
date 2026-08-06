package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.CreatePaymentRequest;
import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.InsufficientBalancePayment;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.InsufficientBalancePaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InsufficientBalanceAuditService {
    private static final String MESSAGE =
            "The selected account does not have sufficient funds to complete this transaction.";

    private final InsufficientBalancePaymentRepository audits;

    public InsufficientBalanceAuditService(InsufficientBalancePaymentRepository audits) {
        this.audits = audits;
    }

    @Transactional
    public void record(String idempotencyKey, CreatePaymentRequest request, String currency,
                       Account source, Account destination) {
        if (audits.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        InsufficientBalancePayment audit = new InsufficientBalancePayment();
        audit.setIdempotencyKey(idempotencyKey);
        audit.setAmount(request.amount());
        audit.setCurrency(currency);
        audit.setSourceAccount(source);
        audit.setDestinationAccount(destination);
        audit.setReference(request.reference() == null || request.reference().isBlank() ? null : request.reference().trim());
        audit.setIntermediaryBank(request.intermediaryBank() == null || request.intermediaryBank().isBlank()
                ? null : request.intermediaryBank().trim());
        audit.setErrorCode(ErrorCode.INSUFFICIENT_FUNDS.name());
        audit.setErrorDescription(MESSAGE);
        try {
            audits.save(audit);
        } catch (DataIntegrityViolationException ignored) {
            // Another concurrent request with the same idempotency key already persisted this audit row.
        }
    }
}
