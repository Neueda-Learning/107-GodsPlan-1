package com.godsplan.payments.service;

import com.godsplan.payments.config.PaymentProperties;
import com.godsplan.payments.domain.Account;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidationServiceTest {

    private ValidationService service;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(
                new BigDecimal("1000000.00"),
                Set.of("USD", "EUR", "GBP", "INR", "JPY"),
                new PaymentProperties.ExchangeRate(
                        "https://api.example.com", "test-key",
                        Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofSeconds(2)));
        service = new ValidationService(properties);
    }

    // ── normalizeAndValidateCurrency ─────────────────────────────────────────

    @Test
    void normalizeAndValidateCurrency_withSupportedUppercase_returnsUppercase() {
        assertThat(service.normalizeAndValidateCurrency("USD")).isEqualTo("USD");
    }

    @Test
    void normalizeAndValidateCurrency_withSupportedLowercase_returnsUppercase() {
        assertThat(service.normalizeAndValidateCurrency("usd")).isEqualTo("USD");
    }

    @Test
    void normalizeAndValidateCurrency_withLeadingTrailingWhitespace_returnsNormalized() {
        assertThat(service.normalizeAndValidateCurrency("  EUR  ")).isEqualTo("EUR");
    }

    @Test
    void normalizeAndValidateCurrency_withUnsupportedCurrency_throwsApiException() {
        assertThatThrownBy(() -> service.normalizeAndValidateCurrency("XYZ"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_CURRENCY);
                    assertThat(ex.getMessage()).contains("XYZ");
                });
    }

    @Test
    void normalizeAndValidateCurrency_withEmptyString_throwsApiException() {
        assertThatThrownBy(() -> service.normalizeAndValidateCurrency(""))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.INVALID_CURRENCY));
    }

    @Test
    void normalizeAndValidateCurrency_withMixedCase_returnsUppercase() {
        assertThat(service.normalizeAndValidateCurrency("gBp")).isEqualTo("GBP");
    }

    // ── validateAmountShape ──────────────────────────────────────────────────

    @Test
    void validateAmountShape_withValidWholeNumber_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> service.validateAmountShape(new BigDecimal("100")));
    }

    @Test
    void validateAmountShape_withOneDecimalPlace_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> service.validateAmountShape(new BigDecimal("100.5")));
    }

    @Test
    void validateAmountShape_withTwoDecimalPlaces_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> service.validateAmountShape(new BigDecimal("100.50")));
    }

    @Test
    void validateAmountShape_withThreeDecimalPlaces_throwsApiException() {
        assertThatThrownBy(() -> service.validateAmountShape(new BigDecimal("100.501")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void validateAmountShape_withExcessivePrecision_throwsApiException() {
        // 16-digit integer exceeds precision limit of 15
        assertThatThrownBy(() -> service.validateAmountShape(new BigDecimal("9999999999999990")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    // ── validateBusiness ─────────────────────────────────────────────────────

    @Test
    void validateBusiness_withValidPaymentSameCurrency_doesNotThrow() {
        Payment payment = buildPayment(new BigDecimal("100.00"), "USD", true, true, "USD");
        assertThatNoException().isThrownBy(() -> service.validateBusiness(payment));
    }

    @Test
    void validateBusiness_withMaxAllowedAmount_doesNotThrow() {
        Payment payment = buildPayment(new BigDecimal("1000000.00"), "USD", true, true, "USD");
        assertThatNoException().isThrownBy(() -> service.validateBusiness(payment));
    }

    @Test
    void validateBusiness_withZeroAmount_throwsBusinessFailure() {
        Payment payment = buildPayment(BigDecimal.ZERO, "USD", true, true, "USD");
        assertThatThrownBy(() -> service.validateBusiness(payment))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void validateBusiness_withNegativeAmount_throwsBusinessFailure() {
        Payment payment = buildPayment(new BigDecimal("-50.00"), "USD", true, true, "USD");
        assertThatThrownBy(() -> service.validateBusiness(payment))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void validateBusiness_withAmountExceedingMax_throwsBusinessFailure() {
        Payment payment = buildPayment(new BigDecimal("1000000.01"), "USD", true, true, "USD");
        assertThatThrownBy(() -> service.validateBusiness(payment))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void validateBusiness_withInactiveSourceAccount_throwsBusinessFailure() {
        Payment payment = buildPayment(new BigDecimal("100.00"), "USD", false, true, "USD");
        assertThatThrownBy(() -> service.validateBusiness(payment))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode()).isEqualTo(ErrorCode.INVALID_ACCOUNT));
    }

    @Test
    void validateBusiness_withInactiveDestinationAccount_throwsBusinessFailure() {
        Payment payment = buildPayment(new BigDecimal("100.00"), "USD", true, false, "USD");
        assertThatThrownBy(() -> service.validateBusiness(payment))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode()).isEqualTo(ErrorCode.INVALID_ACCOUNT));
    }

    @Test
    void validateBusiness_withCurrencyMismatch_throwsBusinessFailure() {
        // Payment currency is USD but source account is EUR
        Payment payment = buildPayment(new BigDecimal("100.00"), "USD", true, true, "EUR");
        assertThatThrownBy(() -> service.validateBusiness(payment))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode()).isEqualTo(ErrorCode.CURRENCY_MISMATCH));
    }

    // ── validateConvertedAmount ──────────────────────────────────────────────

    @Test
    void validateConvertedAmount_withinLimit_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> service.validateConvertedAmount(new BigDecimal("999999.99")));
    }

    @Test
    void validateConvertedAmount_atExactMax_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> service.validateConvertedAmount(new BigDecimal("1000000.00")));
    }

    @Test
    void validateConvertedAmount_exceedsLimit_throwsBusinessFailure() {
        assertThatThrownBy(() -> service.validateConvertedAmount(new BigDecimal("1000000.01")))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(e -> assertThat(((BusinessFailure) e).getCode())
                        .isEqualTo(ErrorCode.CONVERSION_LIMIT_EXCEEDED));
    }

    @Test
    void validateConvertedAmount_withLargeExceedance_throwsBusinessFailure() {
        assertThatThrownBy(() -> service.validateConvertedAmount(new BigDecimal("5000000.00")))
                .isInstanceOf(BusinessFailure.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Payment buildPayment(BigDecimal amount, String currency,
                                  boolean sourceActive, boolean destActive, String sourceCurrency) {
        Account source = mock(Account.class);
        when(source.isActive()).thenReturn(sourceActive);
        when(source.getCurrency()).thenReturn(sourceCurrency);
        Account dest = mock(Account.class);
        when(dest.isActive()).thenReturn(destActive);
        Payment p = new Payment();
        p.setAmount(amount);
        p.setCurrency(currency);
        p.setSourceAccount(source);
        p.setDestinationAccount(dest);
        return p;
    }
}
