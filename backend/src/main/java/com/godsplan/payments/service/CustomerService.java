package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.CustomerResponse;
import com.godsplan.payments.api.dto.CustomerTransactionResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.api.dto.PaymentAccountOptionResponse;
import com.godsplan.payments.api.dto.PaymentCustomerOptionResponse;
import com.godsplan.payments.domain.CustomerUser;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.AccountRepository;
import com.godsplan.payments.repository.CustomerUserRepository;
import com.godsplan.payments.repository.PaymentCardRepository;
import com.godsplan.payments.repository.PaymentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {
    private final CustomerUserRepository customers;
    private final AccountRepository accounts;
    private final PaymentCardRepository cards;
    private final PaymentRepository payments;

    public CustomerService(CustomerUserRepository customers, AccountRepository accounts,
                           PaymentCardRepository cards, PaymentRepository payments) {
        this.customers = customers;
        this.accounts = accounts;
        this.cards = cards;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String currentUserEmail, Pageable pageable) {
        return PageResponse.from(customers
                .findByActiveTrueAndRoleAndEmailNotIgnoreCase("CUSTOMER", currentUserEmail, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerTransactionResponse> transactions(Long customerId, String currentUserEmail,
                                                                   Pageable pageable) {
        CustomerUser customer = customers.findByIdAndActiveTrue(customerId).orElseThrow(() -> notFound(customerId));
        if (!"CUSTOMER".equals(customer.getRole()) || customer.getEmail().equalsIgnoreCase(currentUserEmail)) {
            throw notFound(customerId);
        }
        return PageResponse.from(payments.findCustomerTransactions(customerId, pageable)
                .map(CustomerTransactionResponse::from));
    }

    @Transactional(readOnly = true)
    public List<PaymentCustomerOptionResponse> paymentOptions(String currentUserEmail) {
        return customers
                .findByActiveTrueAndRoleAndEmailNotIgnoreCaseOrderByFullNameAsc("CUSTOMER", currentUserEmail)
                .stream()
                .map(customer -> new PaymentCustomerOptionResponse(customer.getId(), customer.getFullName(),
                        customer.getCountry()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentAccountOptionResponse> accountOptions(Long customerId, String currentUserEmail) {
        customer(customerId, currentUserEmail);
        return accounts.findByCustomerIdAndActiveTrueOrderByIdAsc(customerId).stream()
                .map(account -> {
                    String masked = maskAccount(account.getAccountNumber());
                    String label = account.getAccountType() + " · " + masked + " · " + account.getCurrency();
                    return new PaymentAccountOptionResponse(account.getId(), account.getAccountType(), masked,
                            account.getCurrency(), label);
                })
                .toList();
    }

    private CustomerResponse toResponse(CustomerUser customer) {
        var card = cards.findFirstByCustomerIdAndActiveTrueOrderByIdAsc(customer.getId());
        String maskedNumber = card.map(value -> mask(value.getLastFour())).orElse(null);
        String brand = card.map(value -> value.getBrand()).orElse(null);
        var accountDetails = accounts.findByCustomerIdOrderByIdAsc(customer.getId()).stream()
                .map(account -> new CustomerResponse.AccountDetails(account.getId(), account.getAccountType(),
                        maskAccount(account.getAccountNumber()), account.getCurrency(), account.isActive()))
                .toList();
        return new CustomerResponse(customer.getId(), customer.getFullName(), customer.getEmail(),
                maskedNumber, brand, accountDetails);
    }

    private String mask(String lastFour) {
        return "XXXX XXXX XXXX " + lastFour;
    }

    private String maskAccount(String accountNumber) {
        String lastFour = accountNumber.substring(Math.max(0, accountNumber.length() - 4));
        return "XXXX " + lastFour;
    }

    private CustomerUser customer(Long id, String currentUserEmail) {
        CustomerUser customer = customers.findByIdAndActiveTrue(id).orElseThrow(() -> notFound(id));
        if (!"CUSTOMER".equals(customer.getRole()) || customer.getEmail().equalsIgnoreCase(currentUserEmail)) {
            throw notFound(id);
        }
        return customer;
    }

    private ApiException notFound(Long id) {
        return new ApiException(ErrorCode.CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Customer " + id + " was not found");
    }
}
