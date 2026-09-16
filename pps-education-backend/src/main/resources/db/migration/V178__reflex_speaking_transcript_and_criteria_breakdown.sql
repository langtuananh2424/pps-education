-- Bổ sung ngoài SDD gốc (UC-23b Video phản xạ, bước Speaking), đã xác nhận với người dùng 2026-09-16:
-- transcript có đánh dấu lỗi (markup {{err}}...{{/err}}) + điểm % tách riêng theo từng tiêu chí,
-- thay cho feedback văn xuôi dài dòng cũ. Xem ReflexSpeakingContentAiGradingService.
ALTER TABLE reflex_question_progress ADD COLUMN speaking_transcript TEXT;
ALTER TABLE reflex_question_progress ADD COLUMN speaking_criteria_scores JSONB;
