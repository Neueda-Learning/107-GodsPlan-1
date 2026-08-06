# Guidelines — International Payments & Currency Conversion

Status: NOT implemented in v1. This is an implementation guide for
Appendix E's "Multi-Currency Support" advanced feature. Nothing here
changes core scope — treat it as an add-on epic once the base
lifecycle (PAY-1xx through PAY-5xx) is working and demoed.

## 1. Why this needs its own design pass

The current schema lets a payment's `currency`, `source_account`
currency, and `destination_account` currency all differ, with no
conversion happening anywhere. Before writing code, decide:

- Does a payment convert **once** at creation time, or does the rate
  apply only at the "SENT" simulated-transmission step?
- Is the amount the caller sends always in the **source account's**
  currency, with the destination amount derived — or does the caller
  specify both and the system just validates the rate used?
- What happens if the rate API is down when a payment needs to move
  from VALIDATED → SENT?

Recommendation for this training system: convert once, at the
CREATED → VALIDATED transition, and freeze the rate on the payment
record. This keeps the state machine's existing shape (validation
either passes or the payment goes to FAILED) and avoids re-fetching a
rate at every later stage.

## 2. New error codes

| Code | HTTP Status | Meaning |
|------|-------------|---------|
| CURRENCY_MISMATCH | 400 | Payment currency doesn't match source account currency and no conversion was requested |
| EXCHANGE_RATE_UNAVAILABLE | 503 | Rate provider could not be reached or returned no rate for the pair |
| STALE_EXCHANGE_RATE | 400 | Cached rate exceeded its max age and a fresh one could not be fetched |
| CONVERSION_LIMIT_EXCEEDED | 400 | Converted amount exceeds a configured max (mirrors INVALID_AMOUNT for the converted side) |

Add these to the existing error envelope and error-code table in
`api_contracts.md` — do not invent a second envelope shape for
currency errors.

## 3. Rate source — the chosen API

This project uses the **exchangerate.host `/convert` endpoint**
(currencylayer-backed). Do not swap in a different provider's shape
without updating this section and the response-mapping table below.

Request (built by `ExchangeRateService`, never by any other component):

```
GET https://api.exchangerate.host/convert
    ?access_key=<key>
    &from=USD
    &to=INR
    &amount=10
```

- `access_key` — read from externalized configuration
  (`application.yml` / env var `EXCHANGE_RATE_API_KEY`), never
  hardcoded or committed to source control.
- `from` — the payment's `currency`.
- `to` — the destination account's currency.
- `amount` — the payment's `amount`. The API does the multiplication
  server-side, so `ExchangeRateService` does not need to multiply the
  rate by the amount itself — see the mapping table below for why this
  still matters for caching.

Response:

```json
{
  "success": true,
  "terms": "https://currencylayer.com/terms",
  "privacy": "https://currencylayer.com/privacy",
  "query": { "from": "USD", "to": "INR", "amount": 10 },
  "info": { "timestamp": 1785786428, "quote": 95.330968 },
  "result": 953.30968
}
```

Field mapping into the domain model:

| API field | Meaning | Where it goes |
|-----------|---------|----------------|
| `success` | Whether the call succeeded | If `false`, treat as EXCHANGE_RATE_UNAVAILABLE regardless of HTTP status |
| `info.quote` | The **unit rate** (1 `from` = `quote` `to`) | In-memory cache entry; `payments.exchange_rate` once frozen |
| `info.timestamp` | Unix seconds when the rate was struck | In-memory cache entry; `payments.exchange_rate_fetched_at` (convert to `TIMESTAMP`) once frozen |
| `result` | `amount * quote`, pre-computed by the provider | Used to derive `payments.destination_amount`, but **not stored directly** — see note below |
| `query.from` / `query.to` | Echo of the request | Sanity-check against what was requested; mismatch → treat as EXCHANGE_RATE_UNAVAILABLE |

