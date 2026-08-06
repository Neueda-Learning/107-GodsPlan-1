-- Add transaction fee columns to the payments table.
-- fee_amount        : the 2% platform fee charged on the original amount.
-- total_debit_amount: amount + fee_amount; the total sum deducted from the sender's account.
ALTER TABLE payments
  ADD COLUMN fee_amount         DECIMAL(15,2) NOT NULL DEFAULT 0.00 AFTER amount,
  ADD COLUMN total_debit_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00 AFTER fee_amount;

-- Back-fill existing rows so the constraint below is satisfied.
UPDATE payments
SET fee_amount         = ROUND(amount * 0.02, 2),
    total_debit_amount = amount + ROUND(amount * 0.02, 2);

ALTER TABLE payments
  ADD CONSTRAINT chk_fee_amount          CHECK (fee_amount >= 0),
  ADD CONSTRAINT chk_total_debit_amount  CHECK (total_debit_amount >= amount);
