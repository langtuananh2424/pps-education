-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-22 — nới ngưỡng dừng bài khi rời trang (Bài lẻ) vì học sinh
-- bị tự dừng/chấm oan khi chuyển app ngắn (tin nhắn, từ điển): lần rời tối thiểu 3 -> 5 giây, số lần 3 -> 5, tổng thời
-- lượng 60 -> 120 giây. Chỉ đổi nếu giá trị vẫn là mặc định V70 (không ghi đè chỉnh tay của quản trị viên).
UPDATE system_settings SET setting_value = '5'
 WHERE setting_key = 'integrity.min_violation_duration_seconds' AND setting_value = '3';
UPDATE system_settings SET setting_value = '5'
 WHERE setting_key = 'integrity.notify_violation_count_threshold' AND setting_value = '3';
UPDATE system_settings SET setting_value = '120'
 WHERE setting_key = 'integrity.notify_cumulative_duration_seconds_threshold' AND setting_value = '60';
