# Database Schema — Payments Processing System

MySQL 8. Migrations via Flyway: V1__init.sql, V2__add_reference.sql…
Rule: the AI never edits an applied migration. It always adds a new one.

## Tables

accounts
  id              BIGINT AUTO_INCREMENT PK
  account_number  VARCHAR(34)  NOT NULL UNIQUE
  currency        CHAR(3)      NOT NULL
  active          BOOLEAN      NOT NULL DEFAULT TRUE
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP

payments
  id                    BIGINT AUTO_INCREMENT PK
  idempotency_key       VARCHAR(80)   NOT NULL UNIQUE
  amount                DECIMAL(15,2) NOT NULL
  currency              CHAR(3)       NOT NULL
  source_account_id     BIGINT NOT NULL REFERENCES accounts(id)
  destination_account_id BIGINT NOT NULL REFERENCES accounts(id)
  reference             VARCHAR(200)
  status                VARCHAR(20)   NOT NULL  -- CREATED|VALIDATED|SENT|COMPLETED|FAILED
  error_code            VARCHAR(40)
  error_description     VARCHAR(300)
  destination_amount    DECIMAL(15,2)           -- set only if currency != destination account currency
  exchange_rate         DECIMAL(18,8)            -- rate actually used, frozen at VALIDATED
  exchange_rate_id      BIGINT REFERENCES exchange_rates(id)
  created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
  updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP

exchange_rates
  id              BIGINT AUTO_INCREMENT PK
  base_currency   CHAR(3)       NOT NULL
  quote_currency  CHAR(3)       NOT NULL
  rate            DECIMAL(18,8) NOT NULL
  source          VARCHAR(60)   NOT NULL  -- rate provider name
  fetched_at      TIMESTAMP     NOT NULL
  created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP

payment_status_history
  id                BIGINT AUTO_INCREMENT PK
  payment_id        BIGINT NOT NULL REFERENCES payments(id)
  from_status        VARCHAR(20)      -- NULL for the initial CREATED entry
  to_status          VARCHAR(20) NOT NULL
  error_code        VARCHAR(40)
  error_description VARCHAR(300)
  created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP

## Indexes

  idx_payments_status            ON payments(status)
  idx_payments_created_at        ON payments(created_at DESC)
  idx_payments_idempotency_key   ON payments(idempotency_key)     -- UNIQUE, see constraints
  idx_history_payment_id         ON payment_status_history(payment_id, created_at)
  idx_accounts_number            ON accounts(account_number)
  idx_rates_pair_time            ON exchange_rates(base_currency, quote_currency, fetched_at DESC)

## Constraints

  CHECK (amount > 0)
  CHECK (status IN ('CREATED','VALIDATED','SENT','COMPLETED','FAILED'))
  CHECK (source_account_id <> destination_account_id)
  UNIQUE (idempotency_key)   -- enforces PAY-102 / AC-102-3 at the data layer,
                              -- not just in application code
  FOREIGN KEY (source_account_id)      REFERENCES accounts(id)
  FOREIGN KEY (destination_account_id) REFERENCES accounts(id)
  FOREIGN KEY (payment_id) ON payment_status_history REFERENCES payments(id)
  FOREIGN KEY (exchange_rate_id) ON payments REFERENCES exchange_rates(id)
  UNIQUE (base_currency, quote_currency, fetched_at) ON exchange_rates

## Notes on design choices

- `payments.status` is the current-state cache; `payment_status_history`
  is the append-only source of truth for "what happened and when."
  Every write to `payments.status` happens in the same transaction as
  the corresponding insert into `payment_status_history` — this is a
  hard rule (NFR-D-02), not a convention.
- `error_code` / `error_description` live on both `payments` (for "what
  is this payment's current failure reason, if any") and
  `payment_status_history` (for "what was the reason at the moment of
  *this* transition") — a payment can only be FAILED once as its
  current state, but the history preserves the exact transition where
  it happened.
- `idempotency_key` is required on every payment (not nullable) to keep
  the uniqueness rule simple and to make "was this a retry?" always
  answerable.
- No retention/purge job is required for v1 — unlike a system holding
  personal visitor data, there's no compliance-driven deletion
  requirement in the payments training project's business
  requirements.
- `exchange_rates` doubles as both cache and audit trail: a row is
  only ever inserted (never updated), so `payments.exchange_rate_id`
  always points at the exact rate record used for that payment, even
  after the in-memory/application cache has long since expired. See
  `currency_conversion_guidelines.md` for the full design, caching
  strategy, and rounding rules.

## Sample seed data (accounts)

  INSERT INTO accounts (account_number, currency, active) VALUES
    ('ACC-0001', 'USD', TRUE),
    ('ACC-0002', 'USD', TRUE),
    ('ACC-0003', 'EUR', TRUE),
    ('ACC-0004', 'INR', TRUE);
