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
- Protected Customer Details workspace with staff sessions, paginated customer records, masked cards, and lazy-loaded payment history
- Protected, database-driven Analytics workspace with backend aggregation, filters, KPI trends, interactive charts, stored FX history, refunds, heatmaps, and paginated transactions
- Responsive desktop/tablet/mobile layouts, skeletons, toasts, empty states, accessible controls, and inline validation
- Database-backed create-payment modal with customer selectors, dependent masked-account selectors, and backend ownership validation
- Public payment creation and public-safe dropdown APIs; only Customers and Analytics require staff authentication
- Live database balances with insufficient-funds checks and transactional, concurrency-safe debit/credit settlement
- Docker-based local environment and automated backend/frontend tests

## Run the whole project

Requirements: Docker Desktop with Compose.

```bash
cp .env.example .env
docker compose up -d --build
```

Then open:

- Dashboard: http://localhost:8081
- Customer Details: http://localhost:8081/customers
- Analytics: http://localhost:8081/analytics
- API: http://localhost:8081/api/v1/payments
- Swagger UI: http://localhost:8081/swagger-ui.html
- Health check: http://localhost:8081/actuator/health

MySQL is available on `localhost:3306`. The default development credentials in `.env.example` are intentionally local-only.

The application uses an nginx reverse proxy to serve both frontend and backend through a single port. The nginx port can be configured via the `NGINX_PORT` environment variable in `.env` (defaults to 8081). This is useful when deploying to environments with specific port requirements like EC2 instances.

This is a single-user local system: none of the API endpoints require authentication, and no account details are masked in responses.

The modal uses the public `/api/v1/payment-options/**` endpoints to retrieve active customers, accounts, currencies, and current balances. `/api/v1/customers/**` and `/api/v1/analytics/**` are likewise open, with no login required.

CVVs and payment tokens are never returned by the API or stored by the customer-card migration.

### Seed development analytics data

The analytics dashboard never creates sample values in the frontend. If a development database does not contain enough historical records, explicitly run the backend seeder after building the stack:

```bash
docker compose run --rm -e SPRING_PROFILES_ACTIVE=analytics-seed api
```

The command inserts deterministic demonstration records directly into the existing customer, account, card, payment, status-history, refund, and exchange-rate tables. It uses unique seed identifiers, does not overwrite or delete existing data, skips payment insertion once the database has enough records, and is safe to run repeatedly.

`APP_ENVIRONMENT` must be one of `development`, `dev`, `local`, `test`, `staging`, or `demo`. The command fails before writing anything when the environment is `production` or unspecified. Seeding is never performed during normal application startup.

Analytics APIs are available under `/api/v1/analytics` and require an authenticated `ADMIN` or `STAFF` session. Aggregations and all filters execute on the backend; recent transactions are database-paginated and card values are masked before serialization.

To stop the local stack without deleting its database volume:

```bash
docker compose down
```

## EC2 Deployment

The application is designed to run on a single EC2 instance with all services accessible through one nginx reverse proxy port:

1. **Configure the port** in your `.env` file:
   ```bash
   cp .env.example .env
   # Set NGINX_PORT to your available port (8081 or 8082)
   echo "NGINX_PORT=8081" >> .env
   ```

2. **Ensure your EC2 security group** allows inbound traffic on the configured port

3. **Build and run**:
   ```bash
   docker compose up -d --build
   ```

4. **Access the application** from your Windows DCV session:
   - All endpoints: `http://<ec2-private-ip>:8081`
   - Frontend, API, and Swagger UI all accessible through the same port
   - Nginx automatically routes `/api/*` to backend and everything else to frontend

The nginx reverse proxy configuration is in `nginx.conf` at the root of the project.

### Seed accounts

| ID | Account | Currency |
|---:|---|---|
| 1 | ACC-0001 | USD |
| 2 | ACC-0002 | USD |
| 3 | ACC-0003 | EUR |
| 4 | ACC-0004 | INR |

The create-payment form resolves these identifiers from customer and masked-account dropdowns; staff users never need to type raw IDs.

Migration V6 initializes balances for the existing demonstration accounts. Completed payments lock both accounts in a deterministic order, verify the latest sender balance, debit the sender, credit the receiver, and finalize the payment in one database transaction. Concurrent requests therefore cannot overdraw an account.

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
    "senderCustomerId": 2,
    "sourceAccountId": 2,
    "receiverCustomerId": 5,
    "destinationAccountId": 7,
    "amount": 250.00,
    "currency": "USD",
    "intermediaryBank": null,
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

Backend only requires Java 21, Maven 3.9+, and a running MySQL database. Configuration is externalized through `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PAYMENT_MAX_AMOUNT`, `EXCHANGE_RATE_API_KEY`, `APP_ENVIRONMENT`, `ANALYTICS_TIME_ZONE`, `ANALYTICS_BASE_CURRENCY`, and `ANALYTICS_MAX_QUERY_ROWS`.

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

### Service Architecture

```
┌─────────────────────────────────────────────────────┐
│  nginx Reverse Proxy (Port 8081)                   │
│  ┌──────────────────────────────────────────────┐  │
│  │  Routes:                                     │  │
│  │  • /api/* → Backend API (port 8080)          │  │
│  │  • /swagger-ui/* → Backend Swagger           │  │
│  │  • /actuator/* → Backend Health/Metrics      │  │
│  │  • /* → Frontend (port 80)                   │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
         ↓                              ↓
    ┌────────┐                    ┌──────────┐
    │  Web   │                    │   API    │
    │ (React)│                    │ (Spring) │
    └────────┘                    └──────────┘
                                       ↓
                                  ┌────────┐
                                  │ MySQL  │
                                  └────────┘
```

The backend is a modular monolith. Controllers only handle HTTP concerns; payment creation, validation, FX lookup, lifecycle transitions, idempotency, audit reading, and privacy-filtered customer queries are separate services.

Every status transition locks the payment, checks a single transition table, updates current state, and appends the history row in one transaction. A unique database constraint is the final authority for idempotency under concurrent submissions. API responses are DTOs—JPA entities are never exposed.

Structurally malformed requests (missing fields/header, malformed numbers, unsupported currency/account references) return a 4xx error envelope. A well-shaped payment that fails business processing is retained as an auditable `FAILED` resource and returns HTTP 201, matching `prompt_files/api_contracts.md`.

## Repository layout

```text
backend/   Spring Boot API, migrations, and integration tests
frontend/  React dashboard and component tests
prompt_files/, requirements/, analysis/  source specifications
compose.yaml  MySQL + API + web orchestration
```

Payment creation and its public-safe customer/account option APIs are intentionally unauthenticated. The privacy-sensitive Customer Details and Analytics workspaces retain isolated staff authentication and role checks. No real payment gateway is included; only the exchange-rate lookup is external, while account balance settlement is performed inside the local database.
