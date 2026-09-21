-- =============================================================================
-- Migration V036: Seed Mock Data for Billing, Invoicing & SePay Reconciliation
-- =============================================================================

do $$
declare
  v_course_id uuid;
  v_order_1_id uuid;
  v_order_2_id uuid;
  v_order_4_id uuid;
begin
  -- 1. Ensure at least one course exists to reference
  select id into v_course_id from public.courses order by created_at desc limit 1;

  if v_course_id is null then
    v_course_id := gen_random_uuid();
    insert into public.courses (id, code, name, description, level, target_band, total_sessions, tuition_amount, is_public, is_active)
    values (
      v_course_id,
      'IELTS-INTENSIVE-75',
      'IELTS Intensive Mastery 7.5+',
      'Khóa học luyện thi chuyên sâu 4 kỹ năng cam kết đầu ra IELTS 7.5+',
      'Advanced',
      7.5,
      36,
      8500000.00,
      true,
      true
    );
  end if;

  -- 2. Mock Order 1: PAID with Valid CQT e-Invoice (B2B Business Buyer)
  select id into v_order_1_id from public.orders where order_code = 'KH260921001';
  if v_order_1_id is null then
    v_order_1_id := gen_random_uuid();
    insert into public.orders (
      id, order_code, course_id, customer_name, customer_email, customer_phone,
      amount, status, expires_at, paid_at, invoice_required, buyer_type,
      invoice_company_name, invoice_tax_code, invoice_address, invoice_email, created_at, updated_at
    ) values (
      v_order_1_id,
      'KH260921001',
      v_course_id,
      'Nguyễn Văn An',
      'an.nguyen@example.com',
      '0901234567',
      8500000.00,
      'PAID'::public.order_status,
      now() + interval '30 days',
      now() - interval '2 hours',
      true,
      'BUSINESS'::public.invoice_buyer_type,
      'CÔNG TY CỔ PHẦN CÔNG NGHỆ ALPHA TECH',
      '0108999888',
      'Số 123 Cầu Giấy, Phường Dịch Vọng Hậu, Quận Cầu Giấy, TP. Hà Nội',
      'ketoan@alphatech.vn',
      now() - interval '2 hours 15 minutes',
      now() - interval '2 hours'
    );

    -- Electronic Invoice for Order 1 (Issued with CQT code)
    insert into public.electronic_invoices (
      id, order_id, invoice_number, invoice_template,
      cqt_code, lookup_code, lookup_url, pdf_url,
      status, retry_count, issued_at, created_at, updated_at
    ) values (
      gen_random_uuid(),
      v_order_1_id,
      '0000125',
      '2C26TLN',
      'T26TLN-2C26TLN-0000125-9A8B7C',
      'SPY-884920',
      'https://einvoice.sepay.vn/tra-cuu/SPY-884920',
      'https://einvoice.sepay.vn/invoices/download/SPY-884920.pdf',
      'ISSUED'::public.invoice_status,
      0,
      now() - interval '2 hours',
      now() - interval '2 hours',
      now() - interval '2 hours'
    );

    -- Payment Transaction for Order 1
    insert into public.payment_transactions (
      id, gateway, sepay_transaction_id, order_id, order_code,
      amount_in, accumulated_amount, transfer_content, bank_brand_name, account_number,
      status, reconciliation_note, raw_payload, created_at
    ) values (
      gen_random_uuid(),
      'SEPAY',
      'SPY-TRX-1001',
      v_order_1_id,
      'KH260921001',
      8500000.00,
      8500000.00,
      'KH260921001 NGUYEN VAN AN chuyen tien hoc phi IELTS',
      'MBBank',
      '0987654321',
      'SUCCESS'::public.payment_transaction_status,
      'Khớp tự động qua SePay Webhook',
      '{}'::jsonb,
      now() - interval '2 hours'
    );
  end if;

  -- 3. Mock Order 2: PENDING_PAYMENT (Waiting for transfer / Manual Approval test)
  select id into v_order_2_id from public.orders where order_code = 'KH260921002';
  if v_order_2_id is null then
    v_order_2_id := gen_random_uuid();
    insert into public.orders (
      id, order_code, course_id, customer_name, customer_email, customer_phone,
      amount, status, expires_at, invoice_required, buyer_type, created_at, updated_at
    ) values (
      v_order_2_id,
      'KH260921002',
      v_course_id,
      'Trần Thị Mai',
      'mai.tran@example.com',
      '0912345678',
      6200000.00,
      'PENDING_PAYMENT'::public.order_status,
      now() + interval '25 minutes',
      true,
      'PERSONAL'::public.invoice_buyer_type,
      now() - interval '5 minutes',
      now() - interval '5 minutes'
    );
  end if;

  -- Ensure all orders are marked invoice_required = true for B2C compliance
  update public.orders set invoice_required = true where invoice_required is distinct from true;

  -- 4. Mock Transaction 3: UNMATCHED (Student forgot/misspelled order code in transfer content)
  if not exists (select 1 from public.payment_transactions where sepay_transaction_id = 'SPY-TRX-1003') then
    insert into public.payment_transactions (
      id, gateway, sepay_transaction_id, order_id, order_code,
      amount_in, accumulated_amount, transfer_content, bank_brand_name, account_number,
      status, reconciliation_note, raw_payload, created_at
    ) values (
      gen_random_uuid(),
      'SEPAY',
      'SPY-TRX-1003',
      null,
      null,
      6200000.00,
      6200000.00,
      'TRAN THI MAI chuyen khoan hoc phi qua VCB',
      'Vietcombank',
      '0987654321',
      'UNMATCHED'::public.payment_transaction_status,
      'Không nhận diện được mã đơn hàng trong nội dung chuyển khoản',
      '{}'::jsonb,
      now() - interval '4 minutes'
    );
  end if;

  -- 5. Mock Order 4: PAID but e-Invoice FAILED (For testing Retry / Cancel Invoice)
  select id into v_order_4_id from public.orders where order_code = 'KH260921004';
  if v_order_4_id is null then
    v_order_4_id := gen_random_uuid();
    insert into public.orders (
      id, order_code, course_id, customer_name, customer_email, customer_phone,
      amount, status, expires_at, paid_at, invoice_required, buyer_type,
      invoice_company_name, invoice_tax_code, invoice_address, created_at, updated_at
    ) values (
      v_order_4_id,
      'KH260921004',
      v_course_id,
      'Lê Hoàng Long',
      'long.le@example.com',
      '0934567890',
      5500000.00,
      'PAID'::public.order_status,
      now() + interval '30 days',
      now() - interval '1 hour',
      true,
      'BUSINESS'::public.invoice_buyer_type,
      'CÔNG TY TNHH PHÁT TRIỂN GIÁO DỤC TƯƠNG LAI',
      '0319888777',
      'Tòa nhà Landmark 81, 720A Điện Biên Phủ, Phường 22, Quận Bình Thạnh, TP.HCM',
      now() - interval '1 hour 10 minutes',
      now() - interval '1 hour'
    );

    -- Electronic Invoice FAILED for Order 4
    insert into public.electronic_invoices (
      id, order_id, invoice_template,
      status, retry_count, error_log, created_at, updated_at
    ) values (
      gen_random_uuid(),
      v_order_4_id,
      '2C26TLN',
      'FAILED'::public.invoice_status,
      2,
      'Tổng cục Thuế báo lỗi: Mã số thuế 0319888777 không tồn tại hoặc đã giải thể',
      now() - interval '1 hour',
      now() - interval '1 hour'
    );
  end if;

  -- 6. Ensure default Billing Settings exist and have rich company info
  if not exists (select 1 from public.billing_settings) then
    insert into public.billing_settings (
      id, sepay_account_number, sepay_bank_name, seller_name, seller_tax_code,
      seller_address, einvoice_template_code, is_sandbox, updated_at
    ) values (
      gen_random_uuid(),
      '0987654321',
      'MBBank',
      'CÔNG TY TNHH THE IELTS SPELLS VIỆT NAM',
      '052098014618',
      'Đường Tôn Đức Thắng, Phường Hòa Khánh Bắc, Quận Liên Chiểu, TP. Đà Nẵng',
      '2C26TLN',
      true,
      now()
    );
  else
    update public.billing_settings
    set seller_name = coalesce(seller_name, 'CÔNG TY TNHH THE IELTS SPELLS VIỆT NAM'),
        seller_tax_code = coalesce(seller_tax_code, '052098014618'),
        seller_address = coalesce(seller_address, 'Đường Tôn Đức Thắng, Phường Hòa Khánh Bắc, Quận Liên Chiểu, TP. Đà Nẵng'),
        updated_at = now();
  end if;
end
$$;
