-- =============================================================================
-- Migration V038: SePay eInvoice API v1 Production Standard & State Machine
-- =============================================================================

-- 1. Extend invoice_status enum values
DO $$
BEGIN
  ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'CREATING';
  ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'PROCESSING';
  ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'DRAFT';
  ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'UNKNOWN';
  ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'ADJUSTED';
  ALTER TYPE public.invoice_status ADD VALUE IF NOT EXISTS 'REPLACED';
EXCEPTION
  WHEN duplicate_object THEN NULL;
END
$$;

-- 2. Extend electronic_invoices table
ALTER TABLE public.electronic_invoices
ADD COLUMN IF NOT EXISTS reference_code varchar(64),
ADD COLUMN IF NOT EXISTS create_tracking_code varchar(64),
ADD COLUMN IF NOT EXISTS issue_tracking_code varchar(64),
ADD COLUMN IF NOT EXISTS provider_account_id varchar(64),
ADD COLUMN IF NOT EXISTS template_code varchar(20),
ADD COLUMN IF NOT EXISTS invoice_series varchar(20),
ADD COLUMN IF NOT EXISTS is_draft boolean NOT NULL DEFAULT false,
ADD COLUMN IF NOT EXISTS buyer_type varchar(20),
ADD COLUMN IF NOT EXISTS buyer_name text,
ADD COLUMN IF NOT EXISTS buyer_legal_name text,
ADD COLUMN IF NOT EXISTS buyer_tax_code varchar(20),
ADD COLUMN IF NOT EXISTS buyer_address text,
ADD COLUMN IF NOT EXISTS buyer_email text,
ADD COLUMN IF NOT EXISTS buyer_phone text,
ADD COLUMN IF NOT EXISTS payment_method varchar(10) DEFAULT 'CK',
ADD COLUMN IF NOT EXISTS subtotal numeric(12, 2),
ADD COLUMN IF NOT EXISTS tax_rate integer,
ADD COLUMN IF NOT EXISTS tax_amount numeric(12, 2),
ADD COLUMN IF NOT EXISTS total_amount numeric(12, 2),
ADD COLUMN IF NOT EXISTS provider varchar(50),
ADD COLUMN IF NOT EXISTS provider_error_code varchar(50),
ADD COLUMN IF NOT EXISTS provider_error_message text,
ADD COLUMN IF NOT EXISTS next_retry_at timestamptz;

-- Backfill reference_code for existing invoices
UPDATE public.electronic_invoices ei
SET reference_code = 'INV-' || o.order_code
FROM public.orders o
WHERE ei.order_id = o.id AND ei.reference_code IS NULL;

-- Unique constraint on reference_code
CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_reference_code ON public.electronic_invoices(reference_code);
CREATE INDEX IF NOT EXISTS idx_invoices_create_tracking ON public.electronic_invoices(create_tracking_code);
CREATE INDEX IF NOT EXISTS idx_invoices_issue_tracking ON public.electronic_invoices(issue_tracking_code);

-- 3. Audit logs for invoice lifecycle
CREATE TABLE IF NOT EXISTS public.invoice_audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id uuid NOT NULL REFERENCES public.electronic_invoices(id) ON DELETE CASCADE,
    event varchar(50) NOT NULL,
    actor varchar(100) NOT NULL DEFAULT 'SYSTEM',
    message text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invoice_audit_invoice_id ON public.invoice_audit_logs(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_audit_created_at ON public.invoice_audit_logs(created_at);

-- 4. Extend billing_settings table for dynamic provider metadata
ALTER TABLE public.billing_settings
ADD COLUMN IF NOT EXISTS tax_authority_approved_date varchar(20),
ADD COLUMN IF NOT EXISTS available_templates jsonb DEFAULT '[]'::jsonb;
