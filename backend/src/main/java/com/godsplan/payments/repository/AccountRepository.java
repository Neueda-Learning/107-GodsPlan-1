package com.godsplan.payments.repository;

import com.godsplan.payments.domain.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByCustomerIdOrderByIdAsc(Long customerId);
    List<Account> findByCustomerIdAndActiveTrueOrderByIdAsc(Long customerId);
    Optional<Account> findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
            Long id, Long customerId, String customerRole);
}
