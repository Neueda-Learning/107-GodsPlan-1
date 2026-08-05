package com.godsplan.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.godsplan.payments.config.PaymentProperties;
import com.godsplan.payments.error.BusinessFailure;
import com.godsplan.payments.error.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService {
    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final String SOURCE = "exchangerate.host";
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Cache<String, RateQuote> cache;
    private final ConcurrentHashMap<String, Object> pairLocks = new ConcurrentHashMap<>();

    public ExchangeRateService(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration maxAge = properties.exchangeRate().maxAge();
        this.cache = Caffeine.newBuilder().maximumSize(100)
                .expireAfterWrite(maxAge.toMillis(), TimeUnit.MILLISECONDS).build();
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.exchangeRate().timeout()).build();
    }

    public RateQuote getRate(String base, String quote, BigDecimal amount) {
        String normalizedBase = base.toUpperCase(Locale.ROOT);
        String normalizedQuote = quote.toUpperCase(Locale.ROOT);
        if (normalizedBase.equals(normalizedQuote)) {
            return new RateQuote(BigDecimal.ONE, Instant.now(), "identity");
        }
        String pair = normalizedBase + "_" + normalizedQuote;
        RateQuote cached = cache.getIfPresent(pair);
        if (isFresh(cached)) return cached;

        synchronized (pairLocks.computeIfAbsent(pair, ignored -> new Object())) {
            cached = cache.getIfPresent(pair);
            if (isFresh(cached)) return cached;
            try {
                RateQuote fetched = fetchWithRetry(normalizedBase, normalizedQuote, amount);
                cache.put(pair, fetched);
                return fetched;
            } catch (BusinessFailure failure) {
                if (cached != null && cached.fetchedAt().plus(properties.exchangeRate().maxAge()).isAfter(Instant.now())) {
                    log.warn("Using non-fresh cached FX rate for {} after provider failure", pair);
                    return cached;
                }
                if (cached != null) {
                    throw new BusinessFailure(ErrorCode.STALE_EXCHANGE_RATE,
                            "The cached exchange rate is too old and a fresh rate is unavailable");
                }
                throw failure;
            } finally {
                pairLocks.remove(pair);
            }
        }
    }

    private boolean isFresh(RateQuote quote) {
        return quote != null && quote.fetchedAt().plus(properties.exchangeRate().freshTtl()).isAfter(Instant.now());
    }

    private RateQuote fetchWithRetry(String base, String quote, BigDecimal amount) {
        if (properties.exchangeRate().apiKey() == null || properties.exchangeRate().apiKey().isBlank()) {
            throw new BusinessFailure(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                    "Exchange-rate service is not configured");
        }
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return fetch(base, quote, amount);
            } catch (Exception ex) {
                last = ex;
                log.warn("FX lookup attempt {} failed for {}/{}: {}", attempt, base, quote, ex.getMessage());
            }
        }
        if (last instanceof InterruptedException) Thread.currentThread().interrupt();
        throw new BusinessFailure(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                "A current exchange rate is unavailable; please try again later");
    }

    private RateQuote fetch(String base, String quote, BigDecimal amount) throws Exception {
        String query = "?access_key=" + encode(properties.exchangeRate().apiKey()) + "&from=" + encode(base)
                + "&to=" + encode(quote) + "&amount=" + encode(amount.toPlainString());
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.exchangeRate().url() + query))
                .timeout(properties.exchangeRate().timeout()).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Provider returned HTTP " + response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        if (!body.path("success").asBoolean(false)) throw new IllegalStateException("Provider reported failure");
        if (!base.equalsIgnoreCase(body.path("query").path("from").asText())
                || !quote.equalsIgnoreCase(body.path("query").path("to").asText())) {
            throw new IllegalStateException("Provider returned a mismatched currency pair");
        }
        BigDecimal rate = body.path("info").path("quote").decimalValue();
        long timestamp = body.path("info").path("timestamp").asLong();
        if (rate.signum() <= 0 || timestamp <= 0) throw new IllegalStateException("Provider response is incomplete");
        return new RateQuote(rate, Instant.ofEpochSecond(timestamp), SOURCE);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

