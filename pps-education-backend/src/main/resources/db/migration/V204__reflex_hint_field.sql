-- =====================================================================
-- V204: Trường "hint" (§D.5, bản bàn giao 30/9) cho luồng chấm Speaking v3.
--
-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-01 — UC-23b
-- (Video phản xạ): học sinh tự luyện trên LMS không có giáo viên chữa bài
-- giữa các lần thu, nên AI chấm nay trả thêm "cách luyện" TÁCH RIÊNG khỏi
-- feedback (feedback vẫn cấm gợi ý sửa, dành cho giáo viên; hint ngược lại
-- bắt buộc là cách luyện cụ thể, dành cho học sinh). Xem ReflexV2Scoring#trimHint,
-- ReflexV2AiGradingService, rubrics-v3/speaking-rubric-common-rules.md §D.5.
-- =====================================================================

ALTER TABLE reflex_question_progress
    ADD COLUMN writing_hint TEXT,
    ADD COLUMN speaking_hint TEXT;

ALTER TABLE reflex_question_progress_history
    ADD COLUMN hint TEXT;
