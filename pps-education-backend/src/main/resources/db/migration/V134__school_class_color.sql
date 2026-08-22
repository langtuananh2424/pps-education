-- Màu hiển thị của lớp trên lịch làm việc dạng lưới (bổ sung ngoài SDD gốc,
-- xác nhận với người dùng 2026-08-21) — hệ thống tự chọn ngẫu nhiên từ 1
-- bảng màu cố định khi tạo lớp mới, người dùng có thể đổi lại sau qua màn
-- Sửa lớp. Lớp đã tồn tại trước migration này được gán màu theo id % 12
-- (rải đều qua bảng màu, không cần random thật vì chỉ chạy 1 lần).
ALTER TABLE classes ADD COLUMN color VARCHAR(7);

UPDATE classes SET color = CASE (id % 12)
    WHEN 0 THEN '#F97316'
    WHEN 1 THEN '#0EA5E9'
    WHEN 2 THEN '#10B981'
    WHEN 3 THEN '#8B5CF6'
    WHEN 4 THEN '#EC4899'
    WHEN 5 THEN '#EAB308'
    WHEN 6 THEN '#14B8A6'
    WHEN 7 THEN '#6366F1'
    WHEN 8 THEN '#F43F5E'
    WHEN 9 THEN '#84CC16'
    WHEN 10 THEN '#06B6D4'
    ELSE '#A855F7'
END;

ALTER TABLE classes ALTER COLUMN color SET NOT NULL;
