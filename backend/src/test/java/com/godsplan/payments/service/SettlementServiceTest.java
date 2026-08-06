package com.godsplan.payments.service;

import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.AccountRepository;
import com.godsplan.payments.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private AccountRepository accounts;
    @Mock private LifecycleService lifecycle;
    @Mock private EntityManager entityManager;

    @InjectMocks private SettlementService service;

    @Test
    void settle_sufficientBalanceSameCurrency_completesSuccessfully() {
        // Arrange
        Account source = mockAccount(1L, new BigDecimal("1000.00"), true);
        Account destination = mockAccount(2L, new BigDecimal("500.00"), true);
        Payment payment = buildPayment(source, destination,
                new BigDecimal("100.00"), new BigDecimal("2.00"), null);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(lifecycle.transition(anyLong(), any(), any(), any())).thenReturn(payment);

        // Act
        assertThatNoException().isThrownBy(() -> service.settle(1L));

        // Assert
        verify(accounts).saveAll(anyList());
        verify(lifecycle).transition(1L, PaymentStatus.SENT, null, null);
        verify(lifecycle).transition(1L, PaymentStatus.COMPLETED, null, null);
    }

    @Test
    void settle_sufficientBalance_debitsSourceAndCreditsDestination() {
        // Arrange
        Account source = mockAccount(1L, new BigDecimal("1000.00"), true);
        Account destination = mockAccount(2L, new BigDecimal("0.00"), true);
        Payment payment = buildPayment(source, destination,
                new BigDecimal("100.00"), new BigDecimal("2.00"), null);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(lifecycle.transition(anyLong(), any(), any(), any())).thenReturn(payment);

        // Act
        service.settle(1L);

        // Assert — debit = amount + fee = 102, credit = amount (no destination amount) = 100
        verify(source).debit(new BigDecimal("102.00"));
        verify(destination).credit(new BigDecimal("100.00"));
    }

    @Test
    void settle_withDestinationAmount_creditsConvertedAmount() {
        // Arrange
        Account source = mockAccount(1L, new BigDecimal("1000.00"), true);
        Account destination = mockAccount(2L, new BigDecimal("0.00"), true);
        Payment payment = buildPayment(source, destination,
                new BigDecimal("100.00"), new BigDecimal("2.00"), new BigDecimal("90.00"));
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(lifecycle.transition(anyLong(), any(), any(), any())).thenReturn(payment);

        // Act
        service.settle(1L);

        // Assert — credit = destinationAmount = 90
        verify(destination).credit(new BigDecimal("90.00"));
        verify(source).debit(new BigDecimal("102.00")); // amount + fee
    }

    @Test
    void settle_paymentNotFound_throwsBusinessFailure() {
        // Arrange
        when(payments.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.settle(99L))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    void settle_insufficientBalance_throwsBusinessFailure() {
        // Arrange — balance (50) < amount + fee (102)
        Account source = mockAccount(1L, new BigDecimal("50.00"), true);
        Account destination = mockAccount(2L, new BigDecimal("0.00"), true);
        Payment payment = buildPayment(source, destination,
                new BigDecimal("100.00"), new BigDecimal("2.00"), null);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> service.settle(1L))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));

        verify(accounts, never()).saveAll(any());
        verify(lifecycle, never()).transition(anyLong(), any(), any(), any());
    }

    @Test
    void settle_inactiveSourceAccount_throwsBusinessFailure() {
        // Arrange
        Account source = mockAccount(1L, new BigDecimal("1000.00"), false);
        Account destination = mockAccount(2L, new BigDecimal("0.00"), true);
        Payment payment = buildPayment(source, destination,
                new BigDecimal("100.00"), new BigDecimal("2.00"), null);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> service.settle(1L))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.INVALID_ACCOUNT));
    }

    @Test
    void settle_inactiveDestinationAccount_throwsBusinessFailure() {
        // Arrange
        Account source = mockAccount(1L, new BigDecimal("1000.00"), true);
        Account destination = mockAccount(2L, new BigDecimal("0.00"), false);
        Payment payment = buildPayment(source, destination,
                new BigDecimal("100.00"), new BigDecimal("2.00"), null);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThatThrownBy(() -> service.settle(1L))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.INVALID_ACCOUNT));
    }

    @Test
    void settle_exactBalance_succeedsWhenBalanceEqualsAmountPlusFee() {
        // Arrange — balance exactly equals amount + fee
        Account source = mockAccount(1L, new BigDecimal("102.00"), true);
        Account destination = mockAccount(2L, new BigDecimal("0.00"), true);
        Payment payment = buildPayment(source, destination,
                new BigDecimal("100.00"), new BigDecimal("2.00"), null);
        when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(lifecycle.transition(anyLong(), any(), any(), any())).thenReturn(payment);

        // Act & Assert
        assertThatNoException().isThrownBy(() -> service.settle(1L));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Account mockAccount(Long id, BigDecimal balance, boolean active) {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(id);
        when(account.getAvailableBalance()).thenReturn(balance);
        when(account.isActive()).thenReturn(active);
        return account;
    }

    private Payment buildPayment(Account source, Account destination,
                                  BigDecimal amount, BigDecimal fee, BigDecimal destinationAmount) {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setSourceAccount(source);
        payment.setDestinationAccount(destination);
        payment.setAmount(amount);
        payment.setFee(fee);
        payment.setDestinationAmount(destinationAmount);
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.VALIDATED);
        return payment;
    }
}
