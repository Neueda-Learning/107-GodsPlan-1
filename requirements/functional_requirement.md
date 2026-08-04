# Functional Requirements — Payments Processing System

Format: FR-<module>-<n>. Every FR must trace to a user story.

## Payment Creation
- FR-PAY-01 A user can create a new payment by supplying, at minimum, amount, currency, source account and destination account.
- FR-PAY-02 Amount, currency, source account and destination account are mandatory. Reference/description is optional.
- FR-PAY-03 A newly created payment is assigned a unique payment ID and starts in status CREATED.
- FR-PAY-04 A client can supply an idempotency key when creating a payment.
- FR-PAY-05 If a payment is submitted with an idempotency key that already exists, the system returns the existing payment instead of creating a duplicate (HTTP 200/existing resource, not a new record).

## Validation
- FR-VAL-01 A payment's amount must be greater than 0 and must not exceed the configured maximum limit.
- FR-VAL-02 A payment's amount must have no more than 2 decimal places (for standard currencies).
- FR-VAL-03 Source and destination accounts must be different.
- FR-VAL-04 Currency must be a supported ISO 4217 code.
- FR-VAL-05 A payment that fails any validation rule transitions to FAILED with a specific error code (see Error Handling) rather than being silently rejected.

## Status Lifecycle
- FR-LIF-01 A payment can only move through the defined lifecycle: CREATED → VALIDATED → SENT → COMPLETED, with FAILED reachable from CREATED, VALIDATED, or SENT.
- FR-LIF-02 Any transition not in the valid transition set (e.g. COMPLETED → CREATED, SENT → VALIDATED) is rejected with error code INVALID_STATUS_TRANSITION.
- FR-LIF-03 Each status transition is recorded with a timestamp, the resulting status, and (if applicable) an error code and description.
- FR-LIF-04 A user can retrieve the full status history (audit trail) for a given payment, in chronological order.

## Retrieval & Search
- FR-RET-01 A user can retrieve a single payment by its payment ID.
- FR-RET-02 A user can list all payments.
- FR-RET-03 A user can filter the payment list by status.
- FR-RET-04 Retrieving a payment ID that does not exist returns error code PAYMENT_NOT_FOUND (HTTP 404).

## Error Handling
- FR-ERR-01 The API returns a defined error code and appropriate HTTP status for every failure scenario (e.g. VALIDATION_FAILED, INVALID_AMOUNT, INVALID_CURRENCY, DUPLICATE_PAYMENT, INVALID_STATUS_TRANSITION, PAYMENT_NOT_FOUND, PROCESSING_ERROR, NETWORK_ERROR).
- FR-ERR-02 A FAILED payment retains the error code and a human-readable description as part of its record.

## Documentation
- FR-DOC-01 The REST API is documented (e.g. via Swagger/OpenAPI) so that its endpoints, request/response shapes and error codes are discoverable without reading source code.

## Front End
- FR-FE-01 A user can create a new payment via a form (source account, destination account, amount, currency, optional reference).
- FR-FE-02 A user can view a payment's current status and details, with clear visual indication of status (e.g. colour-coded).
- FR-FE-03 A user can view a payment's full status history as a timeline.
- FR-FE-04 A user can search/filter the payment list by status.
- FR-FE-05 A user can view the error code and description for a FAILED payment.
