-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-15 — ngưỡng % ĐẠT CẢ BÀI của video REFLEX.
-- Trước đây badge "Đạt/Chưa đạt" trên trang thống kê BTVN (ReviewVideoReportService) chỉ có 1 công thức
-- cố định "MỌI câu đều đạt ngưỡng viết/nói" (completion_threshold_percent), không chỉnh được.
-- Cột mới: NULL = giữ nguyên công thức cũ (không đổi hành vi video đang chạy); có giá trị = học sinh
-- ĐẠT khi điểm trung bình (viết + nói của mọi câu) >= ngưỡng này, KHÔNG còn bắt buộc từng câu đạt.
-- Độc lập với completion_threshold_percent (ngưỡng đạt viết/nói MỖI CÂU, quyết định mở khoá bước/câu kế).
ALTER TABLE review_videos
    ADD COLUMN overall_pass_threshold_percent INTEGER NULL;

ALTER TABLE review_videos
    ADD CONSTRAINT chk_review_videos_overall_pass_threshold
    CHECK (overall_pass_threshold_percent IS NULL OR (overall_pass_threshold_percent BETWEEN 1 AND 100));
