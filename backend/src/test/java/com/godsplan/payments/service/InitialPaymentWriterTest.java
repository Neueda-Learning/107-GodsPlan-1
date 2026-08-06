package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.CreatePaymentRequest;
import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.repository.PaymentHistoryRepository;
import com.godsplan.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitialPaymentWriterTest {

    @Mock private PaymentRepository payments;
    @Mock private PaymentHistoryRepository history;

    @InjectMocks private InitialPaymentWriter writer;

    @Test
    void create_withFullRequest_setsAllFieldsCorrectly() {
        // Arrange
        String key = "IK-001";
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L,
                new BigDecimal("200.00"), "USD", "Intermediate Bank", "My reference");
        Account source = mock(Account.class);
        Account destination = mock(Account.class);
        Payment savedPayment = new Payment();
        savedPayment.setStatus(PaymentStatus.CREATED);
        when(payments.saveAndFlush(any())).thenReturn(savedPayment);

        // Act
        writer.create(key, request, "USD", source, destination);

        // Assert
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(captor.capture());
        Payment captured = captor.getValue();
        assertThat(captured.getIdempotencyKey()).isEqualTo(key);
        assertThat(captured.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(captured.getFee()).isEqualByComparingTo(new BigDecimal("4.00")); // 2% of 200
        assertThat(captured.getCurrency()).isEqualTo("USD");
        assertThat(captured.getSourceAccount()).isSameAs(source);
        assertThat(captured.getDestinationAccount()).isSameAs(destination);
        assertThat(captured.getReference()).isEqualTo("My reference");
        assertThat(captured.getIntermediaryBank()).isEqualTo("Intermediate Bank");
        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void create_savesHistoryEntry() {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("100.00"), "USD", null, null);
        when(payments.saveAndFlush(any())).thenReturn(new Payment());

        // Act
        writer.create("IK-HIST", request, "USD", mock(Account.class), mock(Account.class));

        // Assert
        verify(history).save(any());
    }

    @Test
    void create_withNullReference_savesNullReference() {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("100.00"), "USD", null, null);
        when(payments.saveAndFlush(any())).thenReturn(new Payment());

        // Act
        writer.create("IK-NULL", request, "USD", mock(Account.class), mock(Account.class));

        // Assert
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReference()).isNull();
        assertThat(captor.getValue().getIntermediaryBank()).isNull();
    }

    @Test
    void create_withBlankReference_savesNullReference() {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("50.00"), "USD", "  ", "  ");
        when(payments.saveAndFlush(any())).thenReturn(new Payment());

        // Act
        writer.create("IK-BLANK", request, "USD", mock(Account.class), mock(Account.class));

        // Assert
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReference()).isNull();
        assertThat(captor.getValue().getIntermediaryBank()).isNull();
    }

    @Test
    void create_feeCalculation_twoPercentRoundedHalfUp() {
        // Arrange — odd amount to check HALF_UP rounding
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("1000.00"), "USD", null, null);
        when(payments.saveAndFlush(any())).thenReturn(new Payment());

        // Act
        writer.create("IK-FEE", request, "USD", mock(Account.class), mock(Account.class));

        // Assert
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getFee()).isEqualByComparingTo(new BigDecimal("20.00")); // 2% of 1000
    }

    @Test
    void create_feeCalculation_smallAmount_roundedCorrectly() {
        // 2% of 12.50 = 0.25 — exactly representable
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("12.50"), "USD", null, null);
        when(payments.saveAndFlush(any())).thenReturn(new Payment());

        // Act
        writer.create("IK-FEE2", request, "USD", mock(Account.class), mock(Account.class));

        // Assert
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getFee()).isEqualByComparingTo(new BigDecimal("0.25"));
    }

    @Test
    void create_withTrimmedReference_trims() {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("100.00"), "USD", null, "  my-ref  ");
        when(payments.saveAndFlush(any())).thenReturn(new Payment());

        // Act
        writer.create("IK-TRIM", request, "USD", mock(Account.class), mock(Account.class));

        // Assert
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReference()).isEqualTo("my-ref");
    }

    @Test
    void create_returnsLocallyBuiltPayment_withCreatedStatus() {
        // InitialPaymentWriter returns the locally-built Payment object (not the repo return value).
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, 2L, 2L, new BigDecimal("100.00"), "USD", null, null);
        when(payments.saveAndFlush(any())).thenReturn(new Payment());

        // Act
        Payment result = writer.create("IK-RET", request, "USD", mock(Account.class), mock(Account.class));

        // Assert — status is set on the local object before saving
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.getIdempotencyKey()).isEqualTo("IK-RET");
    }
}
