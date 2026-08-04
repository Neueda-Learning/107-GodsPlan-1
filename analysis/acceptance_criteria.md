# Acceptance Criteria — Payments Processing System

Given / When / Then. This is the single highest-leverage document for
AI-assisted development: the AI writes materially better code, and
far better tests, when the criteria are explicit.

## PAY-101 — Create a new payment

AC-101-1 Happy path
Given I am an API Consumer
When I POST a payment with a valid amount, currency, source account
and destination account
Then a payment is created with a unique payment ID
And its status is CREATED
And the response is HTTP 201 with the created payment in the body

AC-101-2 Missing mandatory field
Given I POST a payment with the destination account omitted
When the request is processed
Then no payment is created
And the response is HTTP 400 with error code VALIDATION_FAILED
And the response body names the missing field

AC-101-3 Malformed amount
Given I POST a payment with amount "abc"
When the request is processed
Then no payment is created
And the response is HTTP 400 with error code INVALID_AMOUNT

## PAY-102 — Idempotent payment creation

AC-102-1 First submission
Given I POST a payment with idempotency key "IK-001"
When no payment with that key exists yet
Then a new payment is created and returned with HTTP 201

AC-102-2 Duplicate submission
Given a payment already exists with idempotency key "IK-001"
When I POST the same idempotency key again, with the same or
different payload
Then no new payment is created
And the existing payment is returned
And the response is HTTP 200 (not 201)

AC-102-3 Duplicate detected under concurrent requests
Given two requests with idempotency key "IK-002" arrive at the same
time
When both are processed
Then exactly one payment is created
And both responses reference the same payment ID

## PAY-201 — Amount and currency validation

AC-201-1 Zero or negative amount
Given I POST a payment with amount 0 or a negative amount
When the request is processed
Then the response is HTTP 400 with error code INVALID_AMOUNT

AC-201-2 Amount exceeds limit
Given I POST a payment with amount above the configured maximum
When the request is processed
Then the response is HTTP 400 with error code INVALID_AMOUNT

AC-201-3 Too many decimal places
Given I POST a payment with amount 100.123
When the request is processed
Then the response is HTTP 400 with error code INVALID_AMOUNT

AC-201-4 Unsupported currency
Given I POST a payment with currency "XYZ"
When the request is processed
Then the response is HTTP 400 with error code INVALID_CURRENCY

## PAY-302 — Invalid status transition rejected

AC-302-1 Reject backward transition
Given a payment is in status COMPLETED
When a request attempts to transition it to CREATED
Then the transition is rejected
And the response is HTTP 400 with error code INVALID_STATUS_TRANSITION
And the payment's status remains COMPLETED

AC-302-2 Reject skipped-stage transition
Given a payment is in status CREATED
When a request attempts to transition it directly to SENT
Then the transition is rejected
And the response is HTTP 400 with error code INVALID_STATUS_TRANSITION

AC-302-3 Valid transition succeeds
Given a payment is in status VALIDATED
When a request transitions it to SENT
Then the transition succeeds
And a new audit history entry is recorded with the timestamp

## PAY-304 — Retrieve status history

AC-304-1
Given a payment has moved through CREATED → VALIDATED → SENT →
COMPLETED
When I GET its status history
Then all four entries are returned in chronological order
And each entry includes the status and its timestamp

AC-304-2 Failed payment history
Given a payment moved CREATED → VALIDATED → FAILED
When I GET its status history
Then the FAILED entry includes an error code and description

## PAY-401 / PAY-404 — Retrieve a single payment

AC-401-1 Existing payment
Given a payment with ID "PMT-123" exists
When I GET /payments/PMT-123
Then the response is HTTP 200 with the full payment details

AC-404-1 Unknown payment
Given no payment with ID "PMT-999" exists
When I GET /payments/PMT-999
Then the response is HTTP 404 with error code PAYMENT_NOT_FOUND

## PAY-403 — Filter payments by status

AC-403-1
Given payments exist in multiple statuses
When I GET /payments?status=FAILED
Then only payments currently in status FAILED are returned

AC-403-2 No matches
Given no payments are currently FAILED
When I GET /payments?status=FAILED
Then the response is HTTP 200 with an empty list (not an error)

## PAY-702 — View payment status in the UI

AC-702-1
Given a payment is COMPLETED
When I open its details screen
Then the status badge is shown in green

AC-702-2
Given a payment is FAILED
When I open its details screen
Then the status badge is shown in red
And the error code and description are visible without an extra click

## PAY-705 — View failure details in the UI

AC-705-1
Given a payment is FAILED with error code INSUFFICIENT_FUNDS
When I view its details
Then I see the error code and a human-readable description, not a
raw stack trace or internal exception message
