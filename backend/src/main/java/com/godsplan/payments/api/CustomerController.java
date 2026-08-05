package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.CustomerResponse;
import com.godsplan.payments.api.dto.CustomerTransactionResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    @Operation(summary = "List customers, excluding the authenticated staff user")
    public PageResponse<CustomerResponse> list(Authentication authentication,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "8") int size) {
        return customers.list(authentication.getName(), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 50), Sort.by("fullName").ascending()));
    }

    @GetMapping("/{customerId}/transactions")
    @Operation(summary = "Get a customer's paginated payment history")
    public PageResponse<CustomerTransactionResponse> transactions(
            Authentication authentication,
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return customers.transactions(customerId, authentication.getName(), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 50), Sort.by("createdAt").descending()));
    }
}
