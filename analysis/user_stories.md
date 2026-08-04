# User Stories — Payments Processing System

Story format: As a <role>, I want <capability>, so that <outcome>.
Rule: if a story cannot be built and demoed in under one day, split it.

## Epic: Payment Creation

PAY-101
As an API Consumer, I want to create a new payment with amount,
currency, source account and destination account,
so that a payment record exists in the system.
Size: M | FR-PAY-01,02,03 | Sprint 1

PAY-102
As an API Consumer, I want to supply an idempotency key when creating
a payment, so that retrying a request never creates a duplicate.
Size: M | FR-PAY-04,05 | Sprint 2

PAY-103
As an API Consumer, I want a clear error when a required field is
missing or malformed, so that I can fix my request without guessing.
Size: S | FR-PAY-02 | Sprint 1

## Epic: Validation

PAY-201
As an API Consumer, I want the amount checked for validity (positive,
within limit, max 2 decimals) before a payment proceeds,
so that bad data never enters the lifecycle.
Size: M | FR-VAL-01,02 | Sprint 1

PAY-202
As an API Consumer, I want source and destination accounts checked
for validity and that they differ, so that a payment cannot be sent
to itself or to a bad account.
Size: S | FR-VAL-03 | Sprint 1

PAY-203
As an API Consumer, I want currency checked against supported ISO
4217 codes, so that unsupported currencies fail fast with a clear
reason.
Size: S | FR-VAL-04 | Sprint 1

PAY-204
As an API Consumer, I want a payment that fails validation to move
to FAILED with a specific error code, so that I know exactly why it
failed without inspecting logs.
Size: M | FR-VAL-05 | Sprint 2

## Epic: Status Lifecycle

PAY-301
As an API Consumer, I want a payment to progress through CREATED →
VALIDATED → SENT → COMPLETED, so that I can track it end to end.
Size: L | FR-LIF-01 | Sprint 2

PAY-302
As an API Consumer, I want an invalid status transition rejected with
INVALID_STATUS_TRANSITION, so that a payment's history can never be
corrupted.
Size: M | FR-LIF-02 | Sprint 2

PAY-303
As an Auditor, I want every status transition timestamped and stored,
so that I have a complete, trustworthy audit trail.
Size: M | FR-LIF-03 | Sprint 2

PAY-304
As an API Consumer, I want to retrieve a payment's full status
history in order, so that I can see exactly how it reached its
current state.
Size: S | FR-LIF-04 | Sprint 3

## Epic: Retrieval & Search

PAY-401
As an API Consumer, I want to retrieve a single payment by ID,
so that I can check its current status and details.
Size: S | FR-RET-01 | Sprint 1

PAY-402
As an API Consumer, I want to list all payments,
so that I can see everything in the system at a glance.
Size: S | FR-RET-02 | Sprint 1

PAY-403
As a Payment Operator, I want to filter the payment list by status,
so that I can quickly find all FAILED or COMPLETED payments.
Size: S | FR-RET-03 | Sprint 3

PAY-404
As an API Consumer, I want a clear 404 with PAYMENT_NOT_FOUND for an
unknown payment ID, so that I don't get a confusing generic error.
Size: S | FR-RET-04 | Sprint 1

## Epic: Error Handling

PAY-501
As an API Consumer, I want every error response to carry a defined
error code and correct HTTP status, so that my client code can branch
on it programmatically.
Size: M | FR-ERR-01 | Sprint 2

PAY-502
As a Payment Operator, I want a FAILED payment to retain its error
code and a human-readable description, so that I can understand and
explain the failure without digging through logs.
Size: S | FR-ERR-02 | Sprint 2

## Epic: Documentation

PAY-601
As an API Consumer, I want interactive API documentation (e.g.
Swagger/OpenAPI), so that I can discover and try endpoints without
reading source code.
Size: M | FR-DOC-01 | Sprint 2

## Epic: Front End

PAY-701
As a Payment Operator, I want a form to create a new payment,
so that I don't need to call the API directly.
Size: M | FR-FE-01 | Sprint 4

PAY-702
As a Payment Operator, I want to view a payment's status and details
with colour-coded status, so that I can tell its state at a glance.
Size: M | FR-FE-02 | Sprint 4

PAY-703
As a Payment Operator, I want to see a payment's status history as a
timeline, so that I can understand its full journey visually.
Size: M | FR-FE-03 | Sprint 5

PAY-704
As a Payment Operator, I want to search/filter the payment list by
status in the UI, so that I can find what I need without using the
API.
Size: S | FR-FE-04 | Sprint 5

PAY-705
As a Payment Operator, I want to see the error code and description
for a FAILED payment in the UI, so that I don't need to call the API
to diagnose a failure.
Size: S | FR-FE-05 | Sprint 5
