-- =====================================================================
-- V25: PHAN HE 8 - TAI CHINH (UC-30 hoa don & thanh toan hoc phi,
-- UC-31 ghi nhan chi van hanh, UC-32 bao cao tai chinh)
--
-- 4 nhom bang theo docs/sdd-groups/07-tai-chinh-and-hoc-phi.md: Dinh muc
-- hoc phi (tuition_plans/tuition_plan_assignments), Hoc bong/Mien giam
-- (scholarships), Hoa don & Thanh toan (invoices/invoice_items/
-- invoice_scholarship_applications/payments), Chi van hanh
-- (expense_categories/operating_expenses). UC-32 khong can bang rieng -
-- chi query tren cac bang da co.
--
-- tuition_plans/scholarships khong co UC nao mo ta rieng luong tao/duyet
-- (UC-30/31/32 chi mo ta xem/thanh toan/chi/bao cao) - la ha tang bat
-- buoc de UC-30 sinh hoa don hoat dong duoc, gate duoi cung quyen
-- finance.manage nhu UC-31 (STAFF bo phan Ke toan), da ghi ro trong
-- Javadoc Service tuong ung.
-- =====================================================================

-- ============ 1) Dinh muc hoc phi ============

-- a) tuition_plans - Khong history, thay doi plan tao record moi thay vi sua (SDD)
CREATE TABLE tuition_plans (
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    curriculum_id      BIGINT NOT NULL REFERENCES curriculums(id),
    code               VARCHAR(50) UNIQUE NOT NULL,
    name               VARCHAR(300) NOT NULL,
    pricing_model      VARCHAR(20) NOT NULL, -- COURSE / PER_SESSION / MONTHLY
    class_type_filter  VARCHAR(20) NULL,     -- LINKED / OPEN / NULL (ca 2)
    base_price         DECIMAL(15,2) NOT NULL,
    price_per_unit     DECIMAL(15,2) NULL,
    unit_count         INT NULL,
    currency           VARCHAR(3) NOT NULL DEFAULT 'VND',
    effective_from     DATE NULL,
    effective_to       DATE NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / INACTIVE
    created_by         BIGINT NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tuition_plans_curriculum ON tuition_plans(curriculum_id);

-- b) tuition_plan_assignments - Moi lop tai 1 thoi diem chi co 1 plan active (SDD)
CREATE TABLE tuition_plan_assignments (
    id                BIGSERIAL PRIMARY KEY,
    class_id          BIGINT NOT NULL REFERENCES classes(id),
    tuition_plan_id   BIGINT NOT NULL REFERENCES tuition_plans(id),
    price_override    DECIMAL(15,2) NULL,
    override_reason   TEXT NULL,
    effective_from    DATE NULL,
    effective_to      DATE NULL,
    assigned_by       BIGINT NOT NULL REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_tuition_plan_assignments_active_class
    ON tuition_plan_assignments(class_id) WHERE effective_to IS NULL;

-- ============ 2) Hoc bong / Mien giam ============

-- Khong history - thay doi thi REVOKE record cu, tao record moi (SDD)
CREATE TABLE scholarships (
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    student_id         BIGINT NOT NULL REFERENCES students(id),
    code               VARCHAR(50) UNIQUE NOT NULL,
    name               VARCHAR(300) NOT NULL,
    discount_type      VARCHAR(20) NOT NULL, -- PERCENTAGE / FIXED_AMOUNT
    discount_value     DECIMAL(15,2) NOT NULL,
    applicable_scope   VARCHAR(20) NOT NULL DEFAULT 'PER_INVOICE', -- PER_INVOICE / ONE_TIME
    valid_from         DATE NULL,
    valid_to           DATE NULL,
    max_amount         DECIMAL(15,2) NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / EXPIRED / REVOKED
    approved_by        BIGINT NOT NULL REFERENCES users(id),
    approved_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scholarships_student ON scholarships(student_id);

-- ============ 3) Hoa don & Thanh toan ============

