-- =====================================================================
-- V45: Quyền quản trị chi tiết cho điểm danh học sinh (UC-15) — bổ sung
-- ngoài SDD gốc, đã xác nhận với người dùng 2026-07-22. Cập nhật lại
-- docs/uc/phan-he-05-hoc-sinh.md (ghi chú dưới UC-15).
--
-- Bối cảnh: luồng đúng của điểm danh nhanh là Giáo viên CHỈ điểm danh/sửa
-- buổi MÌNH được phân công dạy và CHỈ trong ngày diễn ra buổi học (sửa
-- được tới hết ngày hôm đó — thực thi ở StudentAttendanceService
-- .requireCanWriteAttendance, dùng quyền hiện có academic.attendance.mark).
--
-- Bổ sung 3 quyền QUẢN TRỊ cho phép 1 tài khoản thao tác điểm danh VƯỢT 2
-- ràng buộc trên (buổi bất kỳ, ngày bất kỳ) để bổ sung/khắc phục sai sót:
--   academic.attendance.create — tạo/điểm danh (POST + submit) buổi bất kỳ
--   academic.attendance.update — sửa chi tiết theo tiết buổi bất kỳ
--   academic.attendance.delete — xóa toàn bộ bản ghi điểm danh của 1 buổi
--
-- Chỉ cấp sẵn cho SUPER_ADMIN (role tạo tay ở DB dev — nếu môi trường
-- không có, phép JOIN tự bỏ qua, no-op). Các tài khoản khác do người dùng
-- tự gán qua UC-03/04 (role_permissions / user_permission_overrides).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('academic.attendance.create', 'Tạo/điểm danh buổi bất kỳ (quản trị)', 'ACADEMIC', 'UC-15 — vượt rào GV (buổi/ngày bất kỳ)'),
('academic.attendance.update', 'Sửa điểm danh buổi bất kỳ (quản trị)',  'ACADEMIC', 'UC-15 — vượt rào GV (buổi/ngày bất kỳ)'),
('academic.attendance.delete', 'Xóa bản ghi điểm danh (quản trị)',       'ACADEMIC', 'UC-15 — xóa điểm danh 1 buổi');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN ('academic.attendance.create', 'academic.attendance.update', 'academic.attendance.delete')
  AND r.code = 'SUPER_ADMIN';
