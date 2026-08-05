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
- 

## Assumptions
- A single user is assumed throughout; there is no requirement to manage multiple users or account ownership no need of authentication but there will be multiple accounts in the databse of different users .
- The team has covered enough REST API fundamentals to design their own endpoint surface (routes, verbs, status codes).
- A database technology has already been introduced in training and is available for persistence mysql.
- Enhancements beyond the core lifecycle will be provided incrementally once core requirements are met.



