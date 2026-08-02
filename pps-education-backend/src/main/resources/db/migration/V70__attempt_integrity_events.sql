-- =====================================================================
-- V70: Giám sát thoát màn hình khi làm bài (bổ sung ngoài SDD gốc, đã
-- xác nhận với người dùng 2026-07-31). Xem docs/uc/phan-he-07-lms-portal.md
-- (blockquote "Bổ sung" ở UC-24/UC-27/UC-23b).
--
-- attempt_type/attempt_id là khóa đa hình (trỏ tới exercise_attempts
-- hoặc review_video_question_submissions tùy attempt_type) — không FK
-- DB thật vì trỏ 2 bảng khác nhau, validate ở tầng service. Không CHECK
-- constraint trên attempt_type/event_type (VARCHAR tự do) — mirror đúng
-- quy ước notification_type/import_type đã có, thêm giá trị enum mới
-- (VD LISTENING_PRACTICE) sau này không cần migration.
-- =====================================================================

CREATE TABLE attempt_integrity_events (
    id                 BIGSERIAL PRIMARY KEY,
    attempt_type       VARCHAR(30) NOT NULL, -- EXERCISE / REVIEW_VIDEO_QUESTION
    attempt_id         BIGINT NOT NULL,
    student_id         BIGINT NOT NULL REFERENCES students(id),
    school_class_id    BIGINT NULL REFERENCES classes(id),
    event_type         VARCHAR(30) NOT NULL, -- OUT_OF_FOCUS / FULLSCREEN_EXITED
    started_at         TIMESTAMPTZ NOT NULL,
    ended_at           TIMESTAMPTZ NOT NULL,
    duration_seconds   INT NOT NULL,
    client_reported_at TIMESTAMPTZ NOT NULL,
    user_agent         VARCHAR(500) NULL,
    notified_at        TIMESTAMPTZ NULL, -- set trên đúng 1 dòng đầu tiên vượt ngưỡng cho (attempt_type, attempt_id)
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attempt_integrity_events_attempt ON attempt_integrity_events(attempt_type, attempt_id);
CREATE INDEX idx_attempt_integrity_events_student ON attempt_integrity_events(student_id);
CREATE INDEX idx_attempt_integrity_events_class ON attempt_integrity_events(school_class_id);

-- Cấu hình ngưỡng (category SECURITY — đã có sẵn trong danh sách category
-- dự kiến ở comment cột system_settings.category, V1__foundation_core.sql).
-- Giá trị mặc định đã xác nhận với người dùng 2026-07-31.
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('integrity.monitoring_enabled', 'true', 'Bật/tắt giám sát thoát màn hình khi làm bài', 'SECURITY'),
('integrity.min_violation_duration_seconds', '3', 'Ngưỡng thời lượng tối thiểu (giây) để tính là 1 lần vi phạm', 'SECURITY'),
('integrity.notify_violation_count_threshold', '3', 'Số lần vi phạm tối thiểu để báo phụ huynh/giáo viên', 'SECURITY'),
('integrity.notify_cumulative_duration_seconds_threshold', '60', 'Tổng thời lượng vi phạm (giây) tối thiểu để báo phụ huynh/giáo viên', 'SECURITY');
