-- =====================================================================
-- V29: Permission cho UC-46 (FR-PER-05) — Gán/Thu hồi vai trò cho tài
-- khoản (bảng user_roles). Tách riêng với permission.role.manage (UC-03
-- — cấu hình quyền MẶC ĐỊNH của 1 role) vì 2 khái niệm khác nhau: một
-- bên định nghĩa role CÓ quyền gì, một bên liên kết TÀI KHOẢN nào mang
-- role gì. Theo đúng quy ước V4/V28: mỗi FR-PER-0x có permission code
-- riêng dù tập role hiện tại giống nhau (chỉ SYS_ADMIN).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('user.role.manage', 'Gán/thu hồi vai trò cho tài khoản', 'USER', 'FR-PER-05');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'user.role.manage' AND r.code = 'SYS_ADMIN';
