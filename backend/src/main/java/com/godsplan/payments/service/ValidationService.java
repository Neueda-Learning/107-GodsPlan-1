package com.godsplan.payments.service;

import com.godsplan.payments.config.PaymentProperties;
import com.godsplan.payments.domain.Payment;
import com.godsplan.payments.error.ApiException;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {
    private final PaymentProperties properties;

    public ValidationService(PaymentProperties properties) {
        this.properties = properties;
    }

    public String normalizeAndValidateCurrency(String currency) {
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!properties.supportedCurrencies().contains(normalized)) {
            throw new ApiException(ErrorCode.INVALID_CURRENCY, HttpStatus.BAD_REQUEST,
                    "Currency '" + normalized + "' is not supported");
        }
        return normalized;
    }

    public void validateAmountShape(BigDecimal amount) {
        if (amount.scale() > 2 || amount.precision() > 15) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, HttpStatus.BAD_REQUEST,
                    "Amount must have at most 2 decimal places and 15 digits");
        }
    }

    public void validateBusiness(Payment payment) {
        if (payment.getAmount().signum() <= 0) {
            throw new BusinessFailure(ErrorCode.INVALID_AMOUNT, "Amount must be greater than 0");
        }
        if (payment.getAmount().compareTo(properties.maxAmount()) > 0) {
            throw new BusinessFailure(ErrorCode.INVALID_AMOUNT,
                    "Amount exceeds the configured maximum of " + properties.maxAmount().toPlainString());
        }
        if (!payment.getSourceAccount().isActive() || !payment.getDestinationAccount().isActive()) {
            throw new BusinessFailure(ErrorCode.INVALID_ACCOUNT, "Source and destination accounts must be active");
        }
        if (!payment.getSourceAccount().getCurrency().equals(payment.getCurrency())) {
            throw new BusinessFailure(ErrorCode.CURRENCY_MISMATCH,
                    "Payment currency must match the source account currency");
        }
    }

    public void validateConvertedAmount(BigDecimal convertedAmount) {
        if (convertedAmount.compareTo(properties.maxAmount()) > 0) {
            throw new BusinessFailure(ErrorCode.CONVERSION_LIMIT_EXCEEDED,
                    "Converted destination amount exceeds the configured maximum");
        }
    }
}

