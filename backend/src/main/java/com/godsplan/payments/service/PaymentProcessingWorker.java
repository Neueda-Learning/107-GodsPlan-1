package com.godsplan.payments.service;

import com.godsplan.payments.config.PaymentProperties;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Drives a created payment through its remaining lifecycle stages
 * (VALIDATED -> SENT -> COMPLETED/FAILED) on a background thread,
 * with a fixed configured delay between each transition, so the initial create request can
 * return immediately once the payment has been persisted as CREATED.
 */
@Service
public class PaymentProcessingWorker {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingWorker.class);

    private final PaymentRepository payments;
    private final ValidationService validation;
    private final ExchangeRateService exchangeRates;
    private final LifecycleService lifecycle;
    private final SettlementService settlement;
    private final PaymentProperties properties;

    public PaymentProcessingWorker(PaymentRepository payments, ValidationService validation,
                                   ExchangeRateService exchangeRates, LifecycleService lifecycle,
                                   SettlementService settlement, PaymentProperties properties) {
        this.payments = payments;
        this.validation = validation;
        this.exchangeRates = exchangeRates;
        this.lifecycle = lifecycle;
        this.settlement = settlement;
        this.properties = properties;
    }

    @Async("paymentProcessingExecutor")
    public void processAsync(Long paymentId) {
        try {
            awaitStageDelay();
            validateAndMarkValidated(paymentId);

            awaitStageDelay();
            settlement.markSent(paymentId);

            awaitStageDelay();
            settlement.completeSettlement(paymentId);
        } catch (BusinessFailure failure) {
            log.info("Payment {} failed validation: {}", paymentId, failure.getCode());
            failSafely(paymentId, failure.getCode().name(), failure.getMessage());
        } catch (Exception exception) {
            log.error("Payment {} processing failed", paymentId, exception);
            failSafely(paymentId, ErrorCode.PROCESSING_ERROR.name(), "Payment processing could not be completed");
        }
    }

    private void validateAndMarkValidated(Long paymentId) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> notFound(paymentId));
        validation.validateBusiness(payment);
        String destinationCurrency = payment.getDestinationAccount().getCurrency();
        if (!destinationCurrency.equals(payment.getCurrency())) {
            RateQuote quote = exchangeRates.getRate(payment.getCurrency(), destinationCurrency, payment.getAmount());
            int minorUnits = Math.max(Currency.getInstance(destinationCurrency).getDefaultFractionDigits(), 0);
            BigDecimal converted = payment.getAmount().multiply(quote.rate()).setScale(minorUnits, RoundingMode.HALF_EVEN);
            validation.validateConvertedAmount(converted);
            lifecycle.validateWithRate(paymentId, converted, quote);
        } else {
            lifecycle.validateWithRate(paymentId, null, null);
        }
    }

    private void failSafely(Long paymentId, String errorCode, String description) {
        try {
            lifecycle.transition(paymentId, PaymentStatus.FAILED, errorCode, description);
        } catch (Exception failureTransitionException) {
            log.error("Payment {} could not be marked as FAILED", paymentId, failureTransitionException);
        }
    }

    private void awaitStageDelay() {
        long delayMillis = Math.max(properties.processing().stageDelay().toMillis(), 0);
        if (delayMillis <= 0) return;
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private ApiException notFound(Long id) {
        return new ApiException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Payment " + id + " was not found");
    }
}
