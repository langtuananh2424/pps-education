-- =====================================================================
-- V94: bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06.
--
-- Gợi ý tapescript cho câu hỏi Nghe (GV nước ngoài, skill=LISTENING) sau
-- khi học sinh nghe HẾT audio đủ số lần cấu hình (mặc định 3 lần, đếm
-- theo sự kiện audio phát tới cuối — xem TakeExerciseModal.tsx) — trước
-- đây referencePassage (transcript) bị trả không điều kiện cho học sinh
-- ngay từ đầu qua GET /api/exercises/{id}/questions (lộ đáp án qua
-- DevTools/Network), giờ chỉ lộ qua endpoint listening-hint khi đã mở
-- khóa — xem ExerciseService#toResponse(ExerciseQuestion) + Javadoc
-- ListeningHintService.
--
-- listening_play_progress.listening_key: nhóm "1 audio nhiều câu" dùng
-- CHUNG bộ đếm — server tự suy từ questionId client gửi (groupKey nếu có,
-- không thì "Q"+questionId) — xem Question.groupKey (V85).
-- =====================================================================

CREATE TABLE listening_play_progress (
    id                  BIGSERIAL PRIMARY KEY,
    exercise_attempt_id BIGINT NOT NULL REFERENCES exercise_attempts(id),
    listening_key       VARCHAR(80) NOT NULL,
    play_count          INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (exercise_attempt_id, listening_key)
);

-- Log bất biến: 1 dòng/lần học sinh THỰC SỰ mở gợi ý ra xem (không phải
-- lúc đủ điều kiện mở khóa) — phục vụ thống kê câu nào học sinh cần gợi ý.
CREATE TABLE listening_hint_events (
    id                  BIGSERIAL PRIMARY KEY,
    exercise_attempt_id BIGINT NOT NULL REFERENCES exercise_attempts(id),
    question_id         BIGINT NOT NULL REFERENCES questions(id),
    student_id          BIGINT NOT NULL REFERENCES students(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_listening_hint_events_question ON listening_hint_events(question_id);
CREATE INDEX idx_listening_hint_events_student ON listening_hint_events(student_id);

INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('lms.listening_hint_unlock_play_count', '3', 'Số lần nghe hết audio tối thiểu để mở khóa gợi ý tapescript cho câu hỏi Nghe', 'ACADEMIC');
