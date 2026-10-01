-- ============================================================================
-- V195: Khung giờ yên lặng cho nhắc hạn BTVN (HOMEWORK_DUE_SOON_REMINDER).
--
-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-25: nhắc hạn BTVN
-- mặc định gửi trước hạn nộp homework_alert.reminder_before_due_hours (12)
-- tiếng — VD hạn 12:00 trưa thì nhắc lúc 0:00 đêm, phụ huynh dễ bỏ lỡ và
-- gây khó chịu. Quy tắc mới: nếu mốc nhắc rơi vào khung đêm [21:00, 07:00)
-- thì gửi SỚM HƠN, lúc 21:00 tối ngay trước đó. Giao bài trong khung đêm mà
-- mốc nhắc đã qua thì vẫn gửi ngay (hạn đã gần). Chỉ áp dụng cho nhắc hạn
-- BTVN, không áp dụng cho các loại thông báo khác.
--
-- Lưu theo giờ nguyên (0-23, giờ Việt Nam) cho đồng bộ kiểu số với
-- reminder_before_due_hours ở V92. start = end nghĩa là tắt khung yên lặng.
-- ============================================================================
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('homework_alert.reminder_quiet_start_hour', '21', 'Giờ bắt đầu khung đêm không gửi nhắc hạn BTVN (0-23, giờ VN) — mốc nhắc rơi vào khung này sẽ gửi sớm lúc giờ bắt đầu', 'NOTIFICATION'),
('homework_alert.reminder_quiet_end_hour', '7', 'Giờ kết thúc khung đêm không gửi nhắc hạn BTVN (0-23, giờ VN); bằng giờ bắt đầu = tắt khung đêm', 'NOTIFICATION');
