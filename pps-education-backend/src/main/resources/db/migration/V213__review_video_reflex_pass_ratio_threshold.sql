-- Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-10-15) — REFLEX: ngưỡng % SỐ CÂU đạt
-- (khác completion_threshold_percent là ngưỡng điểm Viết/Nói của TỪNG câu) để tính Đạt/Chưa đạt
-- tổng thể của 1 học sinh trong 1 bộ Video phản xạ. Không có ý nghĩa với videoType=CONNECTION
-- (CONNECTION đã có cơ chế tương đương dùng completion_threshold_percent, xem isConnectionVideoPassed).
ALTER TABLE review_videos
    ADD COLUMN pass_ratio_threshold_percent INT NOT NULL DEFAULT 70;
