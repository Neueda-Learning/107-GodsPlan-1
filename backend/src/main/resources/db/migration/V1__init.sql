CREATE TABLE accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_number VARCHAR(34) NOT NULL UNIQUE,
  currency CHAR(3) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE payments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  idempotency_key VARCHAR(80) NOT NULL UNIQUE,
  amount DECIMAL(15,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  source_account_id BIGINT NOT NULL,
  destination_account_id BIGINT NOT NULL,
  reference VARCHAR(200),
  status VARCHAR(20) NOT NULL,
  error_code VARCHAR(40),
  error_description VARCHAR(300),
  destination_amount DECIMAL(15,2),
  exchange_rate DECIMAL(18,8),
  exchange_rate_source VARCHAR(60),
  exchange_rate_fetched_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_payment_source FOREIGN KEY (source_account_id) REFERENCES accounts(id),
  CONSTRAINT fk_payment_destination FOREIGN KEY (destination_account_id) REFERENCES accounts(id),
  CONSTRAINT chk_payment_accounts CHECK (source_account_id <> destination_account_id),
  CONSTRAINT chk_payment_status CHECK (status IN ('CREATED','VALIDATED','SENT','COMPLETED','FAILED')),
  INDEX idx_payments_status (status),
  INDEX idx_payments_created_at (created_at DESC)
);

CREATE TABLE payment_status_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_id BIGINT NOT NULL,
  from_status VARCHAR(20),
  to_status VARCHAR(20) NOT NULL,
  error_code VARCHAR(40),
  error_description VARCHAR(300),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
  INDEX idx_history_payment_id (payment_id, created_at)
);

INSERT INTO accounts (account_number, currency, active) VALUES
  ('ACC-0001', 'USD', TRUE),
  ('ACC-0002', 'USD', TRUE),
  ('ACC-0003', 'EUR', TRUE),
  ('ACC-0004', 'INR', TRUE);

