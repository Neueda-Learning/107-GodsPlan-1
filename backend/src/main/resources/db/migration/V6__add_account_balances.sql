ALTER TABLE accounts
  ADD COLUMN available_balance DECIMAL(19,2) NULL;

UPDATE accounts
SET available_balance = CASE currency
  WHEN 'INR' THEN 500000.00
  WHEN 'JPY' THEN 1000000.00
  ELSE 25000.00
END
WHERE available_balance IS NULL;

ALTER TABLE accounts
  MODIFY available_balance DECIMAL(19,2) NOT NULL;
