-- V127: Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19 — đổi thời điểm giao "BTVN buổi
-- sau" từ lúc Giáo viên Lưu nháp (UC-21 bước 2) sang lúc Gửi nhận xét (UC-21 bước 4, submitComments()).
-- Trước V127, chọn 1 Exercise/ReviewVideoSet ở writeComment()/updateComment()/importRow() giao bài
-- NGAY cho cả lớp (StudentCommentService#resolveExerciseHomework/resolveVideoHomework gọi thẳng
-- deliverToClass) dù nhận xét còn DRAFT, chưa qua duyệt. Giờ 3 method đó chỉ VALIDATE (còn tồn tại +
-- không xung đột cùng buổi) rồi lưu tạm lựa chọn vào 3 cột dưới đây — chỉ submitComments() mới thật
-- sự materialize thành ExerciseAssignment/ReviewVideoAssignment (xem Javadoc submitComments).
--
-- pending_homework_next_due_date (TIMESTAMP, không phải TIMESTAMPTZ) bắt buộc phải có: hạn nộp tự
-- chọn của Giáo viên (request.homeworkNextDueDate(), kiểu LocalDateTime) trước đây nạp thẳng vào
-- dueAt của bản giao ngay lúc lưu — hoãn giao bài mà không lưu lại giá trị này thì hạn nộp tự chọn sẽ
-- mất âm thầm lúc Gửi (rơi về mặc định "buổi kế tiếp"). Giữ nguyên kiểu TIMESTAMP (giờ tường thuật
-- thô, chưa gắn múi giờ) — chỉ quy đổi múi giờ thật lúc resolveDueAt() xử lý ở bước Gửi.
ALTER TABLE student_comments
    ADD COLUMN pending_homework_next_exercise_id BIGINT NULL REFERENCES exercises(id),
    ADD COLUMN pending_homework_next_review_video_set_id BIGINT NULL REFERENCES review_video_sets(id),
    ADD COLUMN pending_homework_next_due_date TIMESTAMP NULL;
