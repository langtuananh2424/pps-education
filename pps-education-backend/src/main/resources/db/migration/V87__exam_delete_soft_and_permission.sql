-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: đủ CRUD cho Kho đề — "Xóa Đề"
-- (soft-delete Exam qua deleted_at, cùng pattern PartnerContract/SchoolClass đã dùng — không xóa cứng
-- vì exercises/exercise_assignments/exercise_attempts/student_answers có thể đã tham chiếu qua Bài
-- thuộc Đề). "Xóa Bài" (Exercise) tái dùng status=ARCHIVED đã có sẵn từ đầu (không cần cột/permission
-- mới, chỉ chưa từng có đường gọi tới) — không cần migration riêng cho phần đó.

ALTER TABLE exams ADD COLUMN deleted_at TIMESTAMPTZ NULL;

INSERT INTO permissions (code, name, module, description) VALUES
('lms.exam.delete', 'Xóa Đề (Kho đề)', 'LMS', 'UC-40');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.exam.delete' AND r.code = 'TEACHER';

-- Mirror V72: cấp GRANT override cho user đã có lms.exam.create dạng GRANT override (VD superadmin,
-- vốn không có bypass tự động — xem PpsPermissionEvaluator/V72), tránh mất khả năng "Xóa Đề" ngay
-- sau khi thêm quyền mới.
INSERT INTO user_permission_overrides (user_id, permission_id, override_type, reason, granted_by, granted_at)
SELECT DISTINCT eo.user_id, dp.id, 'GRANT',
       'Tự động cấp kèm lms.exam.create — bổ sung Xóa Đề (V87)',
       eo.granted_by, now()
FROM user_permission_overrides eo
JOIN permissions ep ON ep.id = eo.permission_id AND ep.code = 'lms.exam.create'
CROSS JOIN (SELECT id FROM permissions WHERE code = 'lms.exam.delete') dp
WHERE eo.override_type = 'GRANT'
  AND NOT EXISTS (
      SELECT 1 FROM user_permission_overrides existing
      WHERE existing.user_id = eo.user_id AND existing.permission_id = dp.id
  );
