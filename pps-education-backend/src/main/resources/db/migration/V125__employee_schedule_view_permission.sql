-- =====================================================================
-- V125: Quyền xem lịch làm việc tổng hợp của nhân sự khác (ca làm cố
-- định + lịch dạy) — bổ sung ngoài UC-58/UC-70 gốc, đã xác nhận với
-- người dùng 2026-08-17. Cập nhật lại docs/uc/phan-he-04-nhan-su.md
-- (ghi chú dưới UC-70).
--
-- Bối cảnh: UC-58 (phan-he-06-hoc-thuat.md) chỉ đặc tả "Lịch của tôi"
-- tự phục vụ cho giáo viên (TeacherScheduleController chỉ xem lịch chính
-- mình). UC-70 (phan-he-04-nhan-su.md) chỉ đặc tả HR/Điều hành quản trị
-- ca làm việc, chưa có endpoint xem gộp lịch của 1 nhân viên bất kỳ. Bổ
-- sung endpoint GET /api/employees/{id}/teaching-sessions và gate lại
-- GET /api/employees/{id}/shifts để HR/Điều hành xem lịch làm việc tổng
-- hợp (ca làm + lịch dạy) của nhân viên khác — gate bằng quyền mới dưới
-- đây, mirror đúng nhóm role hiện có hrm.attendance.view-all
-- (V118__attendance_admin_view_permission.sql).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('hrm.employee-schedule.view', 'Xem lịch làm việc tổng hợp của nhân sự khác (ca làm + lịch dạy)', 'HRM', 'Bổ sung ngoài UC-58/UC-70 gốc, xác nhận 2026-08-17');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'hrm.employee-schedule.view'
  AND r.code IN ('HR_MANAGER', 'EXECUTIVE', 'SUPER_ADMIN', 'SYS_ADMIN');
