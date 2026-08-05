ALTER TABLE customer_users
  ADD COLUMN country VARCHAR(80) NOT NULL DEFAULT 'India';

ALTER TABLE accounts
  ADD COLUMN account_type VARCHAR(40) NOT NULL DEFAULT 'Checking Account';

UPDATE accounts
SET account_type = CASE
  WHEN MOD(id, 2) = 0 THEN 'Savings Account'
  ELSE 'Checking Account'
END;

ALTER TABLE payments
  ADD COLUMN intermediary_bank VARCHAR(120) NULL;
