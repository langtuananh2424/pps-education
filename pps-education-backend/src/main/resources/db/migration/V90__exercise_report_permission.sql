-- V90: FR-ACA-07 "Thống kê BTVN theo lớp" (UC-66, bổ sung ngoài SDD gốc,
-- đã xác nhận với người dùng 2026-08-05) — permission mới cho
-- ExerciseReportController. Không tái dùng lms.exercise.* (nhóm soạn/giao
-- đề UC-40) vì đây là nhóm hành động khác: "xem báo cáo". Gate theo tiêu
-- chí đã dùng ở V63 (lms.review-video.view): chỉ TEACHER + SITE_MANAGER
-- (audience đã xác nhận với người dùng), KHÔNG gán SYS_ADMIN/HEAD_ACADEMIC
-- mặc định — đúng tiền lệ V28, cấp thêm qua UC-04 (user_permission_overrides)
-- nếu cần test bằng tài khoản khác.

INSERT INTO permissions (code, name, module, description) VALUES
('lms.exercise.report.view', 'Xem thống kê BTVN theo lớp', 'LMS', 'FR-ACA-07/UC-66');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.exercise.report.view'
  AND r.code IN ('TEACHER', 'SITE_MANAGER');