Important: because `result` is tied to the specific `amount` of one
call, and rates are cached and reused across many different payments
of different amounts, `ExchangeRateService` must cache `info.quote`
(the unit rate), not `result`. Every subsequent payment for the same
currency pair recomputes its own `destination_amount` as
`payment.amount * cached_quote`, rounded per §8. Only the payment that
triggered the live API call gets to use `result` directly as a
cross-check against its own `amount * quote` calculation — if they
don't match (allowing for rounding), log a warning but proceed with
the locally computed value, since the locally computed value is the
one that stays consistent for cached reuse.

A `success: false` response, or a non-200 HTTP status, or a response
where `query.from`/`query.to` don't match what was requested, are all
treated identically: no rate obtained, fall through to the retry/fail
path in §3.1 below.

### 3.1 Timeout and retry

1. Add a `ExchangeRateService` component alongside `ValidationService`
   in the architecture. It owns all outbound calls to
   `api.exchangerate.host`.
2. `ExchangeRateService` is the **only** component allowed to call the
   external API — LifecycleService and PaymentService ask it for a
   rate, they never call the provider directly.
3. Wrap the outbound call in a timeout (e.g. 2s) and one retry. Never
   let a slow rate provider hang a payment indefinitely. On repeated
   failure, fail the payment with EXCHANGE_RATE_UNAVAILABLE rather
   than blocking.

## 4. Caching the rate (don't call the API on every payment)

Real-time doesn't mean "one external call per payment." This provider
rate-limits aggressively on free/entry tiers, and rates don't move
fast enough to justify per-payment calls.

This project uses an **in-memory cache only** — no database table for
rates. That's a deliberate simplification: this is a single-instance
training deployment, so a persisted rate-history table would add a
table, a migration, and a join for benefits (rate lookups independent
of any payment, cache warmth across restarts, sharing across multiple
instances) this project doesn't need. Once a rate is used, it's frozen
onto the payment itself (§5) — that's the permanent record.

- Cache by currency pair (e.g. `USD_INR`) in memory, with a short TTL
  — 1–5 minutes is reasonable for a training system.
- Cache the **unit rate** (`info.quote`), plus `fetchedAt`
  (`info.timestamp`) and `source` (`"exchangerate.host"`) — never
  cache `result`, since that's specific to one payment's amount (see
  §3 mapping table).
- On a cache hit within TTL, use the cached rate — do not call the
  API again.
- On a cache miss or expired TTL, call the API, refresh the cache,
  then proceed. If the call fails and no cached rate exists (or the
  cached rate is older than a hard max-age, e.g. 30 minutes), fail
  the payment with EXCHANGE_RATE_UNAVAILABLE / STALE_EXCHANGE_RATE
  rather than silently using a very old rate.
