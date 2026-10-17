-- =====================================================================
-- V215: Lưu trữ lượt xem clip Kết nối khi học sinh "Làm lại từ đầu"
-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-17.
--
-- archived_at NOT NULL = lượt đã được lưu trữ: giữ nguyên lịch sử (lượt xem và
-- câu trả lời gắn với lượt) để xem ở trang thống kê, nhưng không tính vào số lượt,
-- trạng thái hoàn thành hay điểm trắc nghiệm của lần làm hiện tại.
-- =====================================================================
ALTER TABLE review_video_watch_sessions ADD COLUMN archived_at TIMESTAMPTZ;
