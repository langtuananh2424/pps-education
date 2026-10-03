-- =====================================================================
-- V211: Tích hợp cổng thanh toán payOS (UC-30 Main Flow bước 3-6) — bổ
-- sung ngoài SDD gốc, đã thống nhất với người dùng 2026-10-03 (chọn payOS
-- làm nền tảng QR + webhook).
--
-- invoice_payment_links: mỗi lần Phụ huynh bấm "Thanh toán QR" sinh 1 link
-- payOS cho PHẦN CÒN NỢ của hóa đơn tại thời điểm đó. order_code là mã số
-- (BIGINT) payOS yêu cầu, duy nhất toàn tài khoản payOS; webhook payOS
-- đối chiếu hóa đơn qua order_code. Hóa đơn thanh toán từng phần sẽ có nhiều
-- link (mỗi link ứng với 1 số tiền còn nợ khác nhau).
-- =====================================================================
CREATE TABLE invoice_payment_links (
    id                 BIGSERIAL PRIMARY KEY,
    invoice_id         BIGINT        NOT NULL REFERENCES invoices (id),
    provider           VARCHAR(20)   NOT NULL DEFAULT 'PAYOS',
    order_code         BIGINT        NOT NULL,
    payment_link_id    VARCHAR(100),
    amount             NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED')),
    checkout_url       TEXT,
    qr_code            TEXT,
    bin                VARCHAR(20),
    account_number     VARCHAR(50),
    account_name       VARCHAR(200),
    description        VARCHAR(50),
    expires_at         TIMESTAMPTZ   NOT NULL,
    paid_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_invoice_payment_links_provider_order
    ON invoice_payment_links (provider, order_code);

CREATE INDEX idx_invoice_payment_links_invoice
    ON invoice_payment_links (invoice_id, status);
