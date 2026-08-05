-- =====================================================================
-- V92: Cảnh báo theo lộ trình BTVN (bổ sung ngoài SDD gốc, đã xác nhận
-- với người dùng 2026-08-06). 3 luồng độc lập:
--   1) Thiếu bài tập liên tục/ngắt quãng -> nhắc nhở/cảnh báo học tập/
--      thư mời phụ huynh (student_homework_alert_state).
--   2) Nhắc phụ huynh trước hạn nộp ~12h (parent_reminder_sent_at, mirror
--      teacher_notified_at của V82).
--   3) Dừng bài khi vi phạm giám sát vượt ngưỡng (mở rộng V70).
-- Xem docs/uc/phan-he-06-hoc-thuat.md, docs/uc/phan-he-07-lms-portal.md.
-- =====================================================================

-- 1) Trạng thái đếm streak/tổng thiếu bài, tách riêng theo kênh (Ngữ
-- pháp/Video — 2 chuỗi giao bài độc lập trong hệ thống). academic_term_id
-- KHÔNG bắt buộc (site có thể chưa cấu hình kỳ cho khoảng ngày hiện tại)
-- -- NULL nghĩa là chưa xác định được kỳ, tạm gộp chung 1 nhóm (hiếm gặp,
-- chấp nhận UNIQUE không chặn được nhiều dòng NULL theo đúng ngữ nghĩa
-- NULL của Postgres).
CREATE TABLE student_homework_alert_state (
    id                        BIGSERIAL PRIMARY KEY,
    student_id                BIGINT NOT NULL REFERENCES students(id),
    school_class_id           BIGINT NOT NULL REFERENCES classes(id),
    channel                   VARCHAR(20) NOT NULL, -- GRAMMAR / VIDEO
    academic_term_id          BIGINT NULL REFERENCES academic_terms(id),
    consecutive_miss_count    INT NOT NULL DEFAULT 0,
    total_miss_count_in_term  INT NOT NULL DEFAULT 0,
    type2_alert_sent          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (student_id, school_class_id, channel, academic_term_id)
);
CREATE INDEX idx_homework_alert_state_student ON student_homework_alert_state(student_id);

-- 2) Nhắc trước hạn — mirror teacher_notified_at (V82), đánh dấu đã xử lý
-- xong 1 bản giao để job không quét lại (kể cả khi 0 học sinh cần nhắc).
ALTER TABLE exercise_assignments ADD COLUMN parent_reminder_sent_at TIMESTAMPTZ NULL;
ALTER TABLE review_video_assignments ADD COLUMN parent_reminder_sent_at TIMESTAMPTZ NULL;

-- 3) Đánh dấu lượt làm bài bị dừng ép do vi phạm giám sát (khác lượt tự
-- nộp bình thường) -- giáo viên lọc/nhận biết nhanh khi xem lịch sử nhiều
-- lượt, không cần join sang attempt_integrity_events mỗi lần hiển thị.
ALTER TABLE exercise_attempts ADD COLUMN stopped_by_integrity_violation BOOLEAN NOT NULL DEFAULT FALSE;

-- Cấu hình ngưỡng (category NOTIFICATION — cấu hình hành vi gửi thông
-- báo, mirror category SECURITY của integrity.* ở V70).
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('homework_alert.enabled', 'true', 'Bật/tắt cảnh báo thiếu BTVN theo lộ trình + nhắc trước hạn', 'NOTIFICATION'),
('homework_alert.reflex_pass_threshold_percent', '80', 'Ngưỡng % câu đã trả lời để tính "đạt" BTVN Video phản xạ (REFLEX)', 'NOTIFICATION'),
('homework_alert.reminder_before_due_hours', '12', 'Số giờ trước hạn nộp BTVN để nhắc phụ huynh', 'NOTIFICATION');
