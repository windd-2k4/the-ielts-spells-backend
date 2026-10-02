-- =============================================================================
-- Migration V039: Fix eInvoice Retry, Reconciliation Status & Tax Configuration
-- =============================================================================

-- 1. Create reconciliation_status enum
DO $$
BEGIN
  CREATE TYPE public.reconciliation_status AS ENUM (
    'NOT_REQUIRED',
    'PENDING',
    'RECONCILED',
    'FAILED',
    'REQUIRES_REVIEW'
  );
EXCEPTION
  WHEN duplicate_object THEN NULL;
END
$$;

-- 2. Extend electronic_invoices table
ALTER TABLE public.electronic_invoices
ADD COLUMN IF NOT EXISTS tax_treatment varchar(30) NOT NULL DEFAULT 'NOT_SUBJECT_TO_VAT',
ADD COLUMN IF NOT EXISTS reconciliation_status varchar(30) NOT NULL DEFAULT 'NOT_REQUIRED',
ADD COLUMN IF NOT EXISTS error_category varchar(30),
ADD COLUMN IF NOT EXISTS first_submitted_at timestamptz,
ADD COLUMN IF NOT EXISTS last_status_checked_at timestamptz;

-- 3. Extend billing_settings table
ALTER TABLE public.billing_settings
ADD COLUMN IF NOT EXISTS tax_treatment varchar(30) NOT NULL DEFAULT 'NOT_SUBJECT_TO_VAT',
ADD COLUMN IF NOT EXISTS invoice_type varchar(20) NOT NULL DEFAULT 'VAT';

-- 4. Create indexes for efficient background worker processing and reconciliation
CREATE INDEX IF NOT EXISTS idx_invoices_reconciliation_status ON public.electronic_invoices(reconciliation_status);
CREATE INDEX IF NOT EXISTS idx_invoices_error_category ON public.electronic_invoices(error_category);
CREATE INDEX IF NOT EXISTS idx_invoices_first_submitted_at ON public.electronic_invoices(first_submitted_at);
