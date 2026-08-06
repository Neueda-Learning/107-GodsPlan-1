package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.CustomerResponse;
import com.godsplan.payments.api.dto.CustomerTransactionResponse;
import com.godsplan.payments.api.dto.PageResponse;
import com.godsplan.payments.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    @Operation(summary = "List customers")
    public PageResponse<CustomerResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "8") int size) {
        return customers.list(PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 50), Sort.by("fullName").ascending()));
    }

    @GetMapping("/{customerId}/transactions")
    @Operation(summary = "Get a customer's paginated payment history")
    public PageResponse<CustomerTransactionResponse> transactions(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return customers.transactions(customerId, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 50), Sort.by("createdAt").descending()));
    }
}
