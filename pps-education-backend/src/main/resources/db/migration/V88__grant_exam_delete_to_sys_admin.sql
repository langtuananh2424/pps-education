-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: V87 chỉ gán lms.exam.delete cho
-- TEACHER (mirror V67) + cấp override cho user có lms.exam.create dạng override cá nhân — bỏ sót
-- role SYS_ADMIN đã có sẵn lms.exam.create/update/assign qua role_permissions trực tiếp (không phải
-- qua override), nên sysadmin không tự động có lms.exam.delete. Cấp bổ sung cho MỌI role đã có
-- lms.exam.create qua role_permissions (không riêng SYS_ADMIN, tổng quát hơn — phòng trường hợp role
-- khác cũng được gán trực tiếp sau này).

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, dp.id
FROM role_permissions rp
JOIN permissions cp ON cp.id = rp.permission_id AND cp.code = 'lms.exam.create'
CROSS JOIN (SELECT id FROM permissions WHERE code = 'lms.exam.delete') dp
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = rp.role_id AND existing.permission_id = dp.id
);
