-- UC-21 Nhận xét học sinh: thêm 2 kênh "BTVN online" mới Reading/Writing (song song với kênh Ngữ pháp/
-- TV+NP đã có ở V55/V65) — chỉ áp dụng cho buổi class_sessions.teacher_type=VIETNAMESE. Bổ sung ngoài
-- SDD gốc, đã xác nhận với người dùng 2026-08-21. Mirror đúng cặp cột homework_next_exercise_assignment_id
-- / pending_homework_next_exercise_id đã có, không đổi field nào cũ.
ALTER TABLE student_comments ADD COLUMN homework_next_reading_exercise_assignment_id BIGINT REFERENCES exercise_assignments(id);
ALTER TABLE student_comments ADD COLUMN homework_next_writing_exercise_assignment_id BIGINT REFERENCES exercise_assignments(id);
ALTER TABLE student_comments ADD COLUMN pending_homework_next_reading_exercise_id BIGINT;
ALTER TABLE student_comments ADD COLUMN pending_homework_next_writing_exercise_id BIGINT;

COMMENT ON COLUMN student_comments.homework_next_reading_exercise_assignment_id IS 'BTVN - Online - Reading: bản giao Exercise (skillCategory=READING) cho buổi sau, chỉ buổi teacher_type=VIETNAMESE. Mirror homework_next_exercise_assignment_id (V137).';
COMMENT ON COLUMN student_comments.homework_next_writing_exercise_assignment_id IS 'BTVN - Online - Writing: mirror cột Reading ở trên, Exercise skillCategory=WRITING (V137).';
COMMENT ON COLUMN student_comments.pending_homework_next_reading_exercise_id IS 'Id Exercise (nguồn, skillCategory=READING) vừa chọn nhưng CHƯA Gửi nhận xét (V137).';
COMMENT ON COLUMN student_comments.pending_homework_next_writing_exercise_id IS 'Mirror cột Reading ở trên cho Writing (V137).';
