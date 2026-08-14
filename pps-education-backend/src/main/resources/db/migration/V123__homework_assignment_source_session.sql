-- V123: Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14 — Portal học sinh cần hiển thị
-- "BTVN này giao trong buổi nào" (không chỉ hạn nộp), thay vì phải đoán qua due_at. Lưu lại buổi học
-- (class_sessions) mà Giáo viên đang viết Nhận xét (UC-21, buổi X) lúc chọn Bài/Video làm "BTVN buổi
-- sau" — mirror đúng chỗ StudentCommentService#resolveExerciseHomework/resolveVideoHomework đã có sẵn
-- ClassSession session trong scope. NULL cho bản giao cũ (trước V123) — không backfill vì không suy
-- ngược được buổi gốc từ dữ liệu hiện có.
ALTER TABLE exercise_assignments
    ADD COLUMN source_class_session_id BIGINT NULL REFERENCES class_sessions(id);

ALTER TABLE review_video_assignments
    ADD COLUMN source_class_session_id BIGINT NULL REFERENCES class_sessions(id);
