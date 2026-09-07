-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-07: dedupe device_tokens
-- theo TUNG THIET BI VAT LY (device_id sinh + luu localStorage phia client) thay vi
-- theo platform - 2 may cung he dieu hanh (VD 2 dien thoai Android) van nhan push
-- song song, khong gianh nhau 1 "suat" nhu cach dedupe theo platform.
-- Nullable vi token cu (dang ky truoc migration nay) chua co device_id - khong tham
-- gia dedupe cho toi khi client dang nhap lai (xem NotificationService.registerDeviceToken).
ALTER TABLE device_tokens
    ADD COLUMN device_id VARCHAR(100) NULL;

CREATE INDEX idx_device_tokens_user_device ON device_tokens(user_id, device_id) WHERE device_id IS NOT NULL;
