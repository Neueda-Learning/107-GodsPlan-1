package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.*;
import com.godsplan.payments.domain.PaymentStatus;
import com.godsplan.payments.service.AuditService;
import com.godsplan.payments.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;
    private final AuditService audit;

    public PaymentController(PaymentService payments, AuditService audit) {
        this.payments = payments;
        this.audit = audit;
    }

    @PostMapping
    @Operation(summary = "Create and synchronously process a payment")
    public ResponseEntity<PaymentResponse> create(
            @Parameter(required = true, example = "IK-001") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentService.CreateResult result = payments.create(idempotencyKey, request);
        if (!result.created()) return ResponseEntity.ok(result.payment());
        return ResponseEntity.created(URI.create("/api/v1/payments/" + result.payment().id())).body(result.payment());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a payment")
    public PaymentResponse get(@PathVariable Long id) {
        return payments.get(id);
    }

    @GetMapping
    @Operation(summary = "List and filter payments")
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 1000);
        String[] parts = sort.split(",", 2);
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return payments.list(status, PageRequest.of(safePage, safeSize, Sort.by(direction, "createdAt")));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Retrieve the chronological audit trail")
    public HistoryResponse history(@PathVariable Long id) {
        return audit.getHistory(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Apply an explicitly validated status transition")
    public PaymentResponse transition(@PathVariable Long id, @Valid @RequestBody StatusTransitionRequest request) {
        return payments.transition(id, request);
    }
}

