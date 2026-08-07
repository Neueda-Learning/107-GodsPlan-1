package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.CreatePaymentRequest;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.api.dto.PaymentResponse;
import com.godsplan.payments.api.dto.StatusTransitionRequest;
import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.InsufficientBalancePayment;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.AccountRepository;
import com.godsplan.payments.repository.InsufficientBalancePaymentRepository;
import com.godsplan.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private AccountRepository accounts;
    @Mock private ValidationService validation;
    @Mock private InitialPaymentWriter initialWriter;
    @Mock private LifecycleService lifecycle;
    @Mock private PaymentProcessingWorker processor;
    @Mock private InsufficientBalanceAuditService insufficientAudit;
    @Mock private InsufficientBalancePaymentRepository insufficientAudits;

    @InjectMocks private PaymentService service;

    private Account sourceAccount;
    private Account destAccount;

    @BeforeEach
    void setUp() {
        sourceAccount = mock(Account.class);
        when(sourceAccount.getId()).thenReturn(10L);
        when(sourceAccount.getAvailableBalance()).thenReturn(new BigDecimal("1000.00"));
        when(sourceAccount.getCurrency()).thenReturn("USD");

        destAccount = mock(Account.class);
        when(destAccount.getId()).thenReturn(20L);
        when(destAccount.getCurrency()).thenReturn("USD");

        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                10L, 1L, "CUSTOMER")).thenReturn(Optional.of(sourceAccount));
        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                20L, 2L, "CUSTOMER")).thenReturn(Optional.of(destAccount));
    }

    // ── create: idempotency key validation ───────────────────────────────────

    @Test
    void create_nullIdempotencyKey_throwsValidationException() {
        assertThatThrownBy(() -> service.create(null, validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void create_blankIdempotencyKey_throwsValidationException() {
        assertThatThrownBy(() -> service.create("   ", validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void create_emptyIdempotencyKey_throwsValidationException() {
        assertThatThrownBy(() -> service.create("", validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void create_tooLongIdempotencyKey_throwsValidationException() {
        String key = "A".repeat(81); // > 80 characters
        assertThatThrownBy(() -> service.create(key, validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void create_maxLengthIdempotencyKey_proceedsPastKeyValidation() {
        String key = "A".repeat(80); // exactly 80 — allowed
        when(insufficientAudits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        // Returning existing payment so we don't need to set up full pipeline
        Payment existing = buildCompletedPayment();
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        PaymentService.CreateResult result = service.create(key, validRequest());

        assertThat(result.created()).isFalse(); // idempotent replay
    }

    // ── create: duplicate / idempotent replay ────────────────────────────────

    @Test
    void create_existingInsufficientAuditRecord_throwsInsufficientFundsException() {
        // Arrange
        String key = "IK-INSUF";
        when(insufficientAudits.findByIdempotencyKey(key))
                .thenReturn(Optional.of(new InsufficientBalancePayment()));

        // Act & Assert
        assertThatThrownBy(() -> service.create(key, validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));

        verifyNoInteractions(initialWriter);
    }

    @Test
    void create_existingCompletedPaymentSameKey_returnsExistingWithoutCreating() {
        // Arrange
        String key = "IK-REPLAY";
        when(insufficientAudits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.of(buildCompletedPayment()));

        // Act
        PaymentService.CreateResult result = service.create(key, validRequest());

        // Assert — idempotent: no new payment created
        assertThat(result.created()).isFalse();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.COMPLETED);
        verifyNoInteractions(initialWriter);
    }

    @Test
    void create_existingFailedPaymentWithInsufficientFundsCode_throwsInsufficientFunds() {
        // Arrange
        String key = "IK-FAILED-INSUF";
        Payment failedPayment = new Payment();
        failedPayment.setId(5L);
        failedPayment.setStatus(PaymentStatus.FAILED);
        failedPayment.setErrorCode(ErrorCode.INSUFFICIENT_FUNDS.name());
        failedPayment.setAmount(new BigDecimal("100.00"));
        failedPayment.setFee(new BigDecimal("2.00"));
        failedPayment.setCurrency("USD");
        failedPayment.setSourceAccount(sourceAccount);
        failedPayment.setDestinationAccount(destAccount);
        when(insufficientAudits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.of(failedPayment));

        // Act & Assert
        assertThatThrownBy(() -> service.create(key, validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
    }

    // ── create: business rule violations ─────────────────────────────────────

    @Test
    void create_sameSenderAndReceiverCustomer_throwsInvalidAccountException() {
        // Arrange — sender and receiver have the same customerId
        String key = "IK-SAME-CUST";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 10L, 1L, 20L, // both customer IDs are 1
                new BigDecimal("100.00"), "USD", null, null);
        when(insufficientAudits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(validation.normalizeAndValidateCurrency("USD")).thenReturn("USD");

        // Act & Assert
        assertThatThrownBy(() -> service.create(key, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INVALID_ACCOUNT));
    }

    @Test
    void create_sameSourceAndDestinationAccount_throwsInvalidAccountException() {
        // Arrange — different customers but resolving to the same account
        String key = "IK-SAME-ACC";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 10L, 2L, 10L, // same account id 10L for both
                new BigDecimal("100.00"), "USD", null, null);
        Account sharedAccount = mock(Account.class);
        when(sharedAccount.getId()).thenReturn(10L);
        when(insufficientAudits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(validation.normalizeAndValidateCurrency("USD")).thenReturn("USD");
        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                10L, 1L, "CUSTOMER")).thenReturn(Optional.of(sharedAccount));
        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                10L, 2L, "CUSTOMER")).thenReturn(Optional.of(sharedAccount));

        // Act & Assert
        assertThatThrownBy(() -> service.create(key, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INVALID_ACCOUNT));
    }

    @Test
    void create_insufficientBalance_recordsAuditAndThrowsException() {
        // Arrange — balance (50) < amount + fee
        String key = "IK-LOW-BAL";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 10L, 2L, 20L,
                new BigDecimal("5000.00"), "USD", null, null);

        Account poorSource = mock(Account.class);
        when(poorSource.getId()).thenReturn(10L);
        when(poorSource.getAvailableBalance()).thenReturn(new BigDecimal("50.00"));

        when(insufficientAudits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(validation.normalizeAndValidateCurrency("USD")).thenReturn("USD");
        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                10L, 1L, "CUSTOMER")).thenReturn(Optional.of(poorSource));
        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                20L, 2L, "CUSTOMER")).thenReturn(Optional.of(destAccount));

        // Act & Assert
        assertThatThrownBy(() -> service.create(key, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));

        verify(insufficientAudit).record(eq(key), eq(request), eq("USD"), eq(poorSource), eq(destAccount));
    }

    @Test
    void create_senderAccountNotFound_throwsInvalidAccountException() {
        // Arrange
        String key = "IK-NO-ACC";
        when(insufficientAudits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(validation.normalizeAndValidateCurrency("USD")).thenReturn("USD");
        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                10L, 1L, "CUSTOMER")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.create(key, validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INVALID_ACCOUNT));
    }

    // ── create: happy path ────────────────────────────────────────────────────

    @Test
    void create_validNewPaymentSameCurrency_returnsCreatedImmediatelyAndTriggersAsyncProcessing() {
        // Arrange
        String key = "IK-NEW";
        CreatePaymentRequest request = validRequest();

        Payment createdPayment = new Payment();
        createdPayment.setId(1L);
        createdPayment.setCurrency("USD");
        createdPayment.setSourceAccount(sourceAccount);
        createdPayment.setDestinationAccount(destAccount);
        createdPayment.setAmount(new BigDecimal("100.00"));
        createdPayment.setFee(new BigDecimal("2.00"));
        createdPayment.setStatus(PaymentStatus.CREATED);

        when(insufficientAudits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(validation.normalizeAndValidateCurrency("USD")).thenReturn("USD");
        when(initialWriter.create(eq(key), eq(request), eq("USD"), eq(sourceAccount), eq(destAccount)))
                .thenReturn(createdPayment);

        // Act
        PaymentService.CreateResult result = service.create(key, request);

        // Assert — the response reflects the CREATED payment immediately; remaining
        // lifecycle stages are handed off to the async worker rather than run inline.
        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.CREATED);
        verify(processor).processAsync(1L);
        verifyNoInteractions(lifecycle);
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_existingPayment_returnsPaymentResponse() {
        // Arrange
        when(payments.findById(1L)).thenReturn(Optional.of(buildCompletedPayment()));

        // Act
        PaymentResponse result = service.get(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void get_paymentNotFound_throwsApiException() {
        // Arrange
        when(payments.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
                    assertThat(ex.getMessage()).contains("99");
                });
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_withNoStatusFilter_returnsAllPayments() {
        // Arrange
        Page<Payment> page = new PageImpl<>(List.of(buildCompletedPayment()));
        when(payments.findAll(any(Pageable.class))).thenReturn(page);

        // Act
        PageResponse<PaymentResponse> result = service.list(null, PageRequest.of(0, 10));

        // Assert
        assertThat(result.content()).hasSize(1);
        verify(payments).findAll(any(Pageable.class));
        verify(payments, never()).findByStatus(any(), any());
    }

    @Test
    void list_withStatusFilter_usesStatusQuery() {
        // Arrange
        Page<Payment> page = new PageImpl<>(List.of());
        when(payments.findByStatus(eq(PaymentStatus.FAILED), any(Pageable.class))).thenReturn(page);

        // Act
        PageResponse<PaymentResponse> result = service.list(PaymentStatus.FAILED, PageRequest.of(0, 10));

        // Assert
        assertThat(result.content()).isEmpty();
        verify(payments).findByStatus(eq(PaymentStatus.FAILED), any(Pageable.class));
        verify(payments, never()).findAll(any(Pageable.class));
    }

    @Test
    void list_emptyResult_returnsEmptyPage() {
        // Arrange
        when(payments.findAll(any(Pageable.class))).thenReturn(Page.empty());

        // Act
        PageResponse<PaymentResponse> result = service.list(null, PageRequest.of(0, 10));

        // Assert
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    // ── transition ────────────────────────────────────────────────────────────

    @Test
    void transition_delegatesToLifecycleService() {
        // Arrange
        Payment payment = buildCompletedPayment();
        StatusTransitionRequest request = new StatusTransitionRequest(
                PaymentStatus.VALIDATED, null, null);
        when(lifecycle.transition(1L, PaymentStatus.VALIDATED, null, null)).thenReturn(payment);

        // Act
        PaymentResponse result = service.transition(1L, request);

        // Assert
        assertThat(result).isNotNull();
        verify(lifecycle).transition(1L, PaymentStatus.VALIDATED, null, null);
    }

    @Test
    void transition_withErrorCode_passesErrorCodeToLifecycle() {
        // Arrange
        Payment payment = buildCompletedPayment();
        StatusTransitionRequest request = new StatusTransitionRequest(
                PaymentStatus.FAILED, "PROCESSING_ERROR", "Something went wrong");
        when(lifecycle.transition(1L, PaymentStatus.FAILED, "PROCESSING_ERROR", "Something went wrong"))
                .thenReturn(payment);

        // Act
        service.transition(1L, request);

        // Assert
        verify(lifecycle).transition(1L, PaymentStatus.FAILED, "PROCESSING_ERROR", "Something went wrong");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreatePaymentRequest validRequest() {
        return new CreatePaymentRequest(1L, 10L, 2L, 20L,
                new BigDecimal("100.00"), "USD", null, null);
    }

    private Payment buildCompletedPayment() {
        Payment p = new Payment();
        p.setId(1L);
        p.setAmount(new BigDecimal("100.00"));
        p.setFee(new BigDecimal("2.00"));
        p.setCurrency("USD");
        p.setStatus(PaymentStatus.COMPLETED);
        p.setSourceAccount(sourceAccount);
        p.setDestinationAccount(destAccount);
        return p;
    }
}
