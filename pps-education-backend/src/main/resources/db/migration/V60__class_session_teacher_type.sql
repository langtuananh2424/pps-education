-- "Loại giáo viên" (VIETNAMESE/FOREIGN) của 1 buổi học cụ thể — hiển thị
-- cho Học sinh/Phụ huynh biết buổi đó do giáo viên Việt Nam hay giáo
-- viên nước ngoài dạy. Chỉ ở cấp buổi học (KHÔNG đụng employees/users —
-- 1 giáo viên có thể dạy cả 2 loại buổi tùy lịch). Tùy chọn (nullable),
-- buổi cũ để trống, không set default. Bổ sung ngoài SDD gốc, đã xác
-- nhận với người dùng 2026-07-29.
ALTER TABLE class_sessions
    ADD COLUMN teacher_type VARCHAR(20) NULL;
