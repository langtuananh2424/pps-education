-- =====================================================================
-- V210: Hoàn thiện Phân hệ 8 (UC-30) — lịch sử thay đổi do hệ thống tự
-- động + quyền hủy hóa đơn. Bổ sung ngoài SDD gốc, đã thống nhất với người
-- dùng 2026-10-03 (lộ trình hoàn thiện tài chính, mục 2-3).
--
-- 1. invoices_history / payments_history.changed_by cho phép NULL = thay
--    đổi do hệ thống tự động (cron sinh hóa đơn định kỳ, cron đánh dấu
--    OVERDUE, webhook ngân hàng gạch nợ). Trước đây NOT NULL nên code bỏ
--    qua không ghi lịch sử khi không có người thao tác — mất dấu vết đúng ở
--    các thay đổi quan trọng nhất cần đối soát. Giống invoices.created_by
--    (NULL nếu sinh tự động) đã có từ V25.
-- 2. Quyền finance.invoice.cancel — Kế toán hủy hóa đơn phát hành sai (chưa
--    có khoản thu nào). Lý do hủy lưu trong invoices_history.details.
-- =====================================================================

ALTER TABLE invoices_history ALTER COLUMN changed_by DROP NOT NULL;
ALTER TABLE payments_history ALTER COLUMN changed_by DROP NOT NULL;

INSERT INTO permissions (code, name, module, description) VALUES
('finance.invoice.cancel', 'Hủy hoá đơn', 'FINANCE', 'V210 — hủy hóa đơn phát hành sai (UC-30)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('ACCOUNTANT', 'EXECUTIVE', 'SYS_ADMIN')
  AND p.code = 'finance.invoice.cancel'
ON CONFLICT (role_id, permission_id) DO NOTHING;