- Because the cache is in-memory, it resets on every app restart and
  is not shared if the app is ever scaled to multiple instances. Both
  are acceptable trade-offs for this project's single-VM training
  deployment (architecture.md, ADR #8) — call this out explicitly in
  the presentation if asked "what would you do differently for
  production."

Use a Caffeine cache (or Spring's `@Cacheable`) — no need for Redis or
any external cache store for this project's scale.

## 5. Schema additions

No new table. Extend `payments` directly with the fields that make the
frozen rate part of the payment's own permanent record:

```
ALTER TABLE payments ADD COLUMN destination_amount       DECIMAL(15,2);
ALTER TABLE payments ADD COLUMN exchange_rate            DECIMAL(18,8);
ALTER TABLE payments ADD COLUMN exchange_rate_source     VARCHAR(60);
ALTER TABLE payments ADD COLUMN exchange_rate_fetched_at TIMESTAMP;
```

This means every completed cross-currency payment can always answer
"what rate was actually used, and where did it come from" — critical
for any future audit/reporting work, and consistent with how
`payment_status_history` already treats every state change as
permanent and explainable. What you give up versus a dedicated table:
you can no longer query "what was the USD/INR rate at 9am today"
independent of a specific payment, and the cache itself doesn't
survive a restart. If either of those becomes a real requirement
later, promoting the in-memory cache to a real `exchange_rates` table
is a small, additive change — nothing else in this design has to move.

## 6. API surface changes

Extend `POST /payments` — no new endpoint needed for the common case:

```json
{
  "amount": 250.00,
  "currency": "USD",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "reference": "Invoice 4471"
}
```

If `destinationAccountId`'s currency differs from `currency`, the
system converts automatically and the response includes both sides.
Example for a USD→INR payment (matching the `/convert` call
`from=USD&to=INR&amount=10`, scaled up to a real payment amount):

```json
{
  "id": 9010,
  "status": "COMPLETED",
  "amount": 250.00,
  "currency": "USD",
  "destinationAmount": 23832.74,
  "destinationCurrency": "INR",
  "exchangeRate": 95.330968,
  "exchangeRateSource": "exchangerate.host",
  "exchangeRateFetchedAt": "2026-08-04T09:14:20Z"
}
```

(`destinationAmount` = `amount * exchangeRate`, rounded to INR's 2
decimal places per §8 — not the provider's `result` field directly,
per the caching note in §3.)

Add one read-only endpoint for transparency/debugging:

```
GET /exchange-rates?base=USD&quote=INR
200 { "base": "USD", "quote": "INR", "rate": 95.330968, "fetchedAt": "2026-08-04T09:14:20Z", "source": "exchangerate.host" }
503 EXCHANGE_RATE_UNAVAILABLE
```

## 7. State machine impact

Insert the conversion lookup into the existing CREATED → VALIDATED
step — it does not need a new status:

```
CREATED ──validate + convert──► VALIDATED ──send──► SENT ──confirm──► COMPLETED
   │                 │
   │                 └─(rate unavailable)──► FAILED (EXCHANGE_RATE_UNAVAILABLE)
   └───────────────────────────────────────► FAILED (other validation errors)
```

The rate lookup is just one more thing ValidationService checks before
LifecycleService transitions the payment — it doesn't need its own
lifecycle stage.

## 8. Rounding and precision

- Always round the converted `destinationAmount` using the
  destination currency's minor unit (2 decimals for USD/EUR/GBP, 0 for
  JPY, etc.) — do not assume 2 decimals for every currency.
- Use `RoundingMode.HALF_EVEN` (banker's rounding) for conversion
  math to avoid systematic rounding bias — apply this consistently,
  document it, and never let floating-point `double` arithmetic touch
  money; use `BigDecimal` throughout.
- Store the raw unrounded rate (`DECIMAL(18,8)`) but only ever expose
  correctly-rounded amounts to the client.

## 9. Testing checklist

- Same-currency payment (source == destination == payment currency) —
  conversion is skipped entirely; `exchange_rate` and related columns
  stay null on the payment.
- Cross-currency payment with a fresh cached rate — no external API
  call made; response includes the cached rate and its original
  `fetched_at`.
- Cross-currency payment with an expired/no cached rate — exactly one
  external API call made; result is cached for subsequent payments.
- Rate provider times out or errors — payment fails with
  EXCHANGE_RATE_UNAVAILABLE; no partial state left behind.
- Rounding edge cases — verify JPY (0 decimals) and a 3-decimal
  currency (e.g. BHD, if supported) round correctly, not just USD/EUR.
- Concurrency — two payments for the same currency pair arriving
  during a cache miss don't trigger two duplicate outbound API calls
  if avoidable (acceptable to allow at most one duplicate call under
  race conditions — not worth over-engineering for a training system).

## 10. What NOT to build for this project

- Do not implement live rate streaming/websockets — polling with a
  short cache TTL is sufficient and far simpler to demo.
- Do not attempt real settlement or hedging logic — this is still a
  simulated system per the original problem statement; only the *rate
  lookup* needs to be real, not the money movement.
- Do not store API keys in source control — use environment variables
  or the framework's externalized configuration, and mention in the
  demo that a real deployment would use a secrets manager.
