package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.HistoryResponse;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.PaymentHistoryRepository;
import com.godsplan.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private PaymentHistoryRepository history;

    @InjectMocks private AuditService service;

    @Test
    void getHistory_paymentExists_returnsHistoryResponse() {
        // Arrange
        Long paymentId = 1L;
        when(payments.existsById(paymentId)).thenReturn(true);
        when(history.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId)).thenReturn(List.of());

        // Act
        HistoryResponse result = service.getHistory(paymentId);

        // Assert
        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.history()).isEmpty();
    }

    @Test
    void getHistory_paymentExists_returnsAllHistoryItems() {
        // Arrange
        Long paymentId = 5L;
        when(payments.existsById(paymentId)).thenReturn(true);
        when(history.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId)).thenReturn(List.of());

        // Act
        HistoryResponse result = service.getHistory(paymentId);

        // Assert
        assertThat(result.paymentId()).isEqualTo(5L);
        verify(history).findByPaymentIdOrderByCreatedAtAscIdAsc(5L);
    }

    @Test
    void getHistory_paymentNotFound_throwsApiException() {
        // Arrange
        Long paymentId = 99L;
        when(payments.existsById(paymentId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.getHistory(paymentId))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
                    assertThat(ex.getMessage()).contains("99");
                });

        verify(history, never()).findByPaymentIdOrderByCreatedAtAscIdAsc(any());
    }

    @Test
    void getHistory_paymentNotFound_doesNotQueryHistory() {
        // Arrange
        when(payments.existsById(0L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.getHistory(0L))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(history);
    }
}
