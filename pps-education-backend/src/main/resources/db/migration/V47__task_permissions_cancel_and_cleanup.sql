-- =====================================================================
-- V47: Phân quyền chi tiết cho Quản lý công việc (UC-06/07) + luồng hủy
-- (CANCELLED thay vì xóa trực tiếp) + dọn task CANCELLED sau X ngày. Bổ
-- sung ngoài SDD gốc — đã xác nhận với người dùng 2026-07-22. Cập nhật lại
-- docs/uc/phan-he-03-cong-viec.md.
--
-- Mô hình phạm vi giao việc (đã chốt với người dùng):
--   - Trưởng phòng = departments.head_user_id -> chỉ giao cho nhân sự
--     thuộc phòng mình làm trưởng.
--   - "Company-wide" (giao cho bất kỳ ai, kể cả trưởng phòng) = có quyền
--     task.manage HOẶC role EXECUTIVE (Ban giám đốc).
-- =====================================================================

-- (1) 3 quyền chi tiết
INSERT INTO permissions (code, name, module, description) VALUES
('task.assign',  'Giao việc',                 'TASK', 'UC-06 (thay task.create) — giao/giao lại/hủy công việc trong phạm vi'),
('task.receive', 'Nhận việc',                 'TASK', 'UC-07 — xem & cập nhật trạng thái việc được giao'),
('task.manage',  'Quản trị công việc (cao nhất)', 'TASK', 'UC-06/07 — sửa/xóa/hủy bất kỳ, giao toàn công ty, cấu hình dọn CANCELLED'),
('task.overview.company', 'Xem toàn bộ công việc công ty', 'TASK', 'UC-06/07 — GET /api/tasks/overview tầng company-wide');

-- task.assign: đúng tập role đang có task.create + SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'task.assign'
  AND r.code IN ('SITE_MANAGER', 'HEAD_ACADEMIC', 'OPS_MANAGER', 'HR_MANAGER', 'EXECUTIVE', 'SUPER_ADMIN');

-- task.receive: mọi vai trò nhân sự có thể là người nhận việc
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'task.receive'
  AND r.code IN ('TEACHER', 'STAFF', 'SITE_MANAGER', 'HEAD_ACADEMIC', 'OPS_MANAGER', 'HR_MANAGER', 'EXECUTIVE', 'SUPER_ADMIN');

-- task.manage: cấp quản lý cao nhất (ban quản lý/giám đốc) + admin; role khác tự gán sau
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'task.manage'
  AND r.code IN ('OPS_MANAGER', 'HR_MANAGER', 'EXECUTIVE', 'SUPER_ADMIN');

-- task.overview.company: xem toàn bộ công việc công ty (GET /api/tasks/overview tầng company-wide)
-- = EXECUTIVE (Ban giám đốc) + đúng tập role đang có task.manage.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'task.overview.company'
  AND r.code IN ('OPS_MANAGER', 'HR_MANAGER', 'EXECUTIVE', 'SUPER_ADMIN');

-- (2) Gỡ quyền gộp cũ task.create (cutover dev — chỉ dùng ở TaskController @PreAuthorize, không gate ở FE)
DELETE FROM user_permission_overrides WHERE permission_id = (SELECT id FROM permissions WHERE code = 'task.create');
DELETE FROM role_permissions          WHERE permission_id = (SELECT id FROM permissions WHERE code = 'task.create');
DELETE FROM permissions               WHERE code = 'task.create';

-- (3) Luồng hủy: cột đánh dấu thời điểm chuyển CANCELLED (phục vụ dọn sau X ngày)
ALTER TABLE tasks ADD COLUMN cancelled_at TIMESTAMPTZ NULL;

-- (4) Thiết lập hệ thống: số ngày giữ task CANCELLED trước khi xóa cứng (mặc định 7)
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('task.cancelled_retention_days', '7', 'Số ngày giữ task CANCELLED trước khi cron nightly xóa cứng (UC-06/07)', 'TASK');
