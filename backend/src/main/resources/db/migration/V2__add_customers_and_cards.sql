CREATE TABLE customer_users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  role VARCHAR(20) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT chk_customer_role CHECK (role IN ('CUSTOMER', 'ADMIN', 'STAFF'))
);

ALTER TABLE accounts ADD COLUMN customer_id BIGINT NULL;
ALTER TABLE accounts ADD CONSTRAINT fk_account_customer
  FOREIGN KEY (customer_id) REFERENCES customer_users(id);
CREATE INDEX idx_accounts_customer_id ON accounts(customer_id);

CREATE TABLE payment_cards (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  brand VARCHAR(30) NOT NULL,
  last_four CHAR(4) NOT NULL,
  expiry_month TINYINT NOT NULL,
  expiry_year SMALLINT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_card_customer FOREIGN KEY (customer_id) REFERENCES customer_users(id),
  CONSTRAINT fk_card_account FOREIGN KEY (account_id) REFERENCES accounts(id),
  CONSTRAINT chk_card_last_four CHECK (last_four REGEXP '^[0-9]{4}$'),
  CONSTRAINT chk_card_expiry_month CHECK (expiry_month BETWEEN 1 AND 12),
  INDEX idx_cards_customer_id (customer_id)
);

ALTER TABLE payments ADD COLUMN payment_method VARCHAR(80) NOT NULL DEFAULT 'Bank transfer';

INSERT INTO customer_users (id, full_name, email, role, active) VALUES
  (1, 'Operations Staff', 'staff@godsplan.local', 'ADMIN', TRUE),
  (2, 'Anita Sharma', 'anita.sharma@example.com', 'CUSTOMER', TRUE),
  (3, 'Rohan Mehta', 'rohan.mehta@example.com', 'CUSTOMER', TRUE),
  (4, 'Meera Iyer', 'meera.iyer@example.com', 'CUSTOMER', TRUE);

UPDATE accounts SET customer_id = 1 WHERE id = 1;
UPDATE accounts SET customer_id = 2 WHERE id = 2;
UPDATE accounts SET customer_id = 3 WHERE id = 3;
UPDATE accounts SET customer_id = 4 WHERE id = 4;
ALTER TABLE accounts MODIFY customer_id BIGINT NOT NULL;

INSERT INTO accounts (account_number, currency, active, customer_id) VALUES
  ('ACC-0005', 'EUR', TRUE, 2),
  ('ACC-0006', 'INR', TRUE, 3);

INSERT INTO payment_cards (customer_id, account_id, brand, last_four, expiry_month, expiry_year) VALUES
  (1, 1, 'Visa', '4242', 12, 2029),
  (2, 2, 'Visa', '1847', 8, 2028),
  (3, 3, 'Mastercard', '7306', 3, 2030),
  (4, 4, 'RuPay', '5519', 11, 2027);

INSERT INTO payments (idempotency_key, amount, currency, source_account_id, destination_account_id,
  reference, status, payment_method) VALUES
  ('seed-customer-completed', 185.50, 'USD', 2, 1, 'Consulting invoice', 'COMPLETED', 'Visa ending 1847');
SET @completed_payment_id = LAST_INSERT_ID();
INSERT INTO payment_status_history (payment_id, from_status, to_status) VALUES
  (@completed_payment_id, NULL, 'CREATED'),
  (@completed_payment_id, 'CREATED', 'VALIDATED'),
  (@completed_payment_id, 'VALIDATED', 'SENT'),
  (@completed_payment_id, 'SENT', 'COMPLETED');

INSERT INTO payments (idempotency_key, amount, currency, source_account_id, destination_account_id,
  reference, status, error_code, error_description, payment_method) VALUES
  ('seed-customer-failed', 92.00, 'EUR', 3, 5, 'Subscription renewal', 'FAILED',
   'PROCESSING_ERROR', 'The issuer declined this payment', 'Mastercard ending 7306');
SET @failed_payment_id = LAST_INSERT_ID();
INSERT INTO payment_status_history (payment_id, from_status, to_status, error_code, error_description) VALUES
  (@failed_payment_id, NULL, 'CREATED', NULL, NULL),
  (@failed_payment_id, 'CREATED', 'VALIDATED', NULL, NULL),
  (@failed_payment_id, 'VALIDATED', 'FAILED', 'PROCESSING_ERROR', 'The issuer declined this payment');

INSERT INTO payments (idempotency_key, amount, currency, source_account_id, destination_account_id,
  reference, status, payment_method) VALUES
  ('seed-customer-pending', 1250.00, 'INR', 4, 6, 'Service payment', 'SENT', 'RuPay ending 5519');
SET @pending_payment_id = LAST_INSERT_ID();
INSERT INTO payment_status_history (payment_id, from_status, to_status) VALUES
  (@pending_payment_id, NULL, 'CREATED'),
  (@pending_payment_id, 'CREATED', 'VALIDATED'),
  (@pending_payment_id, 'VALIDATED', 'SENT');
