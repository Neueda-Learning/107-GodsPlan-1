package com.godsplan.payments.repository;

import com.godsplan.payments.domain.PaymentStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {
    List<PaymentStatusHistory> findByPaymentIdOrderByCreatedAtAscIdAsc(Long paymentId);
}

