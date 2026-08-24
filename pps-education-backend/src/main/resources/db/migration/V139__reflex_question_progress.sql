-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — UC-23b (Video phản xạ) V2: đổi từ
-- luồng "xem 1 video liên tục, ghi âm mỗi câu theo mốc thời gian, nộp cả loạt cuối video, GV chấm tay"
-- sang luồng tuần tự theo TỪNG câu hỏi: viết trước (AI chấm ngữ pháp) -> đạt -> mới mở khoá ghi âm ->
-- AI chấm nội dung -> đạt -> mở câu tiếp theo. Bảng MỚI, KHÔNG dùng lại review_video_question_submissions
-- (audio_url ở đó NOT NULL — không hợp với việc "đã có câu trả lời viết nhưng chưa ghi âm"; giữ nguyên
-- bảng cũ cho lịch sử/luồng chấm tay cũ, không đụng vào).
--
-- 1 dòng/(câu hỏi, học sinh, lần giao) — SỬA ĐÈ tại chỗ mỗi lần thử lại (không giữ lịch sử từng lần thử
-- như bảng cũ) vì "không giới hạn số lần thử lại, chỉ cần lưu tiến trình dở" (đã xác nhận với người dùng)
-- — *_attempt_count chỉ để hiển thị thống kê, không phải rào chặn.
CREATE TABLE reflex_question_progress (
    id BIGSERIAL PRIMARY KEY,
    review_video_question_id BIGINT NOT NULL REFERENCES review_video_questions(id),
    student_id BIGINT NOT NULL REFERENCES students(id),
    review_video_assignment_id BIGINT NOT NULL REFERENCES review_video_assignments(id),

    answer_text TEXT,
    writing_score DECIMAL(5,2),
    writing_max_score DECIMAL(5,2),
    writing_feedback TEXT,
    writing_graded_at TIMESTAMPTZ,
    writing_attempt_count INT NOT NULL DEFAULT 0,

    audio_url VARCHAR(1000),
    speaking_score DECIMAL(5,2),
    speaking_max_score DECIMAL(5,2),
    speaking_feedback TEXT,
    speaking_graded_at TIMESTAMPTZ,
    speaking_attempt_count INT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(review_video_question_id, student_id, review_video_assignment_id)
);

CREATE INDEX idx_reflex_question_progress_assignment_student
    ON reflex_question_progress(review_video_assignment_id, student_id);

COMMENT ON TABLE reflex_question_progress IS 'UC-23b V2 (bổ sung ngoài SDD gốc, 2026-08-22) - tiến trình tuần tự viết->AI chấm ngữ pháp->đạt->ghi âm->AI chấm nội dung->đạt->mở câu tiếp theo. Xem ReflexSequentialGradingService.';
