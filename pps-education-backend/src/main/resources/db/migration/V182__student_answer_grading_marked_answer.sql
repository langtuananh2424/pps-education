-- Bổ sung ngoài SDD gốc (UC-40/41, chấm AI bài Writing/Essay), đã xác nhận với người dùng 2026-09-16
-- (PILOT chỉ Khối 7 IELTS): rubric "v3" tự đánh dấu lỗi ngay trong bài viết bằng markup
-- {{mã|đoạn văn bản}} (5 loại lỗi × 2 mức độ) + điểm % tách riêng theo từng tiêu chí, thay cho feedback
-- văn xuôi dài dòng cũ khi chấm bằng rubric v3. Xem WritingAiGradingService.
ALTER TABLE student_answer_grading ADD COLUMN marked_answer TEXT;
ALTER TABLE student_answer_grading ADD COLUMN criteria_scores JSONB;
