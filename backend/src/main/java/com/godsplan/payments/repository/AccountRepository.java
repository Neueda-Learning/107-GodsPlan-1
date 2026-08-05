package com.godsplan.payments.repository;

import com.godsplan.payments.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {}

