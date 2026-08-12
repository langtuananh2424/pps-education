-- Backfill dữ liệu attitude cũ (bổ sung ngoài SDD gốc, đã xác nhận với người
-- dùng 2026-08-12) — StudentComment.Attitude đã chốt lại thành 5 mức
-- WEAK/AVERAGE/FAIR/GOOD/EXCELLENT (xem Javadoc enum), bỏ POOR/ABOVE_AVERAGE
-- của thang 6 mức cũ. Dữ liệu đang lưu attitude='ABOVE_AVERAGE' (nằm giữa
-- AVERAGE và GOOD trên thang cũ) gây lỗi "No enum constant" khi đọc lại —
-- map sang FAIR (Khá), mức gần nghĩa nhất trên thang mới.
UPDATE student_comments SET attitude = 'FAIR' WHERE attitude = 'ABOVE_AVERAGE';
