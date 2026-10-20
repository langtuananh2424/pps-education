-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-19 — "Chấm lần 2": giáo viên xem lại điểm
-- AI chấm (Video phản xạ bước Viết / bước Nói) và ghi đè điểm chính thức. reflex_question_progress ghi đè
-- tại chỗ nên điểm GV được ghi thẳng vào writing_score / speaking_score (mọi chỗ đọc điểm — ngưỡng đạt,
-- báo cáo, tiến độ BTVN — tự dùng điểm GV, không phải sửa); điểm + nhận xét gốc của AI được giữ lại ở
-- các cột *_ai_* để đối chiếu. Cột *_reviewed_at IS NOT NULL = bước này đã được GV chấm lại; học sinh
-- nộp lại bước đó thì AI chấm mới và các cột này được xoá (xem ReflexSequentialGradingService).
-- Bài tập Writing (ESSAY) KHÔNG cần cột mới: student_answer_grading đã có grading_source AI/HUMAN (V138)
-- và giữ lịch sử bản chấm cũ (is_final=false).
ALTER TABLE reflex_question_progress
    ADD COLUMN writing_ai_score        NUMERIC(5, 2) NULL,
    ADD COLUMN writing_ai_feedback     TEXT          NULL,
    ADD COLUMN writing_reviewed_by     BIGINT        NULL REFERENCES users (id),
    ADD COLUMN writing_reviewed_at     TIMESTAMPTZ   NULL,
    ADD COLUMN speaking_ai_score       NUMERIC(5, 2) NULL,
    ADD COLUMN speaking_ai_feedback    TEXT          NULL,
    ADD COLUMN speaking_reviewed_by    BIGINT        NULL REFERENCES users (id),
    ADD COLUMN speaking_reviewed_at    TIMESTAMPTZ   NULL;

COMMENT ON COLUMN reflex_question_progress.writing_ai_score IS 'Điểm AI chấm bước Viết trước khi GV chấm lại (V216); NULL = chưa được chấm lại';
COMMENT ON COLUMN reflex_question_progress.speaking_ai_score IS 'Điểm AI chấm bước Nói trước khi GV chấm lại (V216); NULL = chưa được chấm lại';
