-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — chuẩn bị cho dạng câu hỏi Listening
-- "nghe rồi chọn đáp án bằng hình" (mỗi lựa chọn là 1 tấm ảnh thay vì chữ). NULLABLE — mọi câu hỏi khác
-- (đáp án chữ như trước giờ) không dùng cột này.
ALTER TABLE question_choices ADD COLUMN image_url VARCHAR(1000);

COMMENT ON COLUMN question_choices.image_url IS 'Ảnh riêng cho lựa chọn này (câu hỏi Listening dạng chọn đáp án bằng hình) — NULL = đáp án chữ như trước (xem QuestionChoice.imageUrl).';
