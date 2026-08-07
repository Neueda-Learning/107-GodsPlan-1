ALTER TABLE payments DROP CHECK chk_payment_status;

ALTER TABLE payments ADD CONSTRAINT chk_payment_status
  CHECK (status IN ('CREATED','VALIDATED','SENT','COMPLETED','FAILED'));
