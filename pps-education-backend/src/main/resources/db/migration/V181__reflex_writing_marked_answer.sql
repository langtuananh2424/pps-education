-- Bổ sung ngoài SDD gốc (UC-23b Video phản xạ, bước Writing), đã xác nhận với người dùng 2026-09-16:
-- thay feedback văn xuôi dài dòng cũ bằng hiển thị lại CHÍNH câu trả lời của học sinh, đánh dấu lỗi bằng
-- markup {{err}}...{{/err}} (đồng nhất với speaking_transcript, xem V178). Xem
-- ReflexWritingGrammarAiGradingService. writing_feedback vẫn giữ nguyên (không xoá cột) — nay CHỈ dùng
-- cho thông báo lỗi khi AI chấm thất bại (xem ReflexSequentialGradingService#AI_GRADING_FAILED_FEEDBACK).
ALTER TABLE reflex_question_progress ADD COLUMN writing_marked_answer TEXT;
