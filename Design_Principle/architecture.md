# Architecture — Payments Processing System

---

# System Context

```text
React SPA (Front end) ──────────────► API Gateway / Nginx (TLS)
                                             │
                                             ▼
                                 Spring Boot (Java)
                                 ├─ PaymentService      (create, retrieve, list)
                                 ├─ ValidationService   (amount, currency, accounts)
                                 ├─ ExchangeRateService (FX lookup, caching)
                                 │                        └──► exchangerate.host /convert
                                 ├─ LifecycleService    (state machine, transitions)
                                 ├─ IdempotencyService  (duplicate detection)
                                 └─ AuditService        (status history writes/reads)
                                             │
                                             ▼
                                         MySQL 8
                               (source of truth —
                                payments,
                                payment_status_history,
                                accounts)
```

## Notes

* No authentication/authorization layer — the problem statement and business requirements explicitly assume a single user with no account ownership for v1. If roles are ever added, they slot in as an `AuthService` in front of the gateway, unchanged below it.
* No external payment network or SMS/notification gateway — the problem statement is explicit that processing is simulated internally. **SENT** and **COMPLETED** are produced by `LifecycleService` itself, not by an external call.
* `ExchangeRateService` is the one exception to "everything is simulated internally": it calls the real `exchangerate.host` `/convert` endpoint (currencylayer-backed, `access_key` auth) for currency conversion (Appendix E, Multi-Currency Support). It is the only component permitted to make that outbound call — see `currency_conversion_guidelines.md` for the full request/response contract and caching strategy.

---

# Component Responsibilities

| Component               | Owns                                                                 | Must not                                                            |
| ----------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------- |
| **PaymentService**      | Create, retrieve, list payments (DTOs only)                          | Decide validity or transitions itself                               |
| **ValidationService**   | Amount/currency/account rules (Appendix C)                           | Persist anything directly                                           |
| **ExchangeRateService** | FX rate lookup and caching; the only caller of the external rate API |Perform payment lifecycle transitions, persist payment data directly, or be called directly by controllers/clients|
| **LifecycleService**    | The state machine; the only writer of `status`                       | Be bypassed by any other component                                  |
| **IdempotencyService**  | Detect duplicate idempotency keys before create                      | Allow two payments to share one key                                 |
| **AuditService**        | Append-only status history, read-back for a payment                  | Ever update or delete a history row                                 |

---

# Payment State Machine

```text
CREATED ──validate──► VALIDATED ──send──► SENT ──confirm──► COMPLETED
   │                       │                  │
   └───────────────────────┴──────────────────┴──► FAILED (with error code)
```

Illegal transitions (e.g. **COMPLETED → CREATED**, **CREATED → SENT**) are rejected in `LifecycleService`, not in the UI or the controller layer — the same rule applies whether the request came from the front end or a direct API call.

When a payment's currency differs from its destination account's currency, `ExchangeRateService` is consulted during the **CREATED → VALIDATED** step (not a separate status — see `currency_conversion_guidelines.md` §7). The resolved rate is frozen on the payment record at that point; it is never re-fetched at later stages of the same payment's lifecycle.

---

# Idempotency Flow

1. Client sends `Idempotency-Key` header (or field) with `POST /payments`.
2. `IdempotencyService` checks for an existing payment with that key (enforced by a unique DB constraint, not just an application check).
3. If found → the existing payment is returned unchanged (**HTTP 200**).
4. If not found → `PaymentService` creates a new payment (**HTTP 201**) and the key is stored with it.

---

# Key Decisions (ADR Summary)

| #     | Decision                                                              | Why                                                                                                                  | Rejected Alternative                                                                                                        |
| ----- | --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| **1** | Monolith, modular packages                                            | Small training team, single deployable, matches training scope                                                       | Microservices — needless ops overhead for the exercise                                                                      |
| **2** | MySQL over a document store                                           | Relational integrity for status-history joins; transactional guarantees for status writes                            | MongoDB — weaker fit for an auditable, strictly-ordered history                                                             |
| **3** | Processing simulated synchronously in-request                         | No real gateway to integrate with (explicitly out of scope); keeps the demo simple and deterministic                 | A message queue / async worker — real value only once a real external system exists                                         |
| **4** | Idempotency enforced at the DB layer (unique constraint)              | Prevents a race between two near-simultaneous duplicate requests                                                     | Application-layer check only — not safe under concurrency                                                                   |
| **5** | No authentication in v1                                               | Explicitly out of scope per business requirements (single user assumed)                                              | JWT/session auth — deferred, not needed for the training goals                                                              |
| **6** | FX rates fetched from a real third-party API, cached with a short TTL | Real-time conversion needs a real rate; per-payment calls would hit provider rate limits and add unnecessary latency | Hardcoded/static rate table — not "real time"; per-payment live call — too slow and rate-limit-fragile                      |
| **7** | Rate frozen on the payment at VALIDATED, not re-fetched later         | A COMPLETED payment must always be able to show exactly what rate was used                                           | Re-resolving the rate at SEND/COMPLETE time — would let the recorded rate drift from what the customer was actually charged |

---

# Failure Modes

* Simulated **send** step fails → payment transitions to **FAILED** with `NETWORK_ERROR` or `PROCESSING_ERROR`; the failure and its reason are written to `payment_status_history` in the same transaction as the status change (never a status change with no history row).
* Validation fails → payment transitions straight to **FAILED** with a specific validation error code; no `VALIDATED`/`SENT` entries are ever created for it.
* Database unavailable during a transition → the transition is rolled back entirely; the payment remains at its last successfully persisted status. The API returns `PROCESSING_ERROR` (**HTTP 500**) rather than reporting a status that was never actually saved.
* Duplicate submission under concurrency → the DB unique constraint on `idempotency_key` guarantees only one payment is ever created; the losing request's insert fails and is treated as "already exists," returning the winner's payment.
* FX rate provider unreachable or times out, and no usable cached rate exists → the payment transitions to **FAILED** with `EXCHANGE_RATE_UNAVAILABLE` (or `STALE_EXCHANGE_RATE` if a cached rate exists but exceeds its max age); no partial conversion is ever persisted. Full detail in `currency_conversion_guidelines.md`.
