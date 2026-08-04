# Personas — Payments Processing System

Personas exist so the AI writes UI copy, API error messages and flows
for a real human, not for a generic "user". This is a single-user
training system (no auth, no account ownership) — these personas
represent the different hats the same user wears, plus the people who
evaluate the finished product.

## Deepa — Payment Operator (primary)
Age 29. Comfortable with web forms, not a developer. Uses the
front-end UI, not the API directly.
Device: laptop, Chrome, standard office network.
Goal: create a payment, and know immediately whether it succeeded,
is still processing, or failed — and if it failed, why.
Frustration: raw error codes or stack traces instead of a plain-English
reason; not knowing whether a click actually submitted or not.
Design implication: one primary action per screen, colour-coded
status, plain-language failure descriptions, disabled Submit button
while a request is in flight (to avoid accidental double-submission).

## Kabir — API Consumer / Integrator (primary)
Age 33, backend developer on another (hypothetical) team integrating
against this payments API.
Device: Postman / curl / his own service code.
Goal: create and track payments programmatically, retry safely without
creating duplicates, and branch his own code on a stable, documented
error code.
Frustration: undocumented endpoints; inconsistent error shapes;
ambiguity about whether retrying a failed call is safe.
Design implication: OpenAPI/Swagger docs kept current; every error
response has the same shape (code + HTTP status + description);
idempotency key behaviour is documented and predictable.

## Sana — Auditor / Support Investigator (secondary)
Age 38, plays a compliance/support role reviewing what happened to a
specific payment after the fact.
Device: front-end UI or direct API calls, after the fact — never
present at the moment of failure.
Goal: reconstruct exactly what happened to a payment — every status it
passed through, when, and why it failed if it did.
Frustration: partial or missing history; a status change with no
timestamp or reason attached.
Design implication: full, immutable, chronological status history per
payment; FAILED entries always carry an error code and description;
nothing is ever silently overwritten.

## Instructor — Customer / Stakeholder Stand-in (indirect)
Plays the role of "the business" for this training project. Reviews
the system via live demo and API documentation, not by using it daily.
Goal: see a complete, correct lifecycle demonstrated live — creation,
validation, a success path, a failure path, and the audit trail — and
be able to ask "what happens if...?" and get a confident, demonstrable
answer.
Frustration: a demo that only shows the happy path; a team that can't
explain an edge case (duplicate submission, invalid transition) when
asked.
Design implication: the system must be able to demonstrate idempotency,
invalid-transition rejection and a failure-with-error-code live, on
request, not just the happy path.
