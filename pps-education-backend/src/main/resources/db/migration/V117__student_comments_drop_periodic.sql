-- Bỏ hẳn biểu mẫu nhận xét theo kỳ đánh giá (MID_TERM/END_TERM) trong
-- student_comments (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
-- 2026-08-12) — nhận xét theo kỳ đánh giá (Giữa kỳ/Cuối kỳ) nay dùng field
-- comment trong grade_evaluation_results (sổ điểm, UC-19/53) thay thế.
-- student_comments chỉ còn 1 biểu mẫu DAILY (StudentComment.CommentType).
DELETE FROM student_comments WHERE comment_type IN ('MID_TERM', 'END_TERM');

ALTER TABLE student_comments DROP CONSTRAINT chk_comment_context;
ALTER TABLE student_comments ALTER COLUMN class_session_id SET NOT NULL;
ALTER TABLE student_comments DROP COLUMN academic_term_id;
ALTER TABLE student_comments ADD CONSTRAINT chk_comment_type_daily CHECK (comment_type = 'DAILY');
