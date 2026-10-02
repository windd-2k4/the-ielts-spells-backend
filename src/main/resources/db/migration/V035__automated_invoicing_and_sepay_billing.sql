-- =============================================================================
-- Migration V035: Automated Invoicing and SePay Billing System
-- =============================================================================

-- 1. ENUMS
do $$
begin
  if not exists (select 1 from pg_type where typname = 'order_status') then
    create type public.order_status as enum (
      'PENDING_PAYMENT',
      'PAID',
      'CANCELLED',
      'EXPIRED',
      'REFUNDED'
    );
  end if;

  if not exists (select 1 from pg_type where typname = 'payment_transaction_status') then
    create type public.payment_transaction_status as enum (
      'SUCCESS',
      'UNDERPAID',
      'OVERPAID',
      'UNMATCHED',
      'REFUNDED'
    );
  end if;

  if not exists (select 1 from pg_type where typname = 'invoice_status') then
    create type public.invoice_status as enum (
      'PENDING_ISSUE',
      'ISSUING',
      'ISSUED',
      'FAILED',
      'CANCELLED'
    );
  end if;

  if not exists (select 1 from pg_type where typname = 'invoice_buyer_type') then
    create type public.invoice_buyer_type as enum (
      'PERSONAL',
      'BUSINESS'
    );
  end if;
end
$$;

-- 2. ORDERS TABLE
create table if not exists public.orders (
    id uuid primary key default gen_random_uuid(),
    order_code varchar(32) not null unique,
    course_id uuid not null references public.courses(id) on delete restrict,
    user_id uuid references public.profiles(id) on delete set null,
    customer_name text not null,
    customer_email text not null,
    customer_phone text,
    amount numeric(12, 2) not null check (amount >= 0),
    status public.order_status not null default 'PENDING_PAYMENT',
    expires_at timestamptz not null,
    paid_at timestamptz,
    invoice_required boolean not null default true,
    buyer_type public.invoice_buyer_type not null default 'PERSONAL',
    invoice_company_name text,
    invoice_tax_code varchar(20),
    invoice_address text,
    invoice_email varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_orders_status on public.orders(status);
create index if not exists idx_orders_code on public.orders(order_code);
create index if not exists idx_orders_customer_email on public.orders(customer_email);
create index if not exists idx_orders_course_id on public.orders(course_id);

-- 3. PAYMENT_TRANSACTIONS TABLE
create table if not exists public.payment_transactions (
    id uuid primary key default gen_random_uuid(),
    gateway varchar(20) not null default 'SEPAY',
    sepay_transaction_id varchar(64) not null unique,
    order_id uuid references public.orders(id) on delete set null,
    order_code varchar(32),
    amount_in numeric(12, 2) not null,
    accumulated_amount numeric(12, 2),
    transfer_content text,
    bank_brand_name varchar(50),
    account_number varchar(50),
    status public.payment_transaction_status not null default 'SUCCESS',
    reconciliation_note text,
    raw_payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_payment_trans_order_id on public.payment_transactions(order_id);
create index if not exists idx_payment_trans_status on public.payment_transactions(status);
create index if not exists idx_payment_trans_order_code on public.payment_transactions(order_code);

-- 4. ELECTRONIC_INVOICES TABLE
create table if not exists public.electronic_invoices (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null unique references public.orders(id) on delete restrict,
    invoice_template varchar(20),
    invoice_number varchar(20),
    cqt_code varchar(100),
    lookup_code varchar(64),
    lookup_url text,
    pdf_url text,
    xml_url text,
    status public.invoice_status not null default 'PENDING_ISSUE',
    retry_count int not null default 0,
    error_log text,
    issued_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_invoices_order_id on public.electronic_invoices(order_id);
create index if not exists idx_invoices_status on public.electronic_invoices(status);

-- 5. ACCOUNT_ACTIVATION_TOKENS TABLE
create table if not exists public.account_activation_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references public.profiles(id) on delete cascade,
    order_id uuid not null references public.orders(id) on delete cascade,
    customer_email text not null,
    customer_name text not null,
    token_hash varchar(128) not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists idx_activation_tokens_lookup on public.account_activation_tokens(token_hash, used_at);

-- 6. BILLING_SETTINGS TABLE
create table if not exists public.billing_settings (
    id uuid primary key default gen_random_uuid(),
    sepay_api_key text,
    sepay_webhook_secret text,
    sepay_account_number text,
    sepay_bank_name text,
    einvoice_api_token text,
    einvoice_template_code varchar(20) default '2C26TLN',
    einvoice_tax_rate numeric(4, 2) default 0.00,
    seller_name text default 'HỘ KINH DOANH THE IELTS SPELLS',
    seller_tax_code varchar(20),
    seller_address text,
    is_sandbox boolean not null default true,
    updated_at timestamptz not null default now()
);

-- Seed default initial row for billing_settings
insert into public.billing_settings (
    sepay_account_number,
    sepay_bank_name,
    einvoice_template_code,
    seller_name,
    is_sandbox
) values (
    '0987654321',
    'MBBank',
    '2C26TLN',
    'HỘ KINH DOANH THE IELTS SPELLS',
    true
) on conflict do nothing;
