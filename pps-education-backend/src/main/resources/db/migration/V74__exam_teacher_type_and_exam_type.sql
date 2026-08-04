-- UC-40 (Kho đề): bổ sung 2 tiêu chí lọc mới ở cấp Đề (exams), đã xác
-- nhận với người dùng 2026-08-04:
-- - teacher_type (VIETNAMESE/FOREIGN): Đề dành cho GV Việt Nam hay GV
--   nước ngoài, dùng để lọc khi giao bài. Bắt buộc chọn 1 trong 2 khi
--   tạo Đề (mirror ClassSession.teacherType nhưng bắt buộc, không
--   nullable như bên đó — Đề luôn phải rõ loại GV ngay từ lúc tạo).
-- - exam_type (REVIEW/HOMEWORK): "Loại đề" — Ôn tập/Bài tập về nhà.
--   HOÀN TOÀN ĐỘC LẬP với exercises.exercise_type (SELF_PRACTICE/
--   ASSIGNED/MOCK_TEST/SKILL_PRACTICE) — không thay thế, không đụng.
--
-- DEFAULT tạm để backfill an toàn các dòng exams cũ (từ V66, trước khi 2
-- cột này tồn tại) rồi DROP DEFAULT ngay — buộc mọi Đề tạo mới sau
-- migration này phải truyền tường minh, khớp yêu cầu bắt buộc chọn ở
-- CreateExamRequest. Giá trị default dùng khi backfill chỉ để không vỡ
-- NOT NULL, không phản ánh nghiệp vụ thật của các Đề cũ.
ALTER TABLE exams
    ADD COLUMN teacher_type VARCHAR(20) NOT NULL DEFAULT 'VIETNAMESE',
    ADD COLUMN exam_type    VARCHAR(20) NOT NULL DEFAULT 'HOMEWORK';

ALTER TABLE exams ALTER COLUMN teacher_type DROP DEFAULT;
ALTER TABLE exams ALTER COLUMN exam_type DROP DEFAULT;
