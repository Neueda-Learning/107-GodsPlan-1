package com.godsplan.payments.service;

import java.math.BigDecimal;
import java.time.Instant;

public record RateQuote(BigDecimal rate, Instant fetchedAt, String source) {}

