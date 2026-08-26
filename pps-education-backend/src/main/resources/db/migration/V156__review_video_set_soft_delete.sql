-- Kho Video Ôn tập: "Xóa Bộ" — mirror V87 (exams.deleted_at, "Xóa Đề"): soft-delete qua deleted_at,
-- không xóa cứng vì review_videos/review_video_set_class_assignments/review_video_assignments có thể
-- đã tham chiếu qua Bộ. "Xóa Video" (ReviewVideo) đã có sẵn từ trước (hard-delete, tự chặn nếu học sinh
-- đã có dữ liệu — xem ReviewVideoService#deleteVideo) — deleteSet chỉ cho xóa khi Bộ đã hết Video
-- (giáo viên tự xóa từng Video trước qua nút thùng rác đã có). Bổ sung ngoài SDD gốc, đã xác nhận với
-- người dùng 2026-08-26.
ALTER TABLE review_video_sets ADD COLUMN deleted_at TIMESTAMPTZ NULL;

INSERT INTO permissions (code, name, module, description) VALUES
('lms.review-video.delete', 'Xóa Bộ video ôn tập', 'LMS', 'UC-23');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.review-video.delete' AND r.code = 'TEACHER';
