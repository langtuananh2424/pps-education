-- =====================================================================
-- V119: UC-70 (mới) — Quản lý Ca làm việc (shifts), gán ca cho nhân sự
-- (employee_shifts) và Lịch làm việc/nghỉ lễ (work_calendar) — bổ sung
-- HOÀN TOÀN ngoài SDD/SRS gốc, đã xác nhận với người dùng 2026-08-13.
--
-- Bối cảnh: 3 bảng shifts/employee_shifts/work_calendar (V7) đã tồn tại
-- từ đầu, chỉ được AttendanceService (UC-09) ĐỌC để xác định ngày làm
-- việc/cửa sổ chấm công, nhưng chưa từng có Controller/UI nào để TẠO dữ
-- liệu này — nghĩa là không có ca nào từng được gán, mọi lượt chấm công
-- thật đều bị từ chối "không phải ngày làm việc". Đây là thiếu sót thật
-- trong SRS gốc (không có FR/UC nào đặc tả quản trị 3 bảng này), cần
-- cập nhật docs/uc/phan-he-04-nhan-su.md sau migration này.
--
-- Quyền GET (list/xem) không gate — dữ liệu tra cứu dùng chung (đúng
-- pattern SiteController/DepartmentController).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('hrm.shift.create', 'Tạo ca làm việc', 'HRM', 'UC-70 (bổ sung ngoài SDD gốc)'),
('hrm.shift.update', 'Sửa/vô hiệu hoá ca làm việc', 'HRM', 'UC-70 (bổ sung ngoài SDD gốc)'),
('hrm.employee-shift.assign', 'Gán/đổi ca làm việc cho nhân sự', 'HRM', 'UC-70 (bổ sung ngoài SDD gốc)'),
('hrm.work-calendar.create', 'Tạo override lịch làm việc/nghỉ lễ', 'HRM', 'UC-70 (bổ sung ngoài SDD gốc)'),
('hrm.work-calendar.delete', 'Xoá override lịch làm việc/nghỉ lễ', 'HRM', 'UC-70 (bổ sung ngoài SDD gốc)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN (
    'hrm.shift.create', 'hrm.shift.update', 'hrm.employee-shift.assign',
    'hrm.work-calendar.create', 'hrm.work-calendar.delete'
)
AND r.code IN ('HR_MANAGER', 'SUPER_ADMIN', 'SYS_ADMIN');
