# Database Schema — Payments Processing System

## Overview

**Database:** MySQL 8  
**Migration Tool:** Flyway  

Migration files:

- `V1__init.sql`
- `V2__add_reference.sql`
- ...

> **Rule:** The AI never edits an already applied migration. It always creates a new migration file.

---

# Tables

## accounts

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | AUTO_INCREMENT, PRIMARY KEY | Account identifier |
| account_number | VARCHAR(34) | NOT NULL, UNIQUE | Unique account number |
| currency | CHAR(3) | NOT NULL | Account currency |
| active | BOOLEAN | NOT NULL, DEFAULT TRUE | Account status |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

---

## payments

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | AUTO_INCREMENT, PRIMARY KEY | Payment identifier |
| idempotency_key | VARCHAR(80) | NOT NULL, UNIQUE | Prevents duplicate payments |
| amount | DECIMAL(15,2) | NOT NULL | Payment amount |
| currency | CHAR(3) | NOT NULL | Payment currency |
| source_account_id | BIGINT | NOT NULL, FK → accounts(id) | Sender account |
| destination_account_id | BIGINT | NOT NULL, FK → accounts(id) | Receiver account |
| reference | VARCHAR(200) | NULL | Payment reference |
| status | VARCHAR(20) | NOT NULL | Current payment status |
| error_code | VARCHAR(40) | NULL | Failure error code |
| error_description | VARCHAR(300) | NULL | Failure description |
| destination_amount | DECIMAL(15,2) | NULL | Set only when currencies differ |
| exchange_rate | DECIMAL(18,8) | NULL | Rate used during validation |
| exchange_rate_id | BIGINT | FK → exchange_rates(id) | Rate record reference |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Creation time |
| updated_at | TIMESTAMP | NOT NULL, AUTO UPDATE | Last update time |

### Payment Status Values

```
CREATED
VALIDATED
SENT
COMPLETED
FAILED
```

---

## exchange_rates

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | AUTO_INCREMENT, PRIMARY KEY | Rate identifier |
| base_currency | CHAR(3) | NOT NULL | Source currency |
| quote_currency | CHAR(3) | NOT NULL | Target currency |
| rate | DECIMAL(18,8) | NOT NULL | Exchange rate |
| source | VARCHAR(60) | NOT NULL | Rate provider |
| fetched_at | TIMESTAMP | NOT NULL | Provider fetch time |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Record creation time |

---

## payment_status_history

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | BIGINT | AUTO_INCREMENT, PRIMARY KEY | History identifier |
| payment_id | BIGINT | NOT NULL, FK → payments(id) | Payment reference |
| from_status | VARCHAR(20) | NULL | Previous status (`NULL` for initial entry) |
| to_status | VARCHAR(20) | NOT NULL | New status |
| error_code | VARCHAR(40) | NULL | Error code at transition |
| error_description | VARCHAR(300) | NULL | Error details |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Transition timestamp |

---

# Indexes

```sql
idx_payments_status
    ON payments(status);

idx_payments_created_at
    ON payments(created_at DESC);

idx_payments_idempotency_key
    ON payments(idempotency_key);

idx_history_payment_id
    ON payment_status_history(payment_id, created_at);

idx_accounts_number
    ON accounts(account_number);

idx_rates_pair_time
    ON exchange_rates(base_currency, quote_currency, fetched_at DESC);
```

---

# Constraints

## Payments Constraints

```sql
CHECK (amount > 0);

CHECK (
    status IN (
        'CREATED',
        'VALIDATED',
        'SENT',
        'COMPLETED',
        'FAILED'
    )
);

CHECK (source_account_id <> destination_account_id);
```

---

## Unique Constraints

```sql
UNIQUE (idempotency_key);
```

Ensures duplicate payment requests are prevented at the database layer.

---

## Foreign Keys

```sql
FOREIGN KEY (source_account_id)
    REFERENCES accounts(id);

FOREIGN KEY (destination_account_id)
    REFERENCES accounts(id);

FOREIGN KEY (payment_id)
    REFERENCES payments(id);

FOREIGN KEY (exchange_rate_id)
    REFERENCES exchange_rates(id);
```

---

## Exchange Rate Constraint

```sql
UNIQUE (
    base_currency,
    quote_currency,
    fetched_at
);
```

---

# Design Notes

## Payment Status Handling

`payments.status` acts as the **current-state cache**.

`payment_status_history` acts as the **append-only source of truth**.

Every status update must happen in the same database transaction:

1. Update `payments.status`
2. Insert corresponding `payment_status_history` record

This is a hard requirement:

```
NFR-D-02
```

---

## Error Handling Design

Error information exists in both tables:

### payments

Stores the current failure reason.

Example:

```
FAILED
INSUFFICIENT_FUNDS
Account balance too low
```

### payment_status_history

Stores the error information at the exact transition time.

This allows auditing of previous failures and state changes.

---

## Idempotency Design

Every payment requires:

```
idempotency_key NOT NULL
```

Reasons:

- Prevent duplicate payments
- Detect retries
- Maintain transaction safety

Database enforcement:

```sql
UNIQUE(idempotency_key)
```

---

## Data Retention

No purge or retention job is required for version 1.

Reason:

- Payment records are not treated as temporary visitor data.
- No deletion requirement exists in the current business requirements.

---

## Exchange Rate Design

`exchange_rates` acts as:

- Cache
- Audit history

Rules:

- Exchange rate records are **insert-only**
- Existing rates are never updated
- Every payment stores the exact rate used

Relationship:

```
payments.exchange_rate_id
            |
            v
exchange_rates.id
```

This ensures historical accuracy even after application cache expiration.

Refer to:

```
currency_conversion_guidelines.md
```

for:

- Cache strategy
- Currency conversion rules
- Rounding rules

---

# Sample Seed Data

## Accounts

```sql
INSERT INTO accounts
(
    account_number,
    currency,
    active
)
VALUES
    ('ACC-0001', 'USD', TRUE),
    ('ACC-0002', 'USD', TRUE),
    ('ACC-0003', 'EUR', TRUE),
    ('ACC-0004', 'INR', TRUE);
```
