-- =====================================================================
-- V104: Kenh SMS (Twilio) + PUSH (Firebase Cloud Messaging) cho module
-- Notification. SDD (nhom 9B) da de cho san SMS/PUSH trong enum
-- notification_deliveries.channel nhung chua co sender/nguon du lieu cu
-- the ("device token tai thoi diem gui" duoc snapshot vao
-- notification_deliveries.recipient_address, khong noi ro token hien tai
-- luu o dau). Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-08:
-- Push dung cho thong bao hang ngay (mac dinh bat cho moi user), Email cho
-- thong bao quan trong (khong doi), SMS rieng cho Phu huynh/Hoc sinh (mac
-- dinh bat cho 2 nhom nay khi chua co notification_preferences).
-- =====================================================================

-- device_tokens -- FCM device token hien tai cua user (PushNotificationSender
-- tra cuu luc gui). 1 user co the co nhieu thiet bi -> khong unique theo
-- user_id, unique theo token.
CREATE TABLE device_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    token       VARCHAR(500) NOT NULL UNIQUE,
    platform    VARCHAR(20) NOT NULL, -- ANDROID / IOS / WEB
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_device_tokens_user_active ON device_tokens(user_id, is_active);
