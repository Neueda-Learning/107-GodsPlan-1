package com.godsplan.payments.api;

import com.godsplan.payments.api.dto.ExchangeRateResponse;
import com.godsplan.payments.service.RateQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {
    private final RateQueryService rates;

    public ExchangeRateController(RateQueryService rates) {
        this.rates = rates;
    }

    @GetMapping
    @Operation(summary = "Get the freshest available exchange rate")
    public ExchangeRateResponse get(
            @RequestParam @Pattern(regexp = "[A-Za-z]{3}") String base,
            @RequestParam @Pattern(regexp = "[A-Za-z]{3}") String quote) {
        return rates.get(base, quote);
    }
}
