CREATE TABLE refunds (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  idempotency_key VARCHAR(100) NOT NULL UNIQUE,
  payment_id BIGINT NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL,
  reason VARCHAR(200),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
  CONSTRAINT chk_refund_amount CHECK (amount > 0),
  CONSTRAINT chk_refund_status CHECK (status IN ('PENDING','COMPLETED','FAILED')),
  INDEX idx_refunds_payment_status (payment_id, status),
  INDEX idx_refunds_created_at (created_at)
);

CREATE TABLE exchange_rate_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  base_currency CHAR(3) NOT NULL,
  quote_currency CHAR(3) NOT NULL,
  rate DECIMAL(18,8) NOT NULL,
  source VARCHAR(60) NOT NULL,
  fetched_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT chk_exchange_rate_positive CHECK (rate > 0),
  CONSTRAINT chk_exchange_pair CHECK (base_currency <> quote_currency),
  CONSTRAINT uk_exchange_snapshot UNIQUE (base_currency, quote_currency, source, fetched_at),
  INDEX idx_exchange_pair_time (base_currency, quote_currency, fetched_at)
);

INSERT IGNORE INTO exchange_rate_history (base_currency, quote_currency, rate, source, fetched_at)
SELECT p.currency, destination.currency, p.exchange_rate, p.exchange_rate_source, p.exchange_rate_fetched_at
FROM payments p
JOIN accounts destination ON destination.id = p.destination_account_id
WHERE p.exchange_rate IS NOT NULL
  AND p.exchange_rate_fetched_at IS NOT NULL
  AND p.currency <> destination.currency;

CREATE INDEX idx_payments_analytics_date_status ON payments(created_at, status);
CREATE INDEX idx_payments_analytics_currency_date ON payments(currency, created_at);
CREATE INDEX idx_payments_analytics_method_date ON payments(payment_method, created_at);
CREATE INDEX idx_payments_error_code ON payments(error_code);
CREATE INDEX idx_customers_role_created ON customer_users(role, created_at);
