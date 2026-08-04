# Non-Functional Requirements — Payments Processing System

Every NFR must be testable. "Fast" is not a requirement. "p95 under
400ms at 50 concurrent users" is.

## Performance
- NFR-P-01 p95 API response time < 500ms for single-payment read/create operations under normal training-environment load.
- NFR-P-02 Payment creation (including validation) completes in under 2 seconds end to end.
- NFR-P-03 Listing/filtering up to 1,000 payments returns in under 3 seconds.

## Scalability
- NFR-S-01 System handles at least 100 payments created per test/demo session without degradation.
- NFR-S-02 Payment table remains queryable with acceptable response times up to 10,000 rows (indexed on payment ID and status).

## Availability
- NFR-A-01 System available throughout scheduled training sessions and the final presentation slot.
- NFR-A-02 No requirement for automated backups or multi-environment failover — single training environment is acceptable, but the team should be able to explain what they would add for production.

## Data Integrity & Consistency
- NFR-D-01 Status transitions are enforced by a single point of validation (e.g. a state machine or transition table), not duplicated/inconsistent logic across endpoints.
- NFR-D-02 Every status change is persisted transactionally with its audit record — a status change must never be saved without a corresponding history entry.
- NFR-D-03 Idempotency key uniqueness is enforced at the data layer (e.g. a unique constraint), not just in application logic.

## Security
- NFR-SEC-01 No authentication is required for v1 (single user assumed), but the codebase should not hardcode secrets or credentials in source control.
- NFR-SEC-02 Input validation is applied server-side for all fields, regardless of any front-end validation.

## Reliability / Error Handling
- NFR-R-01 Every API error response includes a defined error code and an appropriate HTTP status code (see FR-ERR-01).
- NFR-R-02 A failed status update never leaves a payment in an ambiguous or undefined status — on failure, the payment is explicitly set to FAILED with a reason, or the transition is rolled back entirely.

## Observability
- NFR-O-01 Meaningful application logs are produced for payment creation, validation, and status transitions, sufficient to debug a failed payment after the fact.

## Maintainability
- NFR-M-01 Business logic (validation, status transition rules) is separated from the API/controller layer and is unit-testable independent of the database.
- NFR-M-02 Code is version-controlled in git with a visible commit history showing incremental progress (not a single large commit).

## Usability
- NFR-U-01 Front-end error messages are human-readable, not raw error codes or stack traces.
- NFR-U-02 Payment status is visually distinguishable at a glance (e.g. colour coding: green for COMPLETED, red for FAILED, in-progress states clearly differentiated).

## Documentation
- NFR-DOC-01 API documentation (e.g. Swagger/OpenAPI) is kept up to date with the actual implemented endpoints at the time of the final presentation.
