-- =============================================================================
-- Migration V040: Production Readiness, Dual-Context & Secret Hardening
-- =============================================================================

-- 1. Extend billing_settings for Dual-Context (Sandbox vs Production),
--    Activation State Machine, and Emergency Kill-Switch.
ALTER TABLE public.billing_settings
ADD COLUMN IF NOT EXISTS activation_state varchar(30) NOT NULL DEFAULT 'SANDBOX',
ADD COLUMN IF NOT EXISTS auto_invoice_enabled boolean NOT NULL DEFAULT true,
ADD COLUMN IF NOT EXISTS prod_client_id text,
ADD COLUMN IF NOT EXISTS prod_client_secret text,
ADD COLUMN IF NOT EXISTS prod_provider_account_id text,
ADD COLUMN IF NOT EXISTS prod_invoice_series varchar(20),
ADD COLUMN IF NOT EXISTS prod_template_code varchar(20),
ADD COLUMN IF NOT EXISTS prod_tax_authority_approved_date varchar(20),
ADD COLUMN IF NOT EXISTS pilot_order_allowlist jsonb DEFAULT '[]'::jsonb;

-- 2. Extend orders for Controlled Pilot explicit approval
ALTER TABLE public.orders
ADD COLUMN IF NOT EXISTS pilot_approved boolean NOT NULL DEFAULT false;

-- 3. Allow invoice_audit_logs to record system-level billing configuration events
ALTER TABLE public.invoice_audit_logs
ALTER COLUMN invoice_id DROP NOT NULL;

-- 4. Ensure current records have clean default values
UPDATE public.billing_settings
SET activation_state = 'SANDBOX',
    auto_invoice_enabled = true
WHERE activation_state IS NULL;
