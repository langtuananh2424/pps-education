-- V129: Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19 — video CONNECTION (xem theo lượt,
-- chấm qua review_video_watch_sessions/review_video_progress) hiện track tiến độ CHUNG 1 rollup theo
-- (video, học sinh), không phân biệt lần giao nào. Từ V128, 1 bộ video có thể được giao ĐỘC LẬP nhiều
-- lần (mỗi buổi Nhận xét 1 lần, không còn huỷ lẫn nhau) — cần tách rollup tiến độ theo TỪNG lần giao để
-- 2 lần giao chấm điểm/viewCount riêng biệt, đúng ý định nghiệp vụ. Mirror đúng cách
-- review_video_question_submissions (video REFLEX) đã làm từ V69 (review_video_assignment_id).
-- NULL cho dữ liệu cũ trước migration này — không suy ngược được lần giao gốc.
ALTER TABLE review_video_watch_sessions
    ADD COLUMN review_video_assignment_id BIGINT NULL REFERENCES review_video_assignments(id);

ALTER TABLE review_video_progress
    ADD COLUMN review_video_assignment_id BIGINT NULL REFERENCES review_video_assignments(id);
