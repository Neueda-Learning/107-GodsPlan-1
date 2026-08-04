# API Contracts — Payments Processing System

Base: `/api/v1`
Auth: none — single user assumed for v1, no Bearer token required.
Errors use a single envelope. Version the path, never the payload.

Currency conversion (`destinationAmount`, `exchangeRate`, etc. below)
is sourced from the `exchangerate.host` `/convert` endpoint. See
`currency_conversion_guidelines.md` §3 for the exact upstream request
shape and field mapping — this file only documents *our* API surface.

## Error envelope

```json
{
  "timestamp": "2026-08-04T09:14:22Z",
  "path": "/api/v1/payments",
  "code": "INVALID_AMOUNT",
  "message": "Amount must be greater than 0",
  "fieldErrors": [{ "field": "amount", "message": "must be > 0" }],
  "traceId": "b1f2c3d4"
}
```

## Error codes

| Code | HTTP Status | Meaning |
|------|-------------|---------|
| VALIDATION_FAILED | 400 | General payload validation failure |
| INVALID_AMOUNT | 400 | Amount is zero, negative, over the limit, or has too many decimals |
| INVALID_CURRENCY | 400 | Currency is not a supported ISO 4217 code |
| INVALID_ACCOUNT | 400 | Source/destination account invalid or doesn't exist |
| INSUFFICIENT_FUNDS | 400 | Simulated source account balance check failed |
| DUPLICATE_PAYMENT | 409 | (Reserved — in practice duplicates return 200 with the existing payment, see POST /payments) |
| INVALID_STATUS_TRANSITION | 400 | Requested transition is not in the allowed set |
| PAYMENT_NOT_FOUND | 404 | Payment ID does not exist |
| PROCESSING_ERROR | 500 | Internal error during simulated processing |
| NETWORK_ERROR | 503 | Simulated communication failure during the "send" stage |
| CURRENCY_MISMATCH | 400 | Payment currency doesn't match source account currency and no conversion was requested |
| EXCHANGE_RATE_UNAVAILABLE | 503 | FX rate provider could not be reached or returned no rate for the pair |
| STALE_EXCHANGE_RATE | 400 | Cached rate exceeded its max age and a fresh one could not be fetched |
| CONVERSION_LIMIT_EXCEEDED | 400 | Converted destination amount exceeds a configured max |

## POST /payments

Creates a payment and (in this training system) synchronously drives it
through validation and simulated processing to its terminal status.

Headers: `Idempotency-Key: <client-generated string>` (required)

Request
```json
{
  "amount": 250.00,
  "currency": "USD",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "reference": "Invoice 4471"
}
```

201 (new payment created, same-currency — no conversion fields set)
```json
{
  "id": 9001,
  "status": "COMPLETED",
  "amount": 250.00,
  "currency": "USD",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "reference": "Invoice 4471",
  "destinationAmount": null,
  "destinationCurrency": null,
  "exchangeRate": null,
  "exchangeRateSource": null,
  "errorCode": null,
  "errorDescription": null,
  "createdAt": "2026-08-04T09:14:22Z",
  "updatedAt": "2026-08-04T09:14:22Z"
}
```

201 (new payment created, cross-currency — converted automatically)
```json
{
  "id": 9010,
  "status": "COMPLETED",
  "amount": 250.00,
  "currency": "USD",
  "sourceAccountId": 1,
  "destinationAccountId": 4,
  "reference": "Invoice 4472",
  "destinationAmount": 23832.74,
  "destinationCurrency": "INR",
  "exchangeRate": 95.330968,
  "exchangeRateSource": "exchangerate.host",
  "exchangeRateFetchedAt": "2026-08-04T09:14:20Z",
  "errorCode": null,
  "errorDescription": null,
  "createdAt": "2026-08-04T09:14:22Z",
  "updatedAt": "2026-08-04T09:14:22Z"
}
```

