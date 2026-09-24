-- =====================================================================
-- V184: Cảnh báo giáo viên chưa nhận lớp / không nhận lớp (UC-71 mở rộng,
-- bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21).
--   1) LATE  : tới giờ bắt đầu buổi học (+ late_after_minutes) vẫn chưa
--              có bản ghi class_session_check_ins -> PUSH tới Quản lý điểm
--              trường của site lớp đó + EMAIL tới giáo viên dạy buổi.
--   2) ABSENT: đã qua giờ kết thúc buổi học mà vẫn chưa nhận lớp -> tương
--              tự (1) với nội dung "không nhận lớp".
-- 2 mốc đánh dấu đã gửi lưu ngay trên class_sessions (mirror
-- parent_reminder_sent_at của V92) để job quét 1 phút/lần không gửi lặp.
-- Xem docs/uc/phan-he-06-hoc-thuat.md (UC-71), ClassCheckInAlertSchedulerService.
-- =====================================================================

ALTER TABLE class_sessions ADD COLUMN checkin_late_alert_sent_at   TIMESTAMPTZ NULL;
ALTER TABLE class_sessions ADD COLUMN checkin_absent_alert_sent_at TIMESTAMPTZ NULL;

-- Backfill: buổi học đã qua trước thời điểm deploy KHÔNG được cảnh báo lại
-- (tránh dội hàng loạt email/push cho lịch sử cũ khi job chạy lần đầu).
UPDATE class_sessions
SET checkin_late_alert_sent_at   = now(),
    checkin_absent_alert_sent_at = now()
WHERE session_date < CURRENT_DATE
   OR (session_date = CURRENT_DATE AND end_time <= LOCALTIME);

CREATE INDEX idx_class_sessions_checkin_alert_pending
    ON class_sessions (session_date)
    WHERE checkin_late_alert_sent_at IS NULL OR checkin_absent_alert_sent_at IS NULL;

-- Cấu hình bật/tắt (category NOTIFICATION, mirror homework_alert.* ở V92).
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('class_checkin_alert.enabled', 'true', 'Bật/tắt cảnh báo giáo viên chưa nhận lớp (push tới Quản lý điểm trường + email tới giáo viên) khi tới giờ học / hết giờ học mà chưa nhận lớp', 'NOTIFICATION'),
('class_checkin_alert.late_after_minutes', '5', 'Số phút sau giờ bắt đầu buổi học mới coi là "chưa nhận lớp" để gửi cảnh báo (0 = gửi ngay khi tới giờ)', 'NOTIFICATION');
