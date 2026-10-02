-- Distinguish intentional static-QR tuition receipts from malformed dynamic
-- payments that genuinely need reconciliation.
DO $$
BEGIN
  ALTER TYPE public.payment_transaction_status ADD VALUE IF NOT EXISTS 'STANDALONE_PAYMENT';
EXCEPTION
  WHEN duplicate_object THEN NULL;
END
$$;
