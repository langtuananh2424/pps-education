-- =====================================================================
-- V93: bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06.
--
-- 1) Giáo viên chọn 1 lượt làm bài (trong nhiều lượt) làm kết quả CHÍNH
-- THỨC — xem ExerciseAttemptService#selectForGrading. Chỉ đánh dấu, CHƯA
-- nối sang grade_entries/grade_components (chưa có quy tắc ánh xạ Bài
-- tập → đầu điểm nào trong SDD — cần xác nhận thêm trước khi làm, xem
-- GAP đã ghi trong Javadoc ExerciseAttemptService).
--
-- 2) Ngưỡng "đạt" cho video Kết nối (CONNECTION) đổi từ "đủ SỐ LƯỢT
-- (viewCount >= requiredViewCount)" sang TỶ LỆ % (viewCount/requiredView
-- Count >= ngưỡng) — dùng CHUNG 1 ngưỡng với video Phản xạ (REFLEX, áp ở
-- tầng FE vì REFLEX tính hoàn thành client-side, không có bảng progress
-- riêng như CONNECTION) để nhất quán "đạt BTVN Video = ≥80%", khớp đúng
-- tinh thần ngưỡng 80% của BTVN Ngữ pháp (exercises.pass_threshold_percent).
-- =====================================================================

ALTER TABLE exercise_attempts ADD COLUMN selected_for_grading BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('review_video.completion_pass_threshold_percent', '80', 'Ngưỡng % lượt xem đạt/yêu cầu để tính "đạt" BTVN Video Kết nối (CONNECTION)', 'FEATURE_FLAG');
