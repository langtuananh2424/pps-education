-- UC-21 Nhận xét học sinh: gộp lại kênh "BTVN online" Nghe (LISTENING) vào kênh "Ngữ pháp" hiện có
-- (revert V146, đã xác nhận với người dùng 2026-08-25). Sau khi thử tách 2 cột riêng, người dùng quyết
-- định quay lại 1 cột/1 dropdown "Ngữ pháp" duy nhất — nhưng KHÔNG lặp lại lỗi trộn lẫn kỹ năng đã ghi ở
-- V146 (buổi teacherType=FOREIGN vẫn chỉ lọc đúng Bài skillCategory=LISTENING, không lẫn VOCAB_GRAMMAR
-- — xem StudentCommentService#grammarChannelSkillCategory). Không sửa/xoá V146 (đã áp dụng) — theo đúng
-- quy ước migration chỉ thêm mới, không sửa migration cũ.
ALTER TABLE student_comments DROP COLUMN homework_next_listening_batch_id;
ALTER TABLE student_comments DROP COLUMN pending_homework_next_listening_exam_id;
