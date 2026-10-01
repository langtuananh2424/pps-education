-- =====================================================================
-- V206: Giới hạn trang "Lịch sử thay đổi dữ liệu" theo phòng ban.
--
-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-01: Trưởng
-- phòng đào tạo chỉ theo dõi thay đổi do chính mình và nhân sự thuộc phòng
-- ban mình làm trưởng phòng (departments.head_user_id, nhân sự theo
-- employees.department_id) thực hiện — cùng quy tắc "trưởng phòng" của
-- tổng quan công việc (TaskService#listOverview).
--
-- academic.change-history.view-all: xem thay đổi của mọi nhân sự, không giới
-- hạn phòng ban (tương tự task.overview.company). Cấp cho Ban giám đốc và
-- Quản trị viên (có mọi quyền theo V202 Q4); KHÔNG cấp cho HEAD_ACADEMIC.
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('academic.change-history.view-all', 'Xem lịch sử thay đổi của mọi nhân sự (không giới hạn phòng ban)', 'ACADEMIC', 'V206 — lịch sử thay đổi theo phòng ban')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('EXECUTIVE', 'SYS_ADMIN')
  AND p.code = 'academic.change-history.view-all'
ON CONFLICT (role_id, permission_id) DO NOTHING;
