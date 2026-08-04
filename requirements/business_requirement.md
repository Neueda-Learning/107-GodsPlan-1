# Business Requirements — Payments Processing System

## Business goals
| # | Goal | Measure | Target |
|---|------|---------|--------|
| G1 | Deliver a working minimal payment lifecycle | Can a payment be created and moved to COMPLETED via the API? | Yes, by end of API training week |
| G2 | Full auditability of every payment | % of status changes with a recorded timestamp | 100% |
| G3 | Prevent invalid or duplicate processing | % of invalid transitions / duplicate submissions rejected | 100% |
| G4 | Usable front end for the core workflow | Can a user create, view, and search payments without using the API directly? | Yes, by end of front-end training week |
| G5 | Demonstrable to stakeholders | Successful live demo of full lifecycle + status history in final presentation | Pass instructor sign-off |

## Stakeholders
| Stakeholder | Interest | Approval rights |
|-------------|----------|------------------|
| Instructor (acting as customer) | Detailed requirements, scope decisions, technical guidance | Final sign-off on requirements and demo |
| Trainee team | Building the system, learning API/frontend/data-model skills | Feature design and implementation decisions |
| Manager / other stakeholders (final presentation audience) | Visibility into training outcomes | Attend presentation, may ask questions |

## Constraints
- No real payment network or gateway integration — must be simulated internally.
- No authentication/authorization system required — single user assumed, no account ownership.
- Technology choices are constrained to whatever has been covered in training so far (e.g. Spring Boot, Flask, or Express.js for the API; instructor approval required for any other framework or technology).
- Delivery is staged: core API first (API training week), front end added in a later training week.
- Team must use git with branching/PRs, and keep the data model deliberately minimal at first ("id, amount, currency, status" is an acceptable v1 payment object).

## Assumptions
- A single user is assumed throughout; there is no requirement to manage multiple users or account ownership.
- The team has covered enough REST API fundamentals to design their own endpoint surface (routes, verbs, status codes).
- A database technology has already been introduced in training and is available for persistence.
- Enhancements beyond the core lifecycle will be provided incrementally once core requirements are met.

## Dependencies
- Instructor availability for scope clarification and requirement sign-off (instructor acts as "customer").
- Git hosting platform, to be confirmed by instructors.
- Team's own decisions on initial data model, made collaboratively and kept intentionally simple.

## Out of scope
Anything listed as out of scope in problem_statement.md carries over here. Additionally, all items under "Advanced Features (If You Have Time)" — batch payments, scheduling, notifications, reporting/analytics beyond basic filtering, concurrency locking strategies, reversal/cancellation, multi-currency, and detailed audit logging of *who* performed an action — are optional stretch goals, not core business requirements.
