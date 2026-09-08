-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07 — ngưỡng %
-- (số lượt đạt / tổng lượt yêu cầu) để phía học viên hiện popup nhắc giữa
-- chừng "đã đạt tiêu chí, muốn dừng hay làm tiếp". ĐỘC LẬP với
-- completion_threshold_percent (nay là ngưỡng % pass điểm trắc nghiệm, xem
-- V59 + ghi chú 2026-08-11 trong ReviewVideoService) và required_view_count
-- (số lượt tối thiểu để hoàn thành).
ALTER TABLE review_videos
    ADD COLUMN session_pass_ratio_threshold_percent INT NOT NULL DEFAULT 70;
