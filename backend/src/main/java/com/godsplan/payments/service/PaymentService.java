package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.CreatePaymentRequest;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.api.dto.PaymentResponse;
import com.godsplan.payments.api.dto.StatusTransitionRequest;
import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.AccountRepository;
import com.godsplan.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository payments;
    private final AccountRepository accounts;
    private final ValidationService validation;
    private final ExchangeRateService exchangeRates;
    private final InitialPaymentWriter initialWriter;
    private final LifecycleService lifecycle;

    public PaymentService(PaymentRepository payments, AccountRepository accounts, ValidationService validation,
                          ExchangeRateService exchangeRates, InitialPaymentWriter initialWriter,
                          LifecycleService lifecycle) {
        this.payments = payments;
        this.accounts = accounts;
        this.validation = validation;
        this.exchangeRates = exchangeRates;
        this.initialWriter = initialWriter;
        this.lifecycle = lifecycle;
    }

    public CreateResult create(String idempotencyKey, CreatePaymentRequest request) {
        validateKey(idempotencyKey);
        Optional<Payment> existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return new CreateResult(PaymentResponse.from(existing.get()), false);

        validation.validateAmountShape(request.amount());
        String currency = validation.normalizeAndValidateCurrency(request.currency());
        Account source = account(request.sourceAccountId(), "source");
        Account destination = account(request.destinationAccountId(), "destination");
        if (source.getId().equals(destination.getId())) {
            throw new ApiException(ErrorCode.INVALID_ACCOUNT, HttpStatus.BAD_REQUEST,
                    "Source and destination accounts must be different");
        }

        Payment created;
        try {
            created = initialWriter.create(idempotencyKey, request, currency, source, destination);
        } catch (DataIntegrityViolationException duplicateRace) {
            Payment winner = payments.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> duplicateRace);
            return new CreateResult(PaymentResponse.from(winner), false);
        }
        log.info("Created payment {} with idempotency key {}", created.getId(), idempotencyKey);
        process(created);
        return new CreateResult(get(created.getId()), true);
    }

    private void process(Payment payment) {
        try {
            validation.validateBusiness(payment);
            String destinationCurrency = payment.getDestinationAccount().getCurrency();
            if (!destinationCurrency.equals(payment.getCurrency())) {
                RateQuote quote = exchangeRates.getRate(payment.getCurrency(), destinationCurrency, payment.getAmount());
                int minorUnits = Math.max(Currency.getInstance(destinationCurrency).getDefaultFractionDigits(), 0);
                BigDecimal converted = payment.getAmount().multiply(quote.rate()).setScale(minorUnits, RoundingMode.HALF_EVEN);
                validation.validateConvertedAmount(converted);
                lifecycle.validateWithRate(payment.getId(), converted, quote);
            } else {
                lifecycle.validateWithRate(payment.getId(), null, null);
            }
            lifecycle.transition(payment.getId(), PaymentStatus.SENT, null, null);
            lifecycle.transition(payment.getId(), PaymentStatus.COMPLETED, null, null);
        } catch (BusinessFailure failure) {
            log.info("Payment {} failed validation: {}", payment.getId(), failure.getCode());
            lifecycle.transition(payment.getId(), PaymentStatus.FAILED,
                    failure.getCode().name(), failure.getMessage());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Payment {} processing failed", payment.getId(), exception);
            lifecycle.transition(payment.getId(), PaymentStatus.FAILED,
                    ErrorCode.PROCESSING_ERROR.name(), "Payment processing could not be completed");
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return PaymentResponse.from(payments.findById(id).orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(PaymentStatus status, Pageable pageable) {
        Page<Payment> result = status == null ? payments.findAll(pageable) : payments.findByStatus(status, pageable);
        return PageResponse.from(result.map(PaymentResponse::from));
    }

    public PaymentResponse transition(Long id, StatusTransitionRequest request) {
        return PaymentResponse.from(lifecycle.transition(id, request.toStatus(),
                request.errorCode(), request.errorDescription()));
    }

    private Account account(Long id, String role) {
        return accounts.findById(id).orElseThrow(() -> new ApiException(ErrorCode.INVALID_ACCOUNT,
                HttpStatus.BAD_REQUEST, "The " + role + " account does not exist"));
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 80) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain between 1 and 80 characters");
        }
    }

    private ApiException notFound(Long id) {
        return new ApiException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Payment " + id + " was not found");
    }

    public record CreateResult(PaymentResponse payment, boolean created) {}
}

