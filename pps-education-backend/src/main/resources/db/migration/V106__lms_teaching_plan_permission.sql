-- =====================================================================
-- V106: Bổ sung quyền cho UC-28 (Điền kế hoạch giảng dạy) — bổ sung ngoài
-- SDD gốc, đã xác nhận với người dùng 2026-08-08.
--
-- Trước đây TeachingPlanController KHÔNG có @PreAuthorize — chỉ dựa vào
-- TeachingPlanService.requireAssignedTeacher (ownership check: chỉ giáo
-- viên đang được phân công dạy lớp qua class_teachers mới thao tác được).
-- Hệ quả: quản trị viên (SUPER_ADMIN) không có cách nào hỗ trợ/khắc phục
-- kế hoạch giảng dạy của giáo viên khác dù cần thiết.
--
-- Theo đúng pattern 2 tầng đã dùng cho UC-15 điểm danh (V44/V45 —
-- academic.attendance.mark vs academic.attendance.create/update):
--   lms.teaching-plan.mark   — quyền thường, giáo viên vẫn phải là người
--                              được phân công dạy lớp (requireAssignedTeacher
--                              vẫn áp dụng trong Service).
--   lms.teaching-plan.manage — quyền quản trị, VƯỢT rào ownership (thao
--                              tác kế hoạch của lớp/giáo viên bất kỳ).
--
-- SUPER_ADMIN (role tạo tay ngoài migration ở DB dev) được cấp cả 2; nếu
-- môi trường không có role này, phép JOIN tự bỏ qua (no-op).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('lms.teaching-plan.mark',   'Điền kế hoạch giảng dạy',               'LMS', 'UC-28 — giáo viên được phân công dạy lớp'),
('lms.teaching-plan.manage', 'Quản trị kế hoạch giảng dạy (vượt rào)', 'LMS', 'UC-28 — quản trị viên, không cần được phân công dạy lớp');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.teaching-plan.mark' AND r.code IN ('TEACHER', 'SUPER_ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.teaching-plan.manage' AND r.code IN ('HEAD_ACADEMIC', 'SUPER_ADMIN');
