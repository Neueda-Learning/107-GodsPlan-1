# God's Plan — Payments Processing System

A complete training-grade payments application built from the repository requirements. It provides an idempotent, synchronously simulated payment lifecycle, an append-only audit trail, optional live currency conversion, and a responsive operations dashboard.

## What is included

- Spring Boot 3 / Java 21 REST API under `/api/v1`
- MySQL 8 persistence with versioned Flyway migrations
- Transactional state machine: `CREATED → VALIDATED → SENT → COMPLETED`, with `FAILED` from each active stage
- Database-enforced idempotency and concurrency-safe transitions
- Consistent error envelopes and interactive OpenAPI/Swagger documentation
- Cached exchangerate.host integration with timeout, one retry, frozen rates, and `HALF_EVEN` rounding
- React, Vite, Tailwind, React Router, Axios, Recharts, and Lucide dashboard
- Dashboard metrics, status chart, searchable/filterable payments, details, conversion data, and history timeline
- Responsive desktop/tablet/mobile layouts, skeletons, toasts, empty states, accessible controls, and inline validation
- Docker-based local environment and automated backend/frontend tests

## Run the whole project

Requirements: Docker Desktop with Compose.

```bash
cp .env.example .env
docker compose up --build
```

Then open:

- Dashboard: http://localhost:3000
- API: http://localhost:8080/api/v1/payments
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

MySQL is available on `localhost:3306`. The default development credentials in `.env.example` are intentionally local-only.

### Seed accounts

| ID | Account | Currency |
|---:|---|---|
| 1 | ACC-0001 | USD |
| 2 | ACC-0002 | USD |
| 3 | ACC-0003 | EUR |
| 4 | ACC-0004 | INR |

Use accounts 1 → 2 for a same-currency demo that does not need any external API key.

### Enable live currency conversion

Set `EXCHANGE_RATE_API_KEY` in `.env`, then restart the API. Cross-currency payments use the exchangerate.host `/convert` contract described in the prompt files. Without a key, a cross-currency payment is still created for auditability but ends in `FAILED` with `EXCHANGE_RATE_UNAVAILABLE`.

Rates are cached in memory for five minutes, may be used as fallback for up to 30 minutes, and are permanently frozen on every converted payment. The cache intentionally resets on restart.

## API examples

Create and process a same-currency payment:

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-001' \
  -d '{
    "amount": 250.00,
    "currency": "USD",
    "sourceAccountId": 1,
    "destinationAccountId": 2,
    "reference": "Invoice 4471"
  }'
```

Repeat the request with the same key to receive the same payment with HTTP 200. The first request returns HTTP 201.

```bash
curl http://localhost:8080/api/v1/payments/1/history
curl 'http://localhost:8080/api/v1/payments?status=COMPLETED&page=0&size=20&sort=createdAt,desc'
curl -X PATCH http://localhost:8080/api/v1/payments/1/status \
  -H 'Content-Type: application/json' \
  -d '{"toStatus":"CREATED"}'
```

The final request demonstrates rejection of an invalid transition without modifying the payment or its audit trail.

## Development

Frontend only (Node 22+):

```bash
cd frontend
npm install
npm run dev
```

Backend only requires Java 21, Maven 3.9+, and a running MySQL database. Configuration is externalized through `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PAYMENT_MAX_AMOUNT`, and `EXCHANGE_RATE_API_KEY`.

Run verification:

```bash
cd frontend
npm run lint
npm test
npm run build

cd ../backend
mvn test
```

If Java/Maven are not installed locally, run the backend test suite in Docker:

```bash
docker run --rm -v "$PWD/backend:/app" -w /app maven:3.9.9-eclipse-temurin-21 mvn test
```

## Architecture and integrity guarantees

The backend is a modular monolith. Controllers only handle HTTP concerns; payment creation, validation, FX lookup, lifecycle transitions, idempotency, and audit reading are separate services.

Every status transition locks the payment, checks a single transition table, updates current state, and appends the history row in one transaction. A unique database constraint is the final authority for idempotency under concurrent submissions. API responses are DTOs—JPA entities are never exposed.

Structurally malformed requests (missing fields/header, malformed numbers, unsupported currency/account references) return a 4xx error envelope. A well-shaped payment that fails business processing is retained as an auditable `FAILED` resource and returns HTTP 201, matching `prompt_files/api_contracts.md`.

## Repository layout

```text
backend/   Spring Boot API, migrations, and integration tests
frontend/  React dashboard and component tests
prompt_files/, requirements/, analysis/  source specifications
compose.yaml  MySQL + API + web orchestration
```

No authentication or real payment gateway is included, as both are explicitly outside v1 scope. Only the exchange-rate lookup is external; settlement remains simulated.

