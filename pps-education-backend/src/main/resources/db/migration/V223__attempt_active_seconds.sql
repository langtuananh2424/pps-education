-- Bổ sung ngoài SDD gốc, đã yêu cầu 2026-10-26 — đo THỜI GIAN LÀM BÀI THỰC (ngầm, học sinh không thấy) của mỗi lượt
-- BTVN (Bài lẻ + từng Bài trong Lô), hiện ở trang "Thống kê BTVN" của admin. Chỉ để tham khảo/đối chiếu, KHÔNG
-- tham gia công thức điểm hay ngưỡng đạt.
-- active_seconds: tổng số giây học sinh THỰC SỰ ở trên màn hình làm bài (tab đang hiện + cửa sổ đang focus),
--   do client gửi định kỳ; server chặn không cho vượt thời gian thực trôi qua kể từ lần gửi trước.
-- last_active_ping_at: mốc server nhận lần gửi gần nhất — dùng để chặn gửi dồn/gian lận cộng quá số giây thực tế.
ALTER TABLE exercise_attempts
    ADD COLUMN active_seconds INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_active_ping_at TIMESTAMPTZ;
