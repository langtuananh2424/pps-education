-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — UC-40/UC-41: phân biệt điểm chấm
-- câu ESSAY do AI chấm tự động (Bài Exercise.skill_category=WRITING, xem WritingAiGradingService)
-- với điểm chấm thủ công của Giáo viên (UC-41, ManualGradingService) trong cùng bảng
-- student_answer_grading. grader_user_id vẫn giữ NOT NULL (dùng exercises.created_by làm giá trị khi
-- AI chấm, thay vì thêm user hệ thống ảo) — cột này chỉ để phân biệt RÕ trong dữ liệu/lịch sử, tránh
-- hiểu nhầm giáo viên đã tự tay chấm.
ALTER TABLE student_answer_grading ADD COLUMN grading_source VARCHAR(10) NOT NULL DEFAULT 'HUMAN';

COMMENT ON COLUMN student_answer_grading.grading_source IS 'HUMAN (GV chấm tay, UC-41) hoặc AI (chấm tự động ESSAY thuộc Bài skill_category=WRITING) - bổ sung V138';
