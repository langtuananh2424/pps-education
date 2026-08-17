-- =====================================================================
-- V124: UC-70 (bổ sung ngoài SDD/SRS gốc, xác nhận với người dùng
-- 2026-08-14) -- cho phép 1 nhân sự có NHIỀU ca active cùng lúc, thay vì
-- chỉ 1 ca/thời điểm (ràng buộc gốc ở V7). Dùng để mô hình hoá "T7 xen kẽ"
-- bằng cách gán 2 ca độc lập cho cùng 1 nhân sự: ca A (VD T2-T7,
-- week_parity=EVEN) + ca B (VD T2-T6, week_parity=ODD) — mỗi ca dùng lại
-- nguyên cặp applies_to_weekdays/week_parity đã có ở V7, không cần thêm
-- cột mới. Việc chống chồng chéo lịch giữa các ca active cùng nhân sự do
-- EmployeeShiftService validate ở tầng application (không thể diễn đạt
-- bằng CHECK constraint đơn giản vì phải so applies_to_weekdays/week_parity
-- của nhiều bản ghi khác nhau).
-- =====================================================================

DROP INDEX idx_employee_shifts_active;
