-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-22 — "họ" (family) của refresh token: mọi token sinh ra
-- từ cùng 1 lần đăng nhập (qua các lần refresh xoay vòng) mang chung family_id. Khi thiết bị mất phản hồi refresh,
-- server cấp lại token và để lại 1 token "mồ côi" cùng họ; đếm giới hạn thiết bị của Học sinh theo họ (AuthService#
-- enforceActiveSessionLimit) để token mồ côi không bị tính thành "thiết bị khác" (popup đăng nhập 409 oan).
-- NULL = token cũ trước migration (mỗi token tự là 1 họ, giữ nguyên hành vi cũ).
ALTER TABLE refresh_tokens ADD COLUMN family_id VARCHAR(36);
