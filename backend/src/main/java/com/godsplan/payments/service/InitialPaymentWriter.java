package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.CreatePaymentRequest;
import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.domain.PaymentStatusHistory;
import com.godsplan.payments.repository.PaymentHistoryRepository;
import com.godsplan.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialPaymentWriter {
    private static final BigDecimal FEE_RATE = new BigDecimal("0.02");

    private final PaymentRepository payments;
    private final PaymentHistoryRepository history;

    public InitialPaymentWriter(PaymentRepository payments, PaymentHistoryRepository history) {
        this.payments = payments;
        this.history = history;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment create(String key, CreatePaymentRequest request, String currency,
                          Account source, Account destination) {
        Payment payment = new Payment();
        payment.setIdempotencyKey(key);
        payment.setAmount(request.amount());
        BigDecimal fee = request.amount().multiply(FEE_RATE).setScale(2, RoundingMode.HALF_EVEN);
        payment.setFeeAmount(fee);
        payment.setTotalDebitAmount(request.amount().add(fee));
        payment.setCurrency(currency);
        payment.setSourceAccount(source);
        payment.setDestinationAccount(destination);
        payment.setReference(request.reference() == null || request.reference().isBlank() ? null : request.reference().trim());
        payment.setStatus(PaymentStatus.CREATED);
        payments.saveAndFlush(payment);
        history.save(new PaymentStatusHistory(payment, null, PaymentStatus.CREATED, null, null));
        return payment;
    }
}

