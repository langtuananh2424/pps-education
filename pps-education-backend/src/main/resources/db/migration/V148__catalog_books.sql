-- UC-40 Kho đề: chèn thêm cấp "Sách" giữa Curriculum (chương trình+khối, VD "IELTS Grade 6" — track +
-- gradeLevel đã có sẵn từ V140) và Unit — V144 trước đây cho Unit gắn thẳng vào Curriculum, người dùng
-- phản hồi sai vì "Khung chương trình" chỉ là khung, không phải nơi tạo Sách. Cấu trúc đúng: Curriculum
-- (chương trình+khối) -> Sách (books, MỚI) -> Unit -> Sub Topic -> Lesson (exams) -> Bài (exercises).
-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24.
CREATE TABLE books (
    id             BIGSERIAL PRIMARY KEY,
    curriculum_id  BIGINT NOT NULL REFERENCES curriculums(id),
    title          VARCHAR(300) NOT NULL,
    display_order  INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_books_curriculum ON books(curriculum_id);

-- Dữ liệu test hiện có (Unit tạo trực tiếp dưới Curriculum trước khi có Sách) chưa có Sách để gắn vào —
-- xoá sạch để reparent sang book_id (vài dòng test tay lúc soạn tính năng, KHÔNG xoá exams/Lesson thật
-- — chỉ gỡ tham chiếu sub_topic_id của chúng về NULL, giáo viên chọn lại Sách/Unit/SubTopic sau). Thứ
-- tự: gỡ FK exams.sub_topic_id trước -> xoá sub_topics (con của units) -> xoá units.
UPDATE exams SET sub_topic_id = NULL WHERE sub_topic_id IS NOT NULL;
DELETE FROM sub_topics;
DELETE FROM units;

ALTER TABLE units DROP COLUMN curriculum_id;
ALTER TABLE units ADD COLUMN book_id BIGINT NOT NULL REFERENCES books(id);
CREATE INDEX idx_units_book ON units(book_id);

COMMENT ON TABLE books IS 'Sách giáo trình (VD "Cambridge English for Schools 6"), thuộc 1 Curriculum (chương trình+khối). Xem docs/sdd-groups/09-lms-and-portal.md.';
COMMENT ON COLUMN units.book_id IS 'V148 — Unit giờ thuộc 1 Sách, không còn gắn thẳng Curriculum như V144.';