`destinationAmount` is computed by us as `amount * exchangeRate`,
rounded to the destination currency's minor unit — it is not the
provider's `result` field taken verbatim, since `result` is only
valid for the exact `amount` sent on a cache-miss call (see
`currency_conversion_guidelines.md` §3 for why the two can diverge by
a rounding fraction on cached reuse).

201 (created, but ended in FAILED — still 201, the resource was created)
```json
{
  "id": 9002,
  "status": "FAILED",
  "amount": -5.00,
  "currency": "USD",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "errorCode": "INVALID_AMOUNT",
  "errorDescription": "Amount must be greater than 0",
  "createdAt": "2026-08-04T09:15:01Z",
  "updatedAt": "2026-08-04T09:15:01Z"
}
```

200 (Idempotency-Key already used — existing payment returned, no new record)
```json
{ "id": 9001, "status": "COMPLETED", "...": "same shape as above" }
```

400  VALIDATION_FAILED / INVALID_ACCOUNT / INVALID_CURRENCY — malformed request
     (missing Idempotency-Key header, missing required field, bad account ref)

If the payment currency differs from the destination account's
currency and no FX rate can be resolved (provider down, no usable
cached rate), the payment is still created but ends in FAILED — same
pattern as any other validation failure (see the FAILED example
above), with `errorCode: "EXCHANGE_RATE_UNAVAILABLE"`.

## GET /payments/{id}

200
```json
{ "id": 9001, "status": "COMPLETED", "...": "same shape as POST response" }
```
404  PAYMENT_NOT_FOUND

## GET /payments

Query: `status`, `page=0`, `size=20`, `sort=createdAt,desc`

200
```json
{
  "content": [ { "id": 9001, "status": "COMPLETED", "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 134
}
```

## GET /payments/{id}/history

200
```json
{
  "paymentId": 9001,
  "history": [
    { "fromStatus": null,       "toStatus": "CREATED",   "errorCode": null,           "createdAt": "2026-08-04T09:14:22.100Z" },
    { "fromStatus": "CREATED",  "toStatus": "VALIDATED", "errorCode": null,           "createdAt": "2026-08-04T09:14:22.140Z" },
    { "fromStatus": "VALIDATED","toStatus": "SENT",      "errorCode": null,           "createdAt": "2026-08-04T09:14:22.180Z" },
    { "fromStatus": "SENT",     "toStatus": "COMPLETED", "errorCode": null,           "createdAt": "2026-08-04T09:14:22.220Z" }
  ]
}
```
404  PAYMENT_NOT_FOUND

## PATCH /payments/{id}/status

Manual/explicit transition endpoint — used by admin tooling and to
demonstrate and test transition validation directly (FR-LIF-02).

Request
```json
{ "toStatus": "FAILED", "errorCode": "PROCESSING_ERROR", "errorDescription": "Manual override" }
```

200 — transition applied, updated payment returned
400  INVALID_STATUS_TRANSITION — requested transition not in the allowed set
404  PAYMENT_NOT_FOUND

## GET /exchange-rates

Read-only, for transparency/debugging — not required to create a
payment (conversion happens automatically inside `POST /payments`).

Query: `base`, `quote`

200
```json
{
  "base": "USD",
  "quote": "INR",
  "rate": 95.330968,
  "source": "exchangerate.host",
  "fetchedAt": "2026-08-04T09:14:20Z"
}
```
503  EXCHANGE_RATE_UNAVAILABLE — provider unreachable and no usable cached rate

## Conventions

- Times are ISO-8601 UTC with millisecond precision. The server is the
  only clock that matters.
- Money is always a JSON number with up to 2 decimal places, never a
  string.
- No endpoint returns a raw JPA entity — DTOs only.
- Every endpoint is documented in Swagger/OpenAPI with an example
  request and response (FR-DOC-01).
- `Idempotency-Key` is required on `POST /payments` only; all other
  endpoints are naturally idempotent (GET) or explicitly transition-
  checked (PATCH).
