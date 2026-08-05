UPDATE customer_users
SET full_name = 'Nihal Yadav', email = 'nihal.yadav@example.com'
WHERE id = 2;

UPDATE customer_users
SET full_name = 'Sriya Patel', email = 'sriya.patel@example.com'
WHERE id = 3;

UPDATE customer_users
SET full_name = 'Shruti Sharma', email = 'shruti.sharma@example.com'
WHERE id = 4;

INSERT INTO customer_users (id, full_name, email, role, active) VALUES
  (5, 'Tushar Mehta', 'tushar.mehta@example.com', 'CUSTOMER', TRUE);

INSERT INTO accounts (account_number, currency, active, customer_id) VALUES
  ('ACC-0007', 'USD', TRUE, 5);
SET @tushar_account_id = LAST_INSERT_ID();

INSERT INTO payment_cards (customer_id, account_id, brand, last_four, expiry_month, expiry_year) VALUES
  (5, @tushar_account_id, 'Visa', '9021', 6, 2030);

INSERT INTO payments (idempotency_key, amount, currency, source_account_id, destination_account_id,
  reference, status, payment_method) VALUES
  ('seed-tushar-completed', 240.00, 'USD', @tushar_account_id, 1,
   'Project payment', 'COMPLETED', 'Visa ending 9021');
SET @tushar_payment_id = LAST_INSERT_ID();

INSERT INTO payment_status_history (payment_id, from_status, to_status) VALUES
  (@tushar_payment_id, NULL, 'CREATED'),
  (@tushar_payment_id, 'CREATED', 'VALIDATED'),
  (@tushar_payment_id, 'VALIDATED', 'SENT'),
  (@tushar_payment_id, 'SENT', 'COMPLETED');
