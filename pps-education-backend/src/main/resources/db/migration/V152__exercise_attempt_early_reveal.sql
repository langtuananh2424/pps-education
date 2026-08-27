-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — UC-24/A4, UC-27/A2: học sinh ĐÃ ĐẠT
-- ngưỡng nhưng vẫn còn lượt làm lại (exercises.max_attempts) có thể TỰ NGUYỆN dừng lại, đổi lại được
-- xem NGAY đáp án đúng của lượt vừa đạt (trước đó phải làm hết max_attempts mới được xem, xem
-- ExerciseAttemptService#toResponse(StudentAnswer)). Cờ này chỉ set true qua endpoint tường minh
-- (ExerciseAttemptService#revealAnswersAndClose) — không tự động set ở đâu khác.
ALTER TABLE exercise_attempts ADD COLUMN answers_revealed_early BOOLEAN NOT NULL DEFAULT FALSE;
