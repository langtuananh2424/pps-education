-- UC-21 Nhận xét học sinh: tách cột "Offline" trong nhóm "BTVN buổi trước" (chấm điểm) và nhóm "BTVN"
-- giao bài buổi sau (mô tả bài giao) thành Reading/Writing riêng — CHỈ áp dụng cho buổi
-- class_sessions.teacher_type = VIETNAMESE (buổi giáo viên nước ngoài giữ nguyên hành vi cũ, dùng
-- homework_previous_score/homework_next như trước). Bổ sung ngoài SDD gốc, đã xác nhận với người dùng
-- 2026-08-21. Không xoá/đổi cột cũ — giữ nguyên cho buổi FOREIGN và dữ liệu lịch sử.

ALTER TABLE student_comments ADD COLUMN homework_previous_reading_score VARCHAR(30);
ALTER TABLE student_comments ADD COLUMN homework_previous_writing_score VARCHAR(30);
ALTER TABLE student_comments ADD COLUMN homework_next_reading TEXT;
ALTER TABLE student_comments ADD COLUMN homework_next_writing TEXT;

COMMENT ON COLUMN student_comments.homework_previous_reading_score IS 'BTVN buổi trước - Offline - Reading: điểm % giáo viên tự chấm tay (chỉ buổi teacher_type=VIETNAMESE, V130).';
COMMENT ON COLUMN student_comments.homework_previous_writing_score IS 'BTVN buổi trước - Offline - Writing: điểm % giáo viên tự chấm tay (chỉ buổi teacher_type=VIETNAMESE, V130).';
COMMENT ON COLUMN student_comments.homework_next_reading IS 'BTVN (giao buổi sau) - Offline - Reading: mô tả bài + trang giao offline (chỉ buổi teacher_type=VIETNAMESE, V130).';
COMMENT ON COLUMN student_comments.homework_next_writing IS 'BTVN (giao buổi sau) - Offline - Writing: mô tả bài + trang giao offline (chỉ buổi teacher_type=VIETNAMESE, V130).';