-- a) invoices - Co invoices_history. Cron nightly chuyen OVERDUE (UC-30 A1).
CREATE TABLE invoices (
    id                     BIGSERIAL PRIMARY KEY,
    uuid                   UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    invoice_number         VARCHAR(50) UNIQUE NOT NULL,
    student_id             BIGINT NOT NULL REFERENCES students(id),
    class_enrollment_id    BIGINT NULL REFERENCES class_enrollments(id),
    payer_parent_id        BIGINT NULL REFERENCES parents(id),
    billing_period_from    DATE NULL,
    billing_period_to      DATE NULL,
    issue_date             DATE NOT NULL,
    due_date               DATE NOT NULL,
    subtotal               DECIMAL(15,2) NOT NULL,
    discount_total         DECIMAL(15,2) NOT NULL DEFAULT 0,
    tax_amount             DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount           DECIMAL(15,2) NOT NULL,
    paid_amount            DECIMAL(15,2) NOT NULL DEFAULT 0,
    outstanding_amount     DECIMAL(15,2) GENERATED ALWAYS AS (total_amount - paid_amount) STORED,
    status                 VARCHAR(20) NOT NULL DEFAULT 'ISSUED', -- DRAFT / ISSUED / PARTIAL_PAID / PAID / OVERDUE / CANCELLED
    qr_code_data           TEXT NULL,
    created_by             BIGINT NULL REFERENCES users(id), -- NULL neu sinh tu dong
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ NULL
);
CREATE INDEX idx_invoices_status_due ON invoices(status, due_date)
    WHERE status IN ('ISSUED', 'PARTIAL_PAID', 'OVERDUE');
CREATE INDEX idx_invoices_student ON invoices(student_id);
CREATE INDEX idx_invoices_payer_parent ON invoices(payer_parent_id);

CREATE TABLE invoices_history (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT NOT NULL REFERENCES invoices(id),
    changed_by  BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details     JSONB NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invoices_history_invoice ON invoices_history(invoice_id);

-- b) invoice_items
CREATE TABLE invoice_items (
    id                     BIGSERIAL PRIMARY KEY,
    invoice_id             BIGINT NOT NULL REFERENCES invoices(id),
    item_type              VARCHAR(30) NOT NULL, -- TUITION / MATERIAL / EXAM_FEE / LATE_FEE / OTHER
    description            VARCHAR(500) NOT NULL,
    tuition_plan_id        BIGINT NULL REFERENCES tuition_plans(id),
    quantity               DECIMAL(10,2) NOT NULL DEFAULT 1,
    unit_price             DECIMAL(15,2) NOT NULL,
    amount                 DECIMAL(15,2) NOT NULL,
    calculation_snapshot   JSONB NULL
);
CREATE INDEX idx_invoice_items_invoice ON invoice_items(invoice_id);

-- c) invoice_scholarship_applications
CREATE TABLE invoice_scholarship_applications (
    id                BIGSERIAL PRIMARY KEY,
    invoice_id        BIGINT NOT NULL REFERENCES invoices(id),
    scholarship_id    BIGINT NOT NULL REFERENCES scholarships(id),
    discount_amount   DECIMAL(15,2) NOT NULL, -- Da snapshot, khong tinh lai
    applied_by        BIGINT NOT NULL REFERENCES users(id),
    UNIQUE(invoice_id, scholarship_id)
);

-- d) payments - 1 hoa don co the co nhieu payment. Co payments_history.
CREATE TABLE payments (
    id                     BIGSERIAL PRIMARY KEY,
    uuid                   UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    payment_reference      VARCHAR(100) UNIQUE NOT NULL,
    invoice_id             BIGINT NOT NULL REFERENCES invoices(id),
    amount                 DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    payment_method         VARCHAR(20) NOT NULL, -- QR_BANK / CASH / BANK_TRANSFER / OTHER
    paid_at                TIMESTAMPTZ NOT NULL,
    bank_transaction_id    VARCHAR(200) NULL, -- Doi soat QR
    receipt_number         VARCHAR(50) NULL,   -- Voi CASH
    status                 VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED', -- PENDING / CONFIRMED / REFUNDED
    confirmed_by           BIGINT NULL REFERENCES users(id), -- Ke toan xac nhan
    confirmed_at           TIMESTAMPTZ NULL
);
CREATE INDEX idx_payments_invoice ON payments(invoice_id);

CREATE TABLE payments_history (
    id          BIGSERIAL PRIMARY KEY,
    payment_id  BIGINT NOT NULL REFERENCES payments(id),
    changed_by  BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(20) NOT NULL,
    details     JSONB NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_history_payment ON payments_history(payment_id);

-- ============ 4) Chi van hanh ============

