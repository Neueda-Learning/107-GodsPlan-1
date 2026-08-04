# Sequence Diagrams — Payments Processing System

---

# SD-01 Create a Payment and Process It to Completion (Happy Path)

```text
Client     API      PaymentSvc   ValidationSvc  LifecycleSvc   DB
 │          │            │             │              │        │
 ├─POST /payments ──────►│             │              │        │
 │  (Idempotency-Key)    │             │              │        │
 │          ├─check idempotency key ──────────────────────────►│
 │          │◄─────────────────────not found──────────────────┤
 │          ├─insert payment, status=CREATED ─────────────────►│
 │          │◄────────────────────────saved───────────────────┤
 │          ├─validate()─►│             │              │        │
 │          │◄──valid─────┤             │              │        │
 │          ├─transition(CREATED→VALIDATED)─────────────►│        │
 │          │             │             │  ├─write status + history row►│
 │          │             │             │  │◄─committed────────┤
 │          ├─transition(VALIDATED→SENT)──────────────────►│        │
 │          │             │             │  ├─write status + history row►│
 │          ├─transition(SENT→COMPLETED)──────────────────►│        │
 │          │             │             │  ├─write status + history row►│
 │◄─201 {id, status:COMPLETED} ─────────┤             │        │
```

## Notes

* All three transitions happen within the same request in this training system (processing is simulated, not asynchronous).
* Each transition writes its own history row in the same DB transaction as the status update—a status is never visible without its corresponding audit entry.
* If any step fails, the flow moves to **SD-03** instead of continuing.

---

# SD-02 Duplicate Submission (Idempotency)

```text
Client        API           IdempotencySvc      DB
 │             │                  │              │
 ├─POST /payments (key=IK-001)───►│              │
 │             ├─check key ──────────────────────►│
 │             │◄──────────not found──────────────┤
 │             ├─create payment PMT-500──────────►│
 │◄─201 {id:PMT-500, status:CREATED}──┤              │
 │
 │   ... client times out and retries ...
 │
 ├─POST /payments (key=IK-001)───►│              │
 │             ├─check key ──────────────────────►│
 │             │◄──────────found: PMT-500─────────┤
 │◄─200 {id:PMT-500, status:<current>}─┤          │
```

## Notes

* The second call never reaches PaymentService's create path—it is short-circuited by IdempotencySvc.
* HTTP **200** (not **201**) signals "this already existed," so the client can tell the two cases apart.

---

# SD-03 Validation Failure

```text
Client     API      ValidationSvc   LifecycleSvc     DB
 │          │             │              │            │
 ├─POST /payments ───────►│              │            │
 │          ├─insert payment, status=CREATED ─────────►│
 │          ├─validate()─►│              │            │
 │          │◄─invalid: amount <= 0 (INVALID_AMOUNT)──┤
 │          ├─transition(CREATED→FAILED, code=INVALID_AMOUNT)─►│
 │          │             │              ├─write status + history►│
 │◄─201 {id, status:FAILED, errorCode:INVALID_AMOUNT}──┤
```

## Notes

* A validation failure still returns **201**—the payment resource itself was created successfully; it simply landed in status **FAILED**. The error is inside the resource, not the HTTP envelope.
* The history row for this FAILED entry carries the error code and a human-readable description (**FR-ERR-02**).

---

# SD-04 Rejected Manual Status Transition

```text
Client        API        LifecycleSvc        DB
 │             │               │              │
 ├─PATCH /payments/PMT-500/status {to: CREATED} (from COMPLETED)
 │             ├─load current status ────────►│
 │             │◄────────────COMPLETED────────┤
 │             ├─check transition table: COMPLETED→CREATED?
 │             │◄─not in allowed set──────────┤
 │◄─400 {code: INVALID_STATUS_TRANSITION}─────┤
```

## Notes

* No DB write occurs for a rejected transition—the payment's status and history are left completely untouched.
* The same check runs identically whether the caller is the front end or a direct API client (**FR-LIF-02**).

---

# SD-05 Cross-Currency Payment (Cache Miss → Live Rate Fetch)

```text
Client   API    PaymentSvc  ValidationSvc  ExchangeRateSvc   exchangerate.host
 │        │          │            │              │                 │
 ├─POST /payments (USD→INR) ─────►│              │                 │
 │        ├─insert payment, status=CREATED────────────────────────►│
 │        ├─validate()───────────►│              │                 │
 │        │          │            ├─needs rate USD/INR────────────►│
 │        │          │            │              ├─check cache (miss)
 │        │          │            │              ├─GET /convert?access_key=..&from=USD&to=INR&amount=250►│
 │        │          │            │              │◄─200 {info:{quote:95.330968,timestamp:1785786428},result:23832.74}
 │        │          │            │◄─rate=95.330968───────────────┤
 │        │          │◄─converted: destinationAmount=23832.74─────┤
 │        ├─transition(CREATED→VALIDATED, freeze rate)────────────────────────►│
 │        ├─transition(VALIDATED→SENT)────────────────────────────────────────►│
 │        ├─transition(SENT→COMPLETED)────────────────────────────────────────►│
 │◄─201 {id, status:COMPLETED, destinationAmount:23832.74, exchangeRate:95.330968}
```

## Notes

* The rate is fetched once during the **CREATED→VALIDATED** step and stored in the payment's `exchange_rate` field. It is never re-fetched later in the payment's lifecycle.
* ExchangeRateSvc caches `info.quote` (the unit rate), not `result`. `result` is only valid for the specific request amount (`250`). A later USD→INR payment for a different amount can reuse the cached rate and compute its own `destinationAmount`.

---

# SD-06 Cross-Currency Payment — Rate Provider Unavailable

```text
Client   API    ValidationSvc  ExchangeRateSvc   exchangerate.host   DB
 │        │            │              │                 │            │
 ├─POST /payments (USD→INR) ─────────►│                 │            │
 │        ├─insert payment, status=CREATED───────────────────────────►│
 │        ├─validate()──────────────►│                 │            │
 │        │            ├─needs rate USD/INR────────────►│            │
 │        │            │              ├─check cache (miss or stale)──►│
 │        │            │              ├─GET /convert?access_key=..&from=USD&to=INR&amount=250►│
 │        │            │              │◄─timeout, or {"success":false}──┤
 │        │            │              ├─retry once──────────────────►│
 │        │            │              │◄─timeout, or {"success":false}──┤
 │        │            │◄─EXCHANGE_RATE_UNAVAILABLE──┤             │
 │        ├─transition(CREATED→FAILED, code=EXCHANGE_RATE_UNAVAILABLE)──►│
 │◄─201 {id, status:FAILED, errorCode:EXCHANGE_RATE_UNAVAILABLE}────────┤
```

## Notes

* Exactly one retry, then fail—never hang the request waiting on an external provider (architecture.md, ExchangeRateService rules).
* No `destination_amount` is persisted for a failed lookup. The FAILED payment simply carries the error code, following the same pattern as every other validation failure.
