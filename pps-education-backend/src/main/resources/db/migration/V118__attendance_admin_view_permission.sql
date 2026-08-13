-- =====================================================================
-- V118: Quyền xem tổng hợp chấm công toàn trung tâm — bổ sung ngoài UC-09
-- gốc, đã xác nhận với người dùng 2026-08-12. Cập nhật lại
-- docs/uc/phan-he-04-nhan-su.md (ghi chú dưới UC-09).
--
-- Bối cảnh: UC-09 gốc chỉ đặc tả chấm công tự phục vụ (AttendanceController
-- không có endpoint list). Bổ sung 1 endpoint admin GET /api/attendance/records
-- để HR/Điều hành xem tổng hợp log chấm công theo khoảng ngày/nhân sự/điểm
-- trường — gate bằng quyền mới dưới đây, mirror đúng nhóm role hiện có
-- hrm.employee.view (V51__consolidate_bundled_permissions.sql).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('hrm.attendance.view-all', 'Xem tổng hợp chấm công toàn trung tâm', 'HRM', 'Bổ sung ngoài UC-09 gốc, xác nhận 2026-08-12');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'hrm.attendance.view-all'
  AND r.code IN ('HR_MANAGER', 'EXECUTIVE', 'SUPER_ADMIN', 'SYS_ADMIN');
