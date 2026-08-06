package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.CreatePaymentRequest;
import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.InsufficientBalancePayment;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.InsufficientBalancePaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsufficientBalanceAuditServiceTest {

    @Mock private InsufficientBalancePaymentRepository audits;

    @InjectMocks private InsufficientBalanceAuditService service;

    @Test
    void record_newIdempotencyKey_savesAuditRecord() {
        // Arrange
        String key = "IK-001";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("500.00"), "USD", null, "Test payment");
        Account source = mock(Account.class);
        Account destination = mock(Account.class);
        when(audits.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        // Act
        service.record(key, request, "USD", source, destination);

        // Assert
        verify(audits).save(any(InsufficientBalancePayment.class));
    }

    @Test
    void record_newAuditRecord_setsCorrectErrorCode() {
        // Arrange
        String key = "IK-002";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("200.00"), "USD", null, null);
        Account source = mock(Account.class);
        Account destination = mock(Account.class);
        when(audits.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        // Act
        service.record(key, request, "USD", source, destination);

        // Assert
        ArgumentCaptor<InsufficientBalancePayment> captor =
                ArgumentCaptor.forClass(InsufficientBalancePayment.class);
        verify(audits).save(captor.capture());
        assertThat(captor.getValue().getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS.name());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(key);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(captor.getValue().getCurrency()).isEqualTo("USD");
    }

    @Test
    void record_existingIdempotencyKey_doesNotSaveAgain() {
        // Arrange
        String key = "IK-EXISTING";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("500.00"), "USD", null, null);
        Account source = mock(Account.class);
        Account destination = mock(Account.class);
        when(audits.findByIdempotencyKey(key)).thenReturn(Optional.of(new InsufficientBalancePayment()));

        // Act
        service.record(key, request, "USD", source, destination);

        // Assert
        verify(audits, never()).save(any());
    }

    @Test
    void record_dataIntegrityViolationOnSave_isIgnoredSilently() {
        // Arrange — simulates a concurrent duplicate insert race condition
        String key = "IK-RACE";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("100.00"), "USD", null, null);
        Account source = mock(Account.class);
        Account destination = mock(Account.class);
        when(audits.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(audits.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        // Act & Assert — must not propagate the exception
        assertThatNoException().isThrownBy(
                () -> service.record(key, request, "USD", source, destination));
    }

    @Test
    void record_withNullReference_savesNullReference() {
        // Arrange
        String key = "IK-NULL-REF";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("200.00"), "USD", null, null);
        Account source = mock(Account.class);
        Account destination = mock(Account.class);
        when(audits.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        // Act
        service.record(key, request, "USD", source, destination);

        // Assert
        ArgumentCaptor<InsufficientBalancePayment> captor =
                ArgumentCaptor.forClass(InsufficientBalancePayment.class);
        verify(audits).save(captor.capture());
        assertThat(captor.getValue().getReference()).isNull();
        assertThat(captor.getValue().getIntermediaryBank()).isNull();
    }

    @Test
    void record_withBlankReference_savesNullReference() {
        // Arrange
        String key = "IK-BLANK-REF";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("200.00"), "USD", "  ", "  ");
        Account source = mock(Account.class);
        Account destination = mock(Account.class);
        when(audits.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        // Act
        service.record(key, request, "USD", source, destination);

        // Assert
        ArgumentCaptor<InsufficientBalancePayment> captor =
                ArgumentCaptor.forClass(InsufficientBalancePayment.class);
        verify(audits).save(captor.capture());
        assertThat(captor.getValue().getReference()).isNull();
        assertThat(captor.getValue().getIntermediaryBank()).isNull();
    }
}
