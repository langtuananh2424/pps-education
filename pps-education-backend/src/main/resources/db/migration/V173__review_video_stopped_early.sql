-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — học sinh
-- chủ động chọn "Dừng, xem kết quả" ở popup ngưỡng (đã đạt
-- session_pass_ratio_threshold_percent số lượt yêu cầu, xem V167) được tính
-- là ĐÃ HOÀN THÀNH nhiệm vụ luôn, không cần xem đủ 100% required_view_count.
-- Cờ riêng (không tái dùng is_completed) để recomputeProgress (V160, công
-- thức viewCount >= requiredViewCount) không tự ghi đè lại false khi có báo
-- tiến độ mới ở lượt xem SAU lúc đã dừng sớm.
ALTER TABLE review_video_progress
    ADD COLUMN stopped_early BOOLEAN NOT NULL DEFAULT FALSE;
