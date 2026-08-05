package com.godsplan.payments.service;

import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.domain.PaymentStatusHistory;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.PaymentHistoryRepository;
import com.godsplan.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifecycleService {
    private static final Logger log = LoggerFactory.getLogger(LifecycleService.class);
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PaymentStatus.CREATED, Set.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
            PaymentStatus.VALIDATED, Set.of(PaymentStatus.SENT, PaymentStatus.FAILED),
            PaymentStatus.SENT, Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
            PaymentStatus.COMPLETED, Set.of(),
            PaymentStatus.FAILED, Set.of());
    private final PaymentRepository payments;
    private final PaymentHistoryRepository history;

    public LifecycleService(PaymentRepository payments, PaymentHistoryRepository history) {
        this.payments = payments;
        this.history = history;
    }

    @Transactional
    public Payment transition(Long paymentId, PaymentStatus target, String errorCode, String description) {
        return transitionInternal(paymentId, target, errorCode, description, null, null);
    }

    @Transactional
    public Payment validateWithRate(Long paymentId, BigDecimal destinationAmount, RateQuote quote) {
        return transitionInternal(paymentId, PaymentStatus.VALIDATED, null, null, destinationAmount, quote);
    }

    private Payment transitionInternal(Long paymentId, PaymentStatus target, String errorCode, String description,
                                       BigDecimal destinationAmount, RateQuote quote) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(() -> notFound(paymentId));
        PaymentStatus current = payment.getStatus();
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION, HttpStatus.BAD_REQUEST,
                    "Payment cannot move from " + current + " to " + target);
        }
        if (target == PaymentStatus.FAILED) {
            errorCode = errorCode == null || errorCode.isBlank() ? ErrorCode.PROCESSING_ERROR.name() : errorCode;
            description = description == null || description.isBlank() ? "Payment processing failed" : description;
        } else {
            errorCode = null;
            description = null;
        }
        payment.setStatus(target);
        payment.setErrorCode(errorCode);
        payment.setErrorDescription(description);
        if (quote != null) {
            payment.setDestinationAmount(destinationAmount);
            payment.setExchangeRate(quote.rate());
            payment.setExchangeRateSource(quote.source());
            payment.setExchangeRateFetchedAt(quote.fetchedAt());
        }
        payments.save(payment);
        history.save(new PaymentStatusHistory(payment, current, target, errorCode, description));
        log.info("Payment {} transitioned {} -> {}{}", paymentId, current, target,
                errorCode == null ? "" : " (" + errorCode + ")");
        return payment;
    }

    private ApiException notFound(Long id) {
        return new ApiException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Payment " + id + " was not found");
    }
}
