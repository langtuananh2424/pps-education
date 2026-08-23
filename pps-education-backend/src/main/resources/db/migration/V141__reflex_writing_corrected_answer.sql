-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — khi học sinh nộp SAI phần viết từ 3
-- lần trở lên (writing_attempt_count >= 3), FE hiện gợi ý câu trả lời đã sửa lỗi ngữ pháp để học sinh
-- tham khảo/copy — AI được yêu cầu CHỈ sửa lỗi trong câu trả lời gốc của học sinh (giữ nguyên cấu
-- trúc/ý), KHÔNG tự viết câu trả lời hoàn toàn mới. Lưu cùng lúc chấm (mọi lần chấm, không chỉ từ lần
-- 3) để không phải gọi AI thêm 1 lần riêng lúc hiện gợi ý — ngưỡng "từ lần 3" chỉ là điều kiện HIỂN THỊ
-- ở FE, không phải điều kiện sinh dữ liệu.
ALTER TABLE reflex_question_progress ADD COLUMN writing_corrected_answer TEXT;
