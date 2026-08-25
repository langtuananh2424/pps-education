-- UC-21 Nhận xét học sinh: tách kênh "BTVN online" Nghe (LISTENING) ra khỏi kênh "Ngữ pháp" hiện có.
-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24. Trước đây field homeworkNextExerciseId
-- ("kênh Ngữ pháp") không lọc theo skill_category — chỉ đổi NHÃN hiển thị "Bài nghe" cho buổi
-- teacher_type=FOREIGN qua i18n (shared.grammarChannel.FOREIGN), khiến Bài VOCAB_GRAMMAR và Bài LISTENING
-- của cùng 1 Đề FOREIGN bị trộn lẫn chung 1 dropdown, không tách được. Mirror đúng cặp cột
-- homework_next_reading_exercise_assignment_id / pending_homework_next_reading_exercise_id (V137).
ALTER TABLE student_comments ADD COLUMN homework_next_listening_exercise_assignment_id BIGINT REFERENCES exercise_assignments(id);
ALTER TABLE student_comments ADD COLUMN pending_homework_next_listening_exercise_id BIGINT;

COMMENT ON COLUMN student_comments.homework_next_listening_exercise_assignment_id IS 'BTVN - Online - Nghe: bản giao Exercise (skillCategory=LISTENING) cho buổi sau, chỉ buổi teacher_type=FOREIGN. Mirror homework_next_reading_exercise_assignment_id (V146).';
COMMENT ON COLUMN student_comments.pending_homework_next_listening_exercise_id IS 'Id Exercise (nguồn, skillCategory=LISTENING) vừa chọn nhưng CHƯA Gửi nhận xét (V146).';
