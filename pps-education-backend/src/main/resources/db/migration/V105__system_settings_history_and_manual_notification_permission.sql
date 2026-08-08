-- =====================================================================
-- V105: Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-08.
--
-- 1) system_settings_history -- SDD (02-nen-tang.md, muc k) da nhac "Co
-- bang system_settings_history" nhung chua tung duoc build. Can cho man
-- hinh Cai dat he thong moi (Quan tri vien sua gia tri qua UI) de biet ai
-- doi gi luc nao -- nhieu setting nhay cam (security.brute_force.*,
-- finance.bank_*).
--
-- 2) 2 permission moi:
--    - system.settings.manage: sua gia tri system_settings qua UI (chi
--      Quan tri vien he thong, theo xac nhan cua nguoi dung -- KHONG mo
--      rong cho Quan ly van hanh).
--    - notification.send.manual: cong cu gui thong bao thu cong toi user
--      duoc chon (muc dich test kenh Push/Email/SMS moi build).
-- =====================================================================

CREATE TABLE system_settings_history (
    id                  BIGSERIAL PRIMARY KEY,
    system_setting_id  BIGINT NOT NULL REFERENCES system_settings(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    old_value            JSONB NOT NULL,
    new_value            JSONB NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_system_settings_history_setting ON system_settings_history(system_setting_id, created_at DESC);

INSERT INTO permissions (code, name, module, description) VALUES
('system.settings.manage',  'Quản lý cấu hình hệ thống', 'SYSTEM', 'Sửa giá trị system_settings qua UI (Cài đặt hệ thống)'),
('notification.send.manual', 'Gửi thông báo thủ công',    'SYSTEM', 'Công cụ gửi thông báo tới user được chọn (test kênh Push/Email/SMS)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN ('system.settings.manage', 'notification.send.manual') AND r.code = 'SYS_ADMIN';
