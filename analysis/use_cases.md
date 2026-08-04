# Use Cases — Payments Processing System

## UC-01 Create and process a payment through to completion

Actor: API Consumer (or Payment Operator via the front end)
Precondition: System is available; caller has a valid source and
destination account reference and a supported currency
Trigger: A payment needs to be sent

Main flow
1. Caller submits a new payment with amount, currency, source
   account, destination account and an idempotency key.
2. System validates the payload (amount, currency, accounts).
3. System persists the payment with status CREATED and records the
   creation as the first audit entry.
4. System runs validation rules and transitions the payment to
   VALIDATED, recording the transition.
5. System simulates transmission and transitions the payment to SENT,
   recording the transition.
6. System simulates confirmation and transitions the payment to
   COMPLETED, recording the transition.
7. Caller retrieves the payment and sees status COMPLETED with a full
   four-entry status history.

Alternate flows
2a. Validation fails at step 2 — payment moves directly to FAILED with
    a specific error code (e.g. INVALID_AMOUNT); flow ends at step 3.
4a. Business validation fails at step 4 (e.g. INSUFFICIENT_FUNDS,
    INVALID_ACCOUNT) — payment moves to FAILED; flow ends.
5a. Simulated transmission fails at step 5 — payment moves to FAILED
    with NETWORK_ERROR or PROCESSING_ERROR; flow ends.

Exception flows
E1. Caller retries the same request with the same idempotency key
    before completion — system returns the existing (in-progress)
    payment rather than creating a second one; no new lifecycle
    starts.
E2. Caller attempts to manually force a transition that skips a stage
    (e.g. CREATED → SENT) — system rejects with
    INVALID_STATUS_TRANSITION; the payment's actual status is
    unchanged.

Postcondition: A payment exists with a final status (COMPLETED or
FAILED) and a complete, chronological, immutable audit trail of every
transition it went through.

## UC-02 Investigate a failed payment

Actor: Payment Operator / Auditor
Precondition: A payment exists with status FAILED

Main flow
1. User opens the payment list and filters by status = FAILED.
2. System returns all payments currently in status FAILED.
3. User selects a payment to inspect.
4. System displays the payment's details plus its full status
   history, with the FAILED entry showing an error code and a
   human-readable description.
5. User reads the reason and decides on next action outside the
   system (e.g. correct and resubmit as a new payment).

Alternate flows
2a. No payments are currently FAILED — system returns an empty list,
    not an error.

Postcondition: User has a clear, complete explanation of why the
payment failed, sourced entirely from the audit trail — no log
inspection required.

## UC-03 Safely retry a payment submission

Actor: API Consumer
Precondition: Caller previously submitted a payment and is unsure
whether the request succeeded (e.g. after a timeout)

Main flow
1. Caller re-sends the original payment request with the same
   idempotency key used originally.
2. System recognises the idempotency key already exists.
3. System returns the existing payment (its current status and
   details) rather than creating a new one.
4. Caller uses the returned payment as the source of truth and does
   not create a second, duplicate payment.

Alternate flows
1a. Caller uses a new idempotency key by mistake — a genuinely new,
    separate payment is created; this is expected behaviour, not a
    bug, since the key is the caller's contract for "this is the same
    request."

Postcondition: Exactly one payment exists for the caller's original
intent, regardless of how many times the request was retried.
