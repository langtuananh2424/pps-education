-- Bổ sung ngoài SDD gốc (UC-23b Video phản xạ), đã xác nhận với người dùng 2026-09-21: tích hợp bộ tiêu
-- chí Speaking v2 (Khối 6-7, dạng câu hỏi ngắn) do người training bàn giao — chấm viết → phiên âm mù →
-- chấm nói, điểm Ngữ pháp KHOÁ từ Bước 1 rồi mang sang Bước 2. Xem ReflexV2AiGradingService.
--
-- rubric_version: 'v2' nếu dòng này được chấm bằng luồng mới (NULL = luồng cũ). Bước Nói định tuyến theo
-- CHÍNH cột này (không theo cờ cấu hình hiện tại) để 1 câu hỏi dở dang không bị đổi luồng giữa chừng khi
-- bật/tắt cờ app.ai-grading.reflex-v2.enabled.
-- writing_locked_grammar_percent / writing_red_error_count: điểm Ngữ pháp khoá + số lỗi đỏ của bài viết —
-- Bước Nói cần cả hai (điểm Ngữ pháp lấy nguyên từ đây; lỗi đỏ mới khi nói cộng với số này, từ 2 trở lên
-- thì trần 60%).
-- writing_audit / speaking_audit: bằng chứng chấm (model thực tế, cổng chặn, danh sách đếm, suspect_words,
-- độ khớp nội dung, điểm gồm Phát âm...) để hiệu chuẩn/kiểm chứng đối chiếu điểm giáo viên — KHÔNG trả ra FE.
ALTER TABLE reflex_question_progress ADD COLUMN rubric_version VARCHAR(10);
ALTER TABLE reflex_question_progress ADD COLUMN writing_locked_grammar_percent NUMERIC(5, 2);
ALTER TABLE reflex_question_progress ADD COLUMN writing_red_error_count INTEGER;
ALTER TABLE reflex_question_progress ADD COLUMN writing_audit JSONB;
ALTER TABLE reflex_question_progress ADD COLUMN speaking_audit JSONB;
