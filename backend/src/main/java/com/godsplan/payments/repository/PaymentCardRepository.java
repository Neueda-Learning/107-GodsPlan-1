package com.godsplan.payments.repository;

import com.godsplan.payments.domain.PaymentCard;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, Long> {
    Optional<PaymentCard> findFirstByCustomerIdAndActiveTrueOrderByIdAsc(Long customerId);
}
