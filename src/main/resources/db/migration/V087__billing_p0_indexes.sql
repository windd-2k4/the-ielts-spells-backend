CREATE INDEX IF NOT EXISTS idx_orders_created_at
    ON orders (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_electronic_invoices_created_at
    ON electronic_invoices (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_electronic_invoices_status_retry
    ON electronic_invoices (status, next_retry_at, created_at);

CREATE INDEX IF NOT EXISTS idx_electronic_invoices_reconciliation_retry
    ON electronic_invoices (reconciliation_status, next_retry_at, created_at);
