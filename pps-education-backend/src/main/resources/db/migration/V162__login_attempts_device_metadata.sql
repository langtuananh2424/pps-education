-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-05: ghi them
-- metadata thiet bi (client tu gui trong LoginRequest/GoogleLoginRequest)
-- de hien thi lich su dang nhap o man Quan ly nguoi dung -> Xem/Sua (UC-44).
-- Nullable vi client cu/khong ho tro van phai dang nhap duoc binh thuong.
ALTER TABLE login_attempts
    ADD COLUMN screen_resolution VARCHAR(20) NULL,
    ADD COLUMN browser_language  VARCHAR(20) NULL,
    ADD COLUMN timezone          VARCHAR(100) NULL;
