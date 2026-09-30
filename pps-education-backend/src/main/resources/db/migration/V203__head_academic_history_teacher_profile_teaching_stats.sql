-- =====================================================================
-- V203: Bổ sung chức năng cho Trưởng phòng đào tạo (HEAD_ACADEMIC).
--
-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30 (đối chiếu
-- danh sách 10 chức năng Trưởng PĐT cần có với hệ thống hiện tại):
--   1. academic.change-history.view — xem lịch sử thay đổi dữ liệu lớp,
--      buổi học (lịch học), ghi danh, giáo viên phụ trách lớp, học sinh,
--      hồ sơ giáo viên. Đọc từ các bảng *_history đã ghi sẵn từ trước
--      (classes_history, class_sessions_history, class_enrollments_history,
--      class_teachers_history, students_history, employees_history) —
--      không thêm bảng mới.
--   2. hrm.teacher.view — xem hồ sơ GIÁO VIÊN (chỉ nhân sự employee_type =
--      TEACHER), KHÔNG gồm CCCD, tài khoản ngân hàng, mã số thuế, BHXH, hợp
--      đồng/lương. Tách khỏi hrm.employee.view (xem toàn bộ hồ sơ nhân sự)
--      để không phải cấp quyền HR đầy đủ cho Trưởng PĐT.
--   3. report.teacher-stats.view — thống kê giảng dạy theo giáo viên (số
--      buổi, số tiết thực tế, nhận lớp đúng giờ/trễ/không nhận lớp, buổi
--      bị huỷ) và xuất Excel.
--
-- Cấp mặc định cho HEAD_ACADEMIC; SYS_ADMIN + EXECUTIVE có mọi quyền (V202
-- Q4) nên cấp luôn. Vai trò khác tick thêm trên màn "Nhóm vai trò".
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('academic.change-history.view', 'Xem lịch sử thay đổi dữ liệu lớp, học sinh, giáo viên, lịch học', 'ACADEMIC', 'V203 — chức năng Trưởng phòng đào tạo'),
('hrm.teacher.view', 'Xem hồ sơ giáo viên (không gồm CCCD, ngân hàng, hợp đồng, lương)', 'HRM', 'V203 — chức năng Trưởng phòng đào tạo'),
('report.teacher-stats.view', 'Xem, xuất thống kê giảng dạy theo giáo viên', 'ACADEMIC', 'V203 — chức năng Trưởng phòng đào tạo')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('HEAD_ACADEMIC', 'EXECUTIVE', 'SYS_ADMIN')
  AND p.code IN ('academic.change-history.view', 'hrm.teacher.view', 'report.teacher-stats.view')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Trang "Lịch sử thay đổi dữ liệu" sắp xếp/lọc theo thời gian trên cả 6 bảng
-- lịch sử — trước đây chỉ có index theo khoá đối tượng (class_id, student_id...).
CREATE INDEX IF NOT EXISTS idx_classes_history_created_at ON classes_history(created_at);
CREATE INDEX IF NOT EXISTS idx_class_sessions_history_created_at ON class_sessions_history(created_at);
CREATE INDEX IF NOT EXISTS idx_class_enrollments_history_created_at ON class_enrollments_history(created_at);
CREATE INDEX IF NOT EXISTS idx_class_teachers_history_created_at ON class_teachers_history(created_at);
CREATE INDEX IF NOT EXISTS idx_students_history_created_at ON students_history(created_at);
CREATE INDEX IF NOT EXISTS idx_employees_history_created_at ON employees_history(created_at);
