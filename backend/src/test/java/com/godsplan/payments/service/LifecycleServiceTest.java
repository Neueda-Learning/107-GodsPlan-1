package com.godsplan.payments.service;

import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.PaymentHistoryRepository;
import com.godsplan.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LifecycleServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private PaymentHistoryRepository history;

    @InjectMocks private LifecycleService service;

    // ── transition tests ──────────────────────────────────────────────────────

    @Test
    void transition_createdToValidated_transitionsSuccessfully() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.CREATED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);

        // Act
        Payment result = service.transition(1L, PaymentStatus.VALIDATED, null, null);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(result.getErrorCode()).isNull();
        verify(history).save(any());
    }

    @Test
    void transition_validatedToSent_transitionsSuccessfully() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.VALIDATED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);

        // Act
        Payment result = service.transition(1L, PaymentStatus.SENT, null, null);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SENT);
    }

    @Test
    void transition_sentToCompleted_transitionsSuccessfully() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.SENT);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);

        // Act
        Payment result = service.transition(1L, PaymentStatus.COMPLETED, null, null);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void transition_createdToFailed_usesDefaultErrorCodeAndMessage() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.CREATED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);

        // Act
        Payment result = service.transition(1L, PaymentStatus.FAILED, null, null);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PROCESSING_ERROR.name());
        assertThat(result.getErrorDescription()).isEqualTo("Payment processing failed");
    }

    @Test
    void transition_createdToFailed_withCustomErrorCode_usesCustomCode() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.CREATED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);

        // Act
        Payment result = service.transition(1L, PaymentStatus.FAILED,
                ErrorCode.INSUFFICIENT_FUNDS.name(), "Account has no funds");

        // Assert
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS.name());
        assertThat(result.getErrorDescription()).isEqualTo("Account has no funds");
    }

    @Test
    void transition_createdToFailed_withBlankErrorCode_usesDefaultCode() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.CREATED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);

        // Act
        Payment result = service.transition(1L, PaymentStatus.FAILED, "  ", "  ");

        // Assert
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.PROCESSING_ERROR.name());
        assertThat(result.getErrorDescription()).isEqualTo("Payment processing failed");
    }

    @Test
    void transition_toNonFailedStatus_clearsErrorCodeAndDescription() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.CREATED);
        payment.setErrorCode("PREVIOUS_ERROR");
        payment.setErrorDescription("Old error");
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);

        // Act
        Payment result = service.transition(1L, PaymentStatus.VALIDATED, null, null);

        // Assert
        assertThat(result.getErrorCode()).isNull();
        assertThat(result.getErrorDescription()).isNull();
    }

    @Test
    void transition_completedToSent_invalidTransition_throwsApiException() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.COMPLETED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> service.transition(1L, PaymentStatus.SENT, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
                    assertThat(ex.getMessage()).contains("COMPLETED").contains("SENT");
                });

        verify(payments, never()).save(any());
        verifyNoInteractions(history);
    }

    @Test
    void transition_failedToCompleted_invalidTransition_throwsApiException() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.FAILED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> service.transition(1L, PaymentStatus.COMPLETED, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void transition_createdToCompleted_invalidTransition_throwsApiException() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.CREATED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> service.transition(1L, PaymentStatus.COMPLETED, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    void transition_paymentNotFound_throwsApiException() {
        // Arrange
        when(payments.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.transition(99L, PaymentStatus.VALIDATED, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
                    assertThat(ex.getMessage()).contains("99");
                });
    }

    // ── validateWithRate tests ────────────────────────────────────────────────

    @Test
    void validateWithRate_withExchangeRate_setsRateDataAndTransitionsToValidated() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.CREATED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);
        RateQuote quote = new RateQuote(new BigDecimal("1.2500"), Instant.now(), "test-source");

        // Act
        Payment result = service.validateWithRate(1L, new BigDecimal("125.00"), quote);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(result.getDestinationAmount()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(result.getExchangeRate()).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(result.getExchangeRateSource()).isEqualTo("test-source");
        assertThat(result.getExchangeRateFetchedAt()).isNotNull();
    }

    @Test
    void validateWithRate_withNullQuote_transitionsToValidatedWithoutRateData() {
        // Arrange
        Payment payment = buildPaymentWithStatus(1L, PaymentStatus.CREATED);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(payments.save(any())).thenReturn(payment);

        // Act
        Payment result = service.validateWithRate(1L, null, null);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(result.getDestinationAmount()).isNull();
        assertThat(result.getExchangeRate()).isNull();
        assertThat(result.getExchangeRateSource()).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Payment buildPaymentWithStatus(Long id, PaymentStatus status) {
        Account source = mock(Account.class);
        Account destination = mock(Account.class);
        Payment payment = new Payment();
        payment.setId(id);
        payment.setStatus(status);
        payment.setSourceAccount(source);
        payment.setDestinationAccount(destination);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setFee(new BigDecimal("2.00"));
        payment.setCurrency("USD");
        return payment;
    }
}
