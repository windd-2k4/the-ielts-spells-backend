-- Transaction-centric billing for the production static-QR workflow.
-- Every accepted incoming bank transaction owns exactly one invoice, while an
-- order may be settled by many deposits/installments.

DO $$
BEGIN
  ALTER TYPE public.payment_transaction_status ADD VALUE IF NOT EXISTS 'PARTIAL_PAYMENT';
EXCEPTION
  WHEN duplicate_object THEN NULL;
END
$$;

ALTER TABLE public.payment_transactions
  ADD COLUMN IF NOT EXISTS payer_name text;

ALTER TABLE public.payment_transactions
  DROP CONSTRAINT IF EXISTS chk_payment_transactions_positive_amount;

ALTER TABLE public.payment_transactions
  ADD CONSTRAINT chk_payment_transactions_positive_amount CHECK (amount_in > 0);

ALTER TABLE public.electronic_invoices
  ALTER COLUMN order_id DROP NOT NULL;

ALTER TABLE public.electronic_invoices
  DROP CONSTRAINT IF EXISTS electronic_invoices_order_id_key;

ALTER TABLE public.electronic_invoices
  ADD COLUMN IF NOT EXISTS payment_transaction_id uuid
    REFERENCES public.payment_transactions(id) ON DELETE RESTRICT,
  ADD COLUMN IF NOT EXISTS product_name text NOT NULL DEFAULT 'Đóng học phí đào tạo IELTS';

CREATE UNIQUE INDEX IF NOT EXISTS uq_electronic_invoices_payment_transaction
  ON public.electronic_invoices(payment_transaction_id)
  WHERE payment_transaction_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_electronic_invoices_order_created
  ON public.electronic_invoices(order_id, created_at DESC)
  WHERE order_id IS NOT NULL;

-- Remove the exact demo accounting records introduced by V036. Production
-- financial reports must never contain seeded revenue or fake tax invoices.
DELETE FROM public.payment_transactions
WHERE sepay_transaction_id IN ('SPY-TRX-1001', 'SPY-TRX-1003');

DELETE FROM public.electronic_invoices
WHERE order_id IN (
  SELECT id FROM public.orders
  WHERE order_code IN ('KH260921001', 'KH260921002', 'KH260921004')
    AND customer_email IN ('an.nguyen@example.com', 'mai.tran@example.com', 'long.le@example.com')
);

DELETE FROM public.orders
WHERE order_code IN ('KH260921001', 'KH260921002', 'KH260921004')
  AND customer_email IN ('an.nguyen@example.com', 'mai.tran@example.com', 'long.le@example.com');

DELETE FROM public.courses c
WHERE c.code = 'IELTS-INTENSIVE-75'
  AND c.description = 'Khóa học luyện thi chuyên sâu 4 kỹ năng cam kết đầu ra IELTS 7.5+'
  AND NOT EXISTS (SELECT 1 FROM public.orders o WHERE o.course_id = c.id);

-- The sandbox credential seeded by V037 was committed as plaintext and must
-- not remain usable. A fresh credential has to be supplied through Settings.
UPDATE public.billing_settings
SET einvoice_client_secret = NULL,
    updated_at = now()
WHERE einvoice_client_id = 'EINV-TEST-EB0HM0MMQW9LMZHP'
  AND einvoice_client_secret IS NOT NULL
  AND einvoice_client_secret NOT LIKE 'enc:v1:%';

-- Fail closed instead of ever producing a payable QR from placeholder data.
UPDATE public.billing_settings
SET sepay_account_number = NULL,
    updated_at = now()
WHERE sepay_account_number = '0987654321';
