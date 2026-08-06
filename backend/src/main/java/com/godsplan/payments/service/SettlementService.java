package com.godsplan.payments.service;

import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.AccountRepository;
import com.godsplan.payments.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {
    private static final String INSUFFICIENT_MESSAGE =
            "The selected account does not have sufficient funds to complete this transaction.";

    private final PaymentRepository payments;
    private final AccountRepository accounts;
    private final LifecycleService lifecycle;
    private final EntityManager entityManager;

    public SettlementService(PaymentRepository payments, AccountRepository accounts, LifecycleService lifecycle,
                             EntityManager entityManager) {
        this.payments = payments;
        this.accounts = accounts;
        this.lifecycle = lifecycle;
        this.entityManager = entityManager;
    }

    @Transactional
    public void settle(Long paymentId) {
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessFailure(ErrorCode.PAYMENT_NOT_FOUND, "Payment was not found"));
        Account source = payment.getSourceAccount();
        Account destination = payment.getDestinationAccount();
        List<Account> lockOrder = List.of(source, destination).stream()
                .distinct().sorted(Comparator.comparing(Account::getId)).toList();
        lockOrder.forEach(account -> entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE));
        if (source == null || destination == null || !source.isActive() || !destination.isActive()) {
            throw new BusinessFailure(ErrorCode.INVALID_ACCOUNT, "Source and destination accounts must be active");
        }
        if (source.getAvailableBalance().compareTo(payment.getAmount().add(payment.getFee())) < 0) {
            throw new BusinessFailure(ErrorCode.INSUFFICIENT_FUNDS, INSUFFICIENT_MESSAGE);
        }

        BigDecimal destinationCredit = payment.getDestinationAmount() == null
                ? payment.getAmount() : payment.getDestinationAmount();
        source.debit(payment.getAmount().add(payment.getFee()));
        destination.credit(destinationCredit);
        accounts.saveAll(List.of(source, destination));
        lifecycle.transition(paymentId, PaymentStatus.SENT, null, null);
        lifecycle.transition(paymentId, PaymentStatus.COMPLETED, null, null);
    }
}
