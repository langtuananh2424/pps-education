-- Bổ sung ngoài SDD gốc (UC-23b Video phản xạ), đã xác nhận với người dùng 2026-09-21:
-- reflex_question_progress ghi đè tại chỗ mỗi lần học sinh nộp lại (không giữ lịch sử), nên giáo viên
-- không thể nghe lại audio/xem kết quả AI chấm của các lần TRƯỚC lần gần nhất. Bảng này lưu 1 dòng SNAPSHOT
-- mỗi khi AI chấm xong (viết HOẶC ghi âm), KHÔNG sửa đè — phục vụ giáo viên xem lịch sử từng lần làm và
-- xuất toàn bộ dữ liệu audio + kết quả AI chấm để tiếp tục train AI. Xem ReflexSequentialGradingService.
CREATE TABLE reflex_question_progress_history (
    id BIGSERIAL PRIMARY KEY,
    reflex_question_progress_id BIGINT NOT NULL REFERENCES reflex_question_progress(id),
    review_video_question_id BIGINT NOT NULL REFERENCES review_video_questions(id),
    student_id BIGINT NOT NULL REFERENCES students(id),
    review_video_assignment_id BIGINT NOT NULL REFERENCES review_video_assignments(id),
    attempt_type VARCHAR(20) NOT NULL CHECK (attempt_type IN ('WRITING', 'SPEAKING')),
    attempt_number INT NOT NULL,
    answer_text TEXT,
    audio_url VARCHAR(1000),
    score DECIMAL(5,2),
    max_score DECIMAL(5,2),
    feedback TEXT,
    marked_answer TEXT,
    transcript TEXT,
    criteria_scores JSONB,
    graded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reflex_question_progress_history_assignment_student
    ON reflex_question_progress_history (review_video_assignment_id, student_id);

CREATE INDEX idx_reflex_question_progress_history_progress
    ON reflex_question_progress_history (reflex_question_progress_id);
