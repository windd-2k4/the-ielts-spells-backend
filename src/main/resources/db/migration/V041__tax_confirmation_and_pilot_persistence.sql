-- V041: Tax Configuration Confirmation & Pilot Execution Persistence

-- 1. Add Tax Confirmation audit fields and pilot tracking to billing_settings
ALTER TABLE billing_settings
    ADD COLUMN IF NOT EXISTS tax_configuration_confirmed BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tax_configuration_confirmed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS tax_configuration_confirmed_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_pilot_execution_id UUID,
    ADD COLUMN IF NOT EXISTS pilot_status VARCHAR(50) DEFAULT 'NOT_STARTED';

-- 2. Create billing_pilot_executions table
CREATE TABLE IF NOT EXISTS billing_pilot_executions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    order_code VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    order_paid BOOLEAN NOT NULL DEFAULT FALSE,
    payment_success BOOLEAN NOT NULL DEFAULT FALSE,
    payment_transaction_id VARCHAR(100),
    enrollment_active BOOLEAN NOT NULL DEFAULT FALSE,
    invoice_issued BOOLEAN NOT NULL DEFAULT FALSE,
    invoice_reference_code VARCHAR(100),
    invoice_number VARCHAR(100),
    invoice_reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    pdf_status VARCHAR(50),
    xml_status VARCHAR(50),
    email_status VARCHAR(50),
    failure_reason TEXT,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    evaluated_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pilot_executions_order_id ON billing_pilot_executions(order_id);
CREATE INDEX IF NOT EXISTS idx_pilot_executions_status ON billing_pilot_executions(status);
