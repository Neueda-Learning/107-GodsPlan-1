package com.godsplan.payments.repository;

import com.godsplan.payments.domain.InsufficientBalancePayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsufficientBalancePaymentRepository extends JpaRepository<InsufficientBalancePayment, Long> {
    Optional<InsufficientBalancePayment> findByIdempotencyKey(String idempotencyKey);
}
