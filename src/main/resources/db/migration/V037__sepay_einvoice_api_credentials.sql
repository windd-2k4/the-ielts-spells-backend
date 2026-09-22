-- =============================================================================
-- Migration V037: Add SePay eInvoice API credentials and configuration
-- =============================================================================

ALTER TABLE public.billing_settings
ADD COLUMN IF NOT EXISTS einvoice_client_id text,
ADD COLUMN IF NOT EXISTS einvoice_client_secret text,
ADD COLUMN IF NOT EXISTS einvoice_provider_account_id text,
ADD COLUMN IF NOT EXISTS einvoice_invoice_series varchar(20) DEFAULT 'C26TSE';

-- Update existing default settings with provided SePay Sandbox credentials
UPDATE public.billing_settings
SET einvoice_client_id = 'EINV-TEST-EB0HM0MMQW9LMZHP',
    einvoice_client_secret = 'd9f2e85f5151eb2f07d875a23113ecd0',
    einvoice_provider_account_id = 'f20729d6-b5d9-11f1-b21a-a6006ab65aca',
    einvoice_invoice_series = 'C26TSE',
    einvoice_template_code = '1',
    is_sandbox = true,
    updated_at = now()
WHERE id IN (
    SELECT id FROM public.billing_settings ORDER BY updated_at DESC LIMIT 1
);
