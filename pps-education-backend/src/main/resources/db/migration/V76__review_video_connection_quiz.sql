-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: Video Kết nối (CONNECTION)
-- giờ bắt buộc có 2-5 câu hỏi trắc nghiệm tự chấm, hiện ra sau khi học sinh xem xong 1 lượt
-- (đạt completion_threshold_percent). "1 lượt hoàn thành" (tính vào required_view_count) = xem
-- đạt ngưỡng VÀ trả lời hết bộ câu hỏi CHO ĐÚNG lượt xem đó — khớp cặp 1-1 qua watch_session_id
-- (xem ReviewVideoService#submitConnectionAnswers/recomputeProgress). REFLEX không đổi gì.

CREATE TABLE review_video_connection_questions (
    id                BIGSERIAL PRIMARY KEY,
    review_video_id   BIGINT NOT NULL REFERENCES review_videos(id),
    prompt            TEXT NOT NULL,
    display_order     INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rv_connection_questions_video ON review_video_connection_questions(review_video_id);

CREATE TABLE review_video_connection_choices (
    id                                    BIGSERIAL PRIMARY KEY,
    review_video_connection_question_id   BIGINT NOT NULL REFERENCES review_video_connection_questions(id),
    choice_label                          VARCHAR(10) NOT NULL,
    content                               TEXT NOT NULL,
    is_correct                            BOOLEAN NOT NULL DEFAULT false,
    display_order                         INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_rv_connection_choices_question ON review_video_connection_choices(review_video_connection_question_id);

-- 1 dòng = 1 câu trả lời của học sinh cho 1 câu hỏi, gắn ĐÚNG 1 lượt xem (watch_session_id) —
-- UNIQUE (question, session) là chốt chặn khớp cặp 1-1, không cho trả lời trùng trong cùng lượt.
CREATE TABLE review_video_connection_answers (
    id                                    BIGSERIAL PRIMARY KEY,
    review_video_connection_question_id   BIGINT NOT NULL REFERENCES review_video_connection_questions(id),
    watch_session_id                      BIGINT NOT NULL REFERENCES review_video_watch_sessions(id),
    student_id                            BIGINT NOT NULL REFERENCES students(id),
    selected_choice_id                    BIGINT NOT NULL REFERENCES review_video_connection_choices(id),
    is_correct                            BOOLEAN NOT NULL,
    answered_at                           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at                            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_video_connection_question_id, watch_session_id)
);
CREATE INDEX idx_rv_connection_answers_session ON review_video_connection_answers(watch_session_id);

-- Mốc "đã trả lời đủ bộ câu hỏi cho lượt này" — dùng làm điều kiện đếm view_count của
-- review_video_progress thay vì chỉ is_qualified như trước (chỉ áp dụng videoType=CONNECTION).
ALTER TABLE review_video_watch_sessions ADD COLUMN quiz_completed_at TIMESTAMPTZ NULL;
