-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03 — ẩn tab "Ngân hàng câu hỏi" khỏi
-- sidebar (chức năng soạn/sửa câu hỏi đã chuyển hẳn vào trong "Soạn & giao đề", xem
-- CreateAndAssignExerciseModal.tsx/ExerciseAssignPage.tsx). Vào "Soạn & giao đề" cần quyền lms.exam.*
-- (V67, thêm SAU đợt cấp "full quyền" cho superadmin ở V62-thời-điểm-đó, 2026-07-30 — bị bỏ sót không
-- cấp kèm lms.exam.* nên superadmin hiện có đủ lms.question-bank.* nhưng KHÔNG vào được "Soạn & giao
-- đề"). Tự động cấp GRANT override lms.exam.create/update/assign cho MỌI tài khoản đã có ĐỦ 3 quyền
-- lms.question-bank.create/update/view dạng GRANT override — tránh mất khả năng quản lý câu hỏi sau khi
-- ẩn tab cũ. Không đụng role_permissions (TEACHER đã có sẵn đủ cả 2 nhóm quyền từ trước, không cần sửa).

INSERT INTO user_permission_overrides (user_id, permission_id, override_type, reason, granted_by, granted_at)
SELECT DISTINCT qb.user_id, ep.id, 'GRANT',
       'Tự động cấp kèm lms.question-bank.* — Soạn & giao đề giờ là nơi duy nhất soạn/sửa câu hỏi (V72)',
       qb.granted_by, now()
FROM user_permission_overrides qb
JOIN permissions qbp ON qbp.id = qb.permission_id AND qbp.code = 'lms.question-bank.create'
CROSS JOIN (SELECT id, code FROM permissions WHERE code IN ('lms.exam.create', 'lms.exam.update', 'lms.exam.assign')) ep
WHERE qb.override_type = 'GRANT'
  AND EXISTS (
      SELECT 1 FROM user_permission_overrides u2
      JOIN permissions p2 ON p2.id = u2.permission_id
      WHERE u2.user_id = qb.user_id AND p2.code = 'lms.question-bank.update' AND u2.override_type = 'GRANT'
  )
  AND NOT EXISTS (
      SELECT 1 FROM user_permission_overrides existing
      WHERE existing.user_id = qb.user_id AND existing.permission_id = ep.id
  );
