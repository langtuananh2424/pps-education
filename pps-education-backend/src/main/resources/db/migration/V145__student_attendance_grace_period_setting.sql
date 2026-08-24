-- =====================================================================
-- V145: Khung giờ ân hạn (grace period) điểm danh học sinh sau end_time
-- buổi học (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
-- 2026-08-22 -- thay thế ràng buộc "chỉ trong [start_time, end_time]"
-- chốt ngày 2026-08-18). Xem docs/uc/phan-he-05-hoc-sinh.md UC-15,
-- StudentAttendanceService#isWithinSessionWindow.
--
-- Key đặt tiền tố "student_attendance." (không dùng "attendance." trần —
-- prefix đó đã dùng cho cấu hình chấm công NHÂN VIÊN UC-09, category
-- ATTENDANCE, xem V7__attendance_core.sql -- khác domain hoàn toàn với
-- điểm danh HỌC SINH ở đây, tránh nhầm lẫn 2 nhóm cấu hình).
-- =====================================================================

INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('student_attendance.grace_period_minutes', '60', 'Số phút nới thêm sau end_time buổi học để Giáo viên vẫn điểm danh/sửa được (UC-15)', 'ACADEMIC');