-- a) expense_categories - Khong history
CREATE TABLE expense_categories (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(50) UNIQUE NOT NULL,
    name             VARCHAR(200) NOT NULL,
    category_group   VARCHAR(30) NOT NULL, -- HR / FACILITY / TECH / MARKETING / OPERATION / OTHER
    is_active        BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO expense_categories (code, name, category_group) VALUES
('SALARY',    'Chi lương',                  'HR'),
('RENT',      'Chi phí mặt bằng',           'FACILITY'),
('UTILITY',   'Chi phí điện nước/tiện ích', 'FACILITY'),
('TECH',      'Chi phí bản quyền công nghệ', 'TECH'),
('CDN',       'Chi phí hạ tầng CDN',        'TECH'),
('MARKETING', 'Chi phí marketing',          'MARKETING'),
('OTHER',     'Khác',                       'OTHER');

-- b) operating_expenses - Co operating_expenses_history
CREATE TABLE operating_expenses (
    id                     BIGSERIAL PRIMARY KEY,
    uuid                   UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    expense_number         VARCHAR(50) UNIQUE NOT NULL,
    expense_category_id    BIGINT NOT NULL REFERENCES expense_categories(id),
    site_id                BIGINT NULL REFERENCES sites(id), -- NULL = chi chung toan he thong
    expense_date           DATE NOT NULL,
    amount                 DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    description            TEXT NOT NULL,
    payment_method         VARCHAR(20) NOT NULL, -- CASH / BANK_TRANSFER / CARD / OTHER
    supplier_name          VARCHAR(300) NULL,
    receipt_number         VARCHAR(100) NULL,
    file_url               VARCHAR(500) NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'RECORDED', -- RECORDED / APPROVED / REJECTED
    approved_by            BIGINT NULL REFERENCES users(id),
    recorded_by            BIGINT NOT NULL REFERENCES users(id), -- Ke toan
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_operating_expenses_site_date ON operating_expenses(site_id, expense_date);

CREATE TABLE operating_expenses_history (
    id           BIGSERIAL PRIMARY KEY,
    expense_id   BIGINT NOT NULL REFERENCES operating_expenses(id),
    changed_by   BIGINT NOT NULL REFERENCES users(id),
    action       VARCHAR(20) NOT NULL,
    details      JSONB NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_operating_expenses_history_expense ON operating_expenses_history(expense_id);

-- ============ 5) Quyen + cau hinh he thong ============

-- Quyen finance.manage (UC-31 Precondition: "role STAFF thuoc bo phan Ke
-- toan, co quyen finance.manage"). Ap dung chung cho toan bo Phan he 8
-- (tuition_plans/scholarships/invoices/payments/operating_expenses) vi
-- khong co UC nao khac dinh nghia quyen rieng cho tung hanh dong.
INSERT INTO permissions (code, name, module, description) VALUES
('finance.manage', 'Quản lý tài chính (học phí, chi vận hành)', 'FINANCE', 'FR-FIN-01,FR-FIN-02,FR-FIN-03');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'STAFF' AND p.code = 'finance.manage';

-- UC-30 Main Flow buoc 1 noi "tu dong xuat hoa don dinh ky" nhung khong
-- neu ro chu ky - da xac nhan voi user: cron hang dem kiem tra ngay trong
-- thang, mac dinh ngay 1 (PM doi duoc sau khong can sua code).
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('finance.invoice_generation_day_of_month', '1', 'Ngày trong tháng cron tự động sinh hóa đơn định kỳ (UC-30 Main Flow bước 1)', 'FINANCE');

-- UC-30 khong neu so ngay tu issue_date toi due_date - ap dung lai pattern
-- system_settings-configurable da dung cho task.due_soon_reminder_hours
-- (V23), mac dinh 15 ngay, PM doi duoc sau khong can sua code.
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('finance.invoice_due_days', '15', 'Số ngày từ issue_date tới due_date khi cron tự động sinh hóa đơn (UC-30 Main Flow bước 1)', 'FINANCE');

-- UC-30 buoc 3 can "ma QR ngan hang dong" nhung SDD/system_settings chua
-- co cau hinh tai khoan ngan hang that, codebase chua co thu vien sinh QR
-- (zxing) - da xac nhan voi user: qr_code_data la chuoi placeholder text
-- (KHONG dung chuan EMVCo VietQR that) tu cac gia tri cau hinh nay, cho
-- toi khi tich hop ngan hang that.
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('finance.bank_name', '"PPS Bank (placeholder)"', 'Tên ngân hàng dùng để sinh qr_code_data placeholder (UC-30 bước 3, chưa tích hợp ngân hàng thật)', 'FINANCE'),
('finance.bank_account_number', '"0000000000"', 'Số tài khoản ngân hàng dùng để sinh qr_code_data placeholder (UC-30 bước 3, chưa tích hợp ngân hàng thật)', 'FINANCE'),
('finance.bank_bin', '"970000"', 'Mã BIN ngân hàng (VietQR) dùng để sinh qr_code_data placeholder (UC-30 bước 3, chưa tích hợp ngân hàng thật)', 'FINANCE');
