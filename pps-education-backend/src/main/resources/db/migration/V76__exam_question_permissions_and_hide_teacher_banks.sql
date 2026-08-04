-- UC-40 (2026-08-04, đã xác nhận với người dùng): Giáo viên soạn/sửa/
-- import câu hỏi qua Đề, không còn quản lý Ngân hàng câu hỏi độc lập.
-- Ngân hàng legacy độc lập chỉ dành cho HEAD_ACADEMIC/SYS_ADMIN/SUPER_ADMIN.
INSERT INTO permissions (code, name, module, description) VALUES
('lms.exam-question.view',   'Xem câu hỏi nội bộ của Đề kèm đáp án', 'LMS', 'UC-40 — ngân hàng câu hỏi ngầm theo Đề'),
('lms.exam-question.create', 'Soạn/import câu hỏi vào Đề',           'LMS', 'UC-40 — ngân hàng câu hỏi ngầm theo Đề'),
('lms.exam-question.update', 'Sửa/archive câu hỏi của Đề',           'LMS', 'UC-40 — ngân hàng câu hỏi ngầm theo Đề');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('TEACHER', 'HEAD_ACADEMIC', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND p.code IN ('lms.exam-question.view', 'lms.exam-question.create', 'lms.exam-question.update')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Bảo đảm 3 vai trò quản lý có đủ quyền generic legacy.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('HEAD_ACADEMIC', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND p.code IN ('lms.question-bank.view', 'lms.question-bank.create', 'lms.question-bank.update')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Giáo viên không còn quyền quản lý bank độc lập; API theo Exam dùng
-- lms.exam-question.* ở trên.
DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.code = 'TEACHER'
  AND p.code IN ('lms.question-bank.view', 'lms.question-bank.create', 'lms.question-bank.update');
