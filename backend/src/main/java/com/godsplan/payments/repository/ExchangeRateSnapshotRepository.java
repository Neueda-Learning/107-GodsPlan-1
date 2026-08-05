package com.godsplan.payments.repository;

import com.godsplan.payments.domain.ExchangeRateSnapshot;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateSnapshotRepository extends JpaRepository<ExchangeRateSnapshot, Long> {
    boolean existsByBaseCurrencyAndQuoteCurrencyAndSourceAndFetchedAt(
            String baseCurrency, String quoteCurrency, String source, Instant fetchedAt);
}
