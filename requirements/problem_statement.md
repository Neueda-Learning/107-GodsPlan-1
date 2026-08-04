# Problem Statement — Payments Processing System

## Context
Internal training project (backend + frontend cohort). A small team is building a Payments Processing application over several training weeks, moving from API fundamentals through to a web front end. There is no real payment network integration — all processing is simulated internally.

## Current state
There is no existing system. Today, "processing a payment" in this training context is a conceptual exercise only — there is no application, no API, and no record of payment status or history anywhere. The team is starting from a blank slate.

## Pain points
1. There is no way to create a payment or track it through its lifecycle (CREATED → VALIDATED → SENT → COMPLETED, or FAILED at any stage).
2. There is no audit trail of status changes — if a payment fails, nobody can see when, at what stage, or why.
3. There is no way to detect or handle a client submitting the same payment twice (idempotency is undefined).
4. There is no way to view, search, or filter payments — a user cannot answer "what is the status of payment X?" or "show me all FAILED payments."
5. There are no defined rules for what counts as a valid status transition, so invalid transitions (e.g. COMPLETED → CREATED) could silently corrupt data if left unchecked.

## Who feels the pain
- **Trainee developers** — no working system to demonstrate skills against, and no clear "definition of done" for a first working version.
- **Instructor (acting as customer)** — cannot evaluate the team's understanding of API design, state machines, or data modelling without a working system to review.
- **End user of the eventual front end** — (simulated) would have no way to create a payment, check its status, or see why it failed.
- **Auditor/compliance stakeholder (simulated)** — would have no trail of who/what triggered each status change.

## What is NOT in scope (v1)
- Integration with real payment networks or gateways (explicitly simulated internally).
- User authentication or multi-user account ownership (single user assumed).
- Batch payments, scheduled/recurring payments, notifications, multi-currency conversion, and payment reversal — these are Advanced/If-Time features, not core scope.

## Success looks like
A user can create a payment via the API, watch it move through CREATED → VALIDATED → SENT → COMPLETED (or FAILED with an error code) at every stage, and — once the front end exists — view a payment's current status, its full status history, and filter the payment list by status, all from a browser.
