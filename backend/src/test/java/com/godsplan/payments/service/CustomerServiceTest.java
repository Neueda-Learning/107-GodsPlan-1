package com.godsplan.payments.service;

import com.godsplan.payments.api.dto.CustomerResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.api.dto.PaymentAccountOptionResponse;
import com.godsplan.payments.api.dto.PaymentCustomerOptionResponse;
import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.CustomerUser;
import com.godsplan.payments.domain.PaymentCard;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.ErrorCode;
import com.godsplan.payments.repository.AccountRepository;
import com.godsplan.payments.repository.CustomerUserRepository;
import com.godsplan.payments.repository.PaymentCardRepository;
import com.godsplan.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceTest {

    @Mock private CustomerUserRepository customers;
    @Mock private AccountRepository accounts;
    @Mock private PaymentCardRepository cards;
    @Mock private PaymentRepository payments;

    @InjectMocks private CustomerService service;

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_returnsPageOfCustomers() {
        // Arrange
        CustomerUser customer = mockCustomer(1L, "Alice Smith", "alice@example.com", "US", "CUSTOMER");
        Page<CustomerUser> page = new PageImpl<>(List.of(customer));
        Account account = mockAccount(1L, "Checking", "ACC-001", "USD", new BigDecimal("500.00"), true);
        when(customers.findByActiveTrueAndRoleOrderByFullNameAsc(eq("CUSTOMER"), any(Pageable.class)))
                .thenReturn(page);
        when(accounts.findByCustomerIdOrderByIdAsc(1L)).thenReturn(List.of(account));
        when(cards.findFirstByCustomerIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(Optional.empty());

        // Act
        PageResponse<CustomerResponse> result = service.list(PageRequest.of(0, 10));

        // Assert
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).fullName()).isEqualTo("Alice Smith");
        assertThat(result.content().get(0).email()).isEqualTo("alice@example.com");
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void list_customerWithCard_includesCardInfo() {
        // Arrange
        CustomerUser customer = mockCustomer(1L, "Bob Jones", "bob@example.com", "GB", "CUSTOMER");
        PaymentCard card = mock(PaymentCard.class);
        when(card.getLastFour()).thenReturn("1234");
        when(card.getBrand()).thenReturn("Visa");
        when(customers.findByActiveTrueAndRoleOrderByFullNameAsc(eq("CUSTOMER"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));
        when(accounts.findByCustomerIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(cards.findFirstByCustomerIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(Optional.of(card));

        // Act
        PageResponse<CustomerResponse> result = service.list(PageRequest.of(0, 10));

        // Assert
        assertThat(result.content().get(0).cardNumber()).isEqualTo("1234");
        assertThat(result.content().get(0).cardBrand()).isEqualTo("Visa");
    }

    @Test
    void list_customerWithMultipleAccounts_includesAllAccountDetails() {
        // Arrange
        CustomerUser customer = mockCustomer(1L, "Carol", "carol@example.com", "US", "CUSTOMER");
        Account usdAccount = mockAccount(10L, "Checking", "ACC-USD", "USD", new BigDecimal("1000.00"), true);
        Account eurAccount = mockAccount(11L, "Savings", "ACC-EUR", "EUR", new BigDecimal("500.00"), true);
        when(customers.findByActiveTrueAndRoleOrderByFullNameAsc(eq("CUSTOMER"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));
        when(accounts.findByCustomerIdOrderByIdAsc(1L)).thenReturn(List.of(usdAccount, eurAccount));
        when(cards.findFirstByCustomerIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(Optional.empty());

        // Act
        PageResponse<CustomerResponse> result = service.list(PageRequest.of(0, 10));

        // Assert
        assertThat(result.content().get(0).accounts()).hasSize(2);
    }

    // ── transactions ─────────────────────────────────────────────────────────

    @Test
    void transactions_validCustomer_returnsPageResponse() {
        // Arrange
        CustomerUser customer = mockCustomer(1L, "Alice", "alice@example.com", "US", "CUSTOMER");
        when(customers.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(customer));
        when(payments.findCustomerTransactions(eq(1L), any(Pageable.class))).thenReturn(Page.empty());

        // Act
        PageResponse<?> result = service.transactions(1L, PageRequest.of(0, 5));

        // Assert
        assertThat(result.content()).isEmpty();
    }

    @Test
    void transactions_customerNotFound_throwsApiException() {
        // Arrange
        when(customers.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.transactions(99L, PageRequest.of(0, 5)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Test
    void transactions_nonCustomerRole_throwsApiException() {
        // Arrange — admin user cannot be looked up as a customer
        CustomerUser admin = mockCustomer(1L, "Admin", "admin@example.com", "US", "ADMIN");
        when(customers.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(admin));

        // Act & Assert
        assertThatThrownBy(() -> service.transactions(1L, PageRequest.of(0, 5)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    // ── paymentOptions ────────────────────────────────────────────────────────

    @Test
    void paymentOptions_returnsListOfAllActiveCustomers() {
        // Arrange
        CustomerUser c1 = mockCustomer(1L, "Alice", "alice@example.com", "US", "CUSTOMER");
        CustomerUser c2 = mockCustomer(2L, "Bob", "bob@example.com", "GB", "CUSTOMER");
        when(customers.findByActiveTrueAndRoleOrderByFullNameAsc("CUSTOMER"))
                .thenReturn(List.of(c1, c2));

        // Act
        List<PaymentCustomerOptionResponse> result = service.paymentOptions();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).fullName()).isEqualTo("Alice");
        assertThat(result.get(0).country()).isEqualTo("US");
    }

    @Test
    void paymentOptions_noCustomers_returnsEmptyList() {
        // Arrange
        when(customers.findByActiveTrueAndRoleOrderByFullNameAsc("CUSTOMER")).thenReturn(List.of());

        // Act
        List<PaymentCustomerOptionResponse> result = service.paymentOptions();

        // Assert
        assertThat(result).isEmpty();
    }

    // ── accountOptions ────────────────────────────────────────────────────────

    @Test
    void accountOptions_validCustomer_returnsActiveAccounts() {
        // Arrange
        CustomerUser customer = mockCustomer(2L, "Bob", "bob@example.com", "UK", "CUSTOMER");
        when(customers.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        Account account = mockAccount(10L, "Savings", "ACC-010", "USD", new BigDecimal("1000.00"), true);
        when(accounts.findByCustomerIdAndActiveTrueOrderByIdAsc(2L)).thenReturn(List.of(account));

        // Act
        List<PaymentAccountOptionResponse> result = service.accountOptions(2L);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).currency()).isEqualTo("USD");
        assertThat(result.get(0).availableBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void accountOptions_customerNotFound_throwsApiException() {
        // Arrange
        when(customers.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.accountOptions(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Test
    void accountOptions_nonCustomerRole_throwsApiException() {
        // Arrange
        CustomerUser admin = mockCustomer(1L, "Admin", "admin@example.com", "US", "ADMIN");
        when(customers.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(admin));

        // Act & Assert
        assertThatThrownBy(() -> service.accountOptions(1L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Test
    void accountOptions_accountLabelIncludesTypeNumberAndCurrency() {
        // Arrange
        CustomerUser customer = mockCustomer(2L, "Bob", "bob@example.com", "UK", "CUSTOMER");
        when(customers.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        Account account = mockAccount(10L, "Checking Account", "ACC-0001", "USD",
                new BigDecimal("500.00"), true);
        when(accounts.findByCustomerIdAndActiveTrueOrderByIdAsc(2L)).thenReturn(List.of(account));

        // Act
        List<PaymentAccountOptionResponse> result = service.accountOptions(2L);

        // Assert
        assertThat(result.get(0).label())
                .isEqualTo("Checking Account · ACC-0001 · USD");
    }

    // ── accountOption ─────────────────────────────────────────────────────────

    @Test
    void accountOption_validCustomerAndAccount_returnsAccountOption() {
        // Arrange
        CustomerUser customer = mockCustomer(2L, "Bob", "bob@example.com", "UK", "CUSTOMER");
        when(customers.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        Account account = mockAccount(10L, "Savings", "ACC-010", "USD", new BigDecimal("500.00"), true);
        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                10L, 2L, "CUSTOMER")).thenReturn(Optional.of(account));

        // Act
        PaymentAccountOptionResponse result = service.accountOption(2L, 10L);

        // Assert
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.availableBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void accountOption_accountNotFound_throwsApiException() {
        // Arrange
        CustomerUser customer = mockCustomer(2L, "Bob", "bob@example.com", "UK", "CUSTOMER");
        when(customers.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(accounts.findByIdAndCustomer_IdAndCustomer_ActiveTrueAndCustomer_RoleAndActiveTrue(
                99L, 2L, "CUSTOMER")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.accountOption(2L, 99L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ErrorCode.INVALID_ACCOUNT));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CustomerUser mockCustomer(Long id, String fullName, String email,
                                       String country, String role) {
        CustomerUser user = mock(CustomerUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getFullName()).thenReturn(fullName);
        when(user.getEmail()).thenReturn(email);
        when(user.getCountry()).thenReturn(country);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    private Account mockAccount(Long id, String type, String number, String currency,
                                 BigDecimal balance, boolean active) {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(id);
        when(account.getAccountType()).thenReturn(type);
        when(account.getAccountNumber()).thenReturn(number);
        when(account.getCurrency()).thenReturn(currency);
        when(account.getAvailableBalance()).thenReturn(balance);
        when(account.isActive()).thenReturn(active);
        return account;
    }
}
