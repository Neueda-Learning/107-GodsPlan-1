package com.godsplan.payments.repository;

import com.godsplan.payments.domain.CustomerUser;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerUserRepository extends JpaRepository<CustomerUser, Long> {
    Page<CustomerUser> findByActiveTrueAndRoleAndEmailNotIgnoreCase(String role, String email, Pageable pageable);
    Optional<CustomerUser> findByIdAndActiveTrue(Long id);
}

