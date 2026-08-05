package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.PaymentAccountOptionResponse;
import com.godsplan.payments.api.dto.PaymentCustomerOptionResponse;
import com.godsplan.payments.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-options")
public class PaymentOptionsController {
    private final CustomerService customers;

    public PaymentOptionsController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping("/customers")
    @Operation(summary = "Public customer options for creating a payment")
    public List<PaymentCustomerOptionResponse> customers() {
        return customers.paymentOptions();
    }

    @GetMapping("/customers/{customerId}/accounts")
    @Operation(summary = "Public masked account options for creating a payment")
    public List<PaymentAccountOptionResponse> accounts(@PathVariable Long customerId) {
        return customers.accountOptions(customerId);
    }

    @GetMapping("/customers/{customerId}/accounts/{accountId}")
    @Operation(summary = "Get the latest public-safe account balance for payment validation")
    public PaymentAccountOptionResponse account(@PathVariable Long customerId, @PathVariable Long accountId) {
        return customers.accountOption(customerId, accountId);
    }
}
