-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 — ngưỡng % đạt (viết VÀ nói) của
-- video REFLEX trước đây hardcode 70 (rải rác 3 nơi: ReflexSequentialGradingService,
-- ReviewVideoService, ReviewVideoReportService — xem comment cũ "CỐ ĐỊNH, ReviewVideoSet chưa có
-- field pass_threshold_percent riêng"). Từ nay đọc từ review_videos.completion_threshold_percent
-- (field đã có sẵn, trước đây CHỈ có ý nghĩa với CONNECTION — nay dùng lại cho REFLEX với ý nghĩa
-- khác: ngưỡng % đạt viết/nói mỗi câu, thay vì % xem tối thiểu/lượt).
--
-- Backfill 70 cho các video REFLEX ĐÃ TỒN TẠI (mặc định cột là 80, dành cho CONNECTION) — bắt buộc
-- phải backfill để KHÔNG đổi ngầm hành vi đã áp dụng cho học sinh đang học dở các video REFLEX cũ
-- (nếu để mặc định 80, ngưỡng đạt sẽ tự nhiên bị siết chặt hơn 70% cũ mà không ai chủ động đổi).
UPDATE review_videos
SET completion_threshold_percent = 70
WHERE review_video_set_id IN (
    SELECT id FROM review_video_sets WHERE video_type = 'REFLEX'
);
