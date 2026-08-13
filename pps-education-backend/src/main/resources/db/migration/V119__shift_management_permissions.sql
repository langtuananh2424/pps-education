-- =====================================================================
-- V119: Quản lý danh mục ca & gán ca cho nhân sự (bổ sung ngoài UC-09 gốc,
-- xác nhận với người dùng 2026-08-13 — xem docs/uc/phan-he-04-nhan-su.md)
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('hrm.shift.create', 'Tạo ca làm việc', 'HRM', 'Quản lý danh mục ca (bổ sung ngoài SDD gốc, 2026-08-13)'),
('hrm.shift.update', 'Sửa/tắt ca làm việc', 'HRM', 'Quản lý danh mục ca (bổ sung ngoài SDD gốc, 2026-08-13)'),
('hrm.employee-shift.assign', 'Gán ca cho nhân sự (đơn lẻ/hàng loạt)', 'HRM', 'Bổ sung ngoài SDD gốc, 2026-08-13');
