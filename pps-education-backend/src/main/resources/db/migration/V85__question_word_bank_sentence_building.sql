-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: mở rộng "Soạn Bài" GV Việt Nam
-- thêm 2 dạng câu hỏi nhiều-chỗ-trống (Điền từ theo hộp từ vựng / Sắp xếp câu) không biểu diễn được
-- bằng answer_text đơn hoặc selected_choice_ids hiện có — dùng JSONB tự do, cùng pattern với cột
-- tags/selected_choice_ids đã có, không tạo bảng con vì không có nhu cầu truy vấn theo từng blank.
-- group_key dùng cho dạng "đọc hiểu — lưới": nhiều câu MULTIPLE_CHOICE cùng 1 group_key được gộp
-- hiển thị chung 1 đoạn văn (reference_passage) ở cả màn soạn lẫn màn học sinh làm bài.

ALTER TABLE questions ADD COLUMN structured_content JSONB NULL;
ALTER TABLE questions ADD COLUMN group_key VARCHAR(64) NULL;
CREATE INDEX idx_questions_group_key ON questions(group_key) WHERE group_key IS NOT NULL;

ALTER TABLE student_answers ADD COLUMN structured_answer JSONB NULL;
