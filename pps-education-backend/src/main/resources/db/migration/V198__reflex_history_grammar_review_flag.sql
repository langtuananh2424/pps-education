-- Bổ sung ngoài SDD gốc (UC-23b Video phản xạ, rubric v3), đã xác nhận với người dùng 2026-09-29: cờ "cần giáo
-- viên soát điểm Ngữ pháp" cho từng lần ghi âm.
--
-- Bài nói chỉ có MỘT lượt phiên âm (phòng đào tạo chấp nhận vì chi phí) nên vài từ nghe nhầm có thể thành lỗi đỏ
-- ngữ pháp giả và kéo Ngữ pháp xuống trần 60%. Quy trình phòng đào tạo chốt 29/9: bài có ≥2 lỗi đỏ NGỮ PHÁP ở
-- nhánh chấm lại từ transcript (nói KHÁC bài viết) thì điểm Ngữ pháp chỉ là tham khảo — giáo viên đọc lại các chỗ
-- bị tô đỏ rồi mới chốt. Cột này để trang thống kê chỉ ra đúng những lần ghi âm cần soát.
--
-- grammar_review_required: true khi lần ghi âm đó cần giáo viên soát (xem ReflexV2AiGradingService).
-- grammar_review_quotes: các đoạn transcript bị tô đỏ ngữ pháp (JSON mảng chuỗi) — chỗ giáo viên cần nghe lại.
ALTER TABLE reflex_question_progress_history ADD COLUMN grammar_review_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reflex_question_progress_history ADD COLUMN grammar_review_quotes JSONB;
