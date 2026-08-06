package com.godsplan.payments.error;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller used by GlobalExceptionHandlerTest to trigger
 * specific exception types and verify handler mappings.
 */
@RestController
@RequestMapping("/test-probe")
class ExceptionHandlerProbeController {

    record Body(@NotNull @NotBlank String value) {}

    @GetMapping("/api-exception")
    void apiException() {
        throw new ApiException(ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Payment not found");
    }

    @GetMapping("/business-failure")
    void businessFailure() {
        throw new BusinessFailure(ErrorCode.INSUFFICIENT_FUNDS, "Not enough funds");
    }

    @PostMapping("/validation")
    void validation(@Valid @RequestBody Body body) {}

    @GetMapping("/type-mismatch")
    void typeMismatch(@RequestParam Integer num) {}

    @GetMapping("/missing-param")
    void missingParam(@RequestParam String required) {}

    @PostMapping("/unreadable")
    void unreadable(@RequestBody JsonNode body) {}
}
