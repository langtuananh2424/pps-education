-- =====================================================================
-- V107: Quản trị viên (SYS_ADMIN) truy cập được mọi lớp cho các tính năng
-- LMS theo phạm vi lớp — bổ sung ngoài SDD gốc, đã xác nhận với người
-- dùng 2026-08-08.
--
-- Phát hiện: 5 Service dùng requireAssignedTeacher/requireReportScope làm
-- rào ownership DUY NHẤT (Giáo viên phải được phân công dạy đúng lớp qua
-- class_teachers), không có đường vượt rào cho quản trị viên — dù
-- @PreAuthorize ở Controller đã cho qua (SYS_ADMIN có sẵn quyền hành động
-- cơ bản như lms.exam.assign/lms.exercise.report.view/academic.comment.write).
--
-- Cũng phát hiện: V106 (lms.teaching-plan.manage) gán cho role 'SUPER_ADMIN'
-- — role KHÔNG tồn tại trong DB này (chỉ là quy ước tạo tay ở 1 số máy dev
-- khác, xem V44/V45), nên chưa có tác dụng thật với tài khoản quản trị
-- đang dùng (SYS_ADMIN). Gán bổ sung SYS_ADMIN ở đây.
--
-- Thêm 4 quyền quản trị ".manage" theo module (bypass ownership, KHÔNG
-- tách theo hành động — mirror academic.class.manage/academic.grade.manage,
-- khác với academic.attendance.create/update/delete vì đây không phải
-- nghiệp vụ rủi ro cao cần audit theo hành động):
--   lms.exam.manage            — ExamService + ExerciseService (Kho đề 2 cấp Đề/Bài)
--   lms.exercise-report.manage — ExerciseReportService (Thống kê BTVN theo lớp)
--   academic.comment.manage    — StudentCommentService (Nhận xét học viên)
--   lms.review-video.manage    — ReviewVideoService (Video ôn tập)
--
-- Gán cho HEAD_ACADEMIC (đào tạo, quản lý xuyên suốt) + SYS_ADMIN (quản
-- trị viên thực tế đang dùng trong hệ thống).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('lms.exam.manage',            'Quản trị Kho đề (vượt rào lớp)',           'LMS',      'UC-40 — quản trị viên, không cần được phân công dạy lớp'),
('lms.exercise-report.manage', 'Quản trị thống kê BTVN (vượt rào lớp)',    'LMS',      'UC-66/FR-ACA-07 — quản trị viên, không cần được phân công dạy lớp'),
('academic.comment.manage',    'Quản trị nhận xét học viên (vượt rào lớp)','ACADEMIC', 'UC-21/22 — quản trị viên, không cần được phân công dạy lớp'),
('lms.review-video.manage',    'Quản trị video ôn tập (vượt rào lớp)',     'LMS',      'UC-40 mở rộng — quản trị viên, không cần được phân công dạy lớp');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN ('lms.exam.manage', 'lms.exercise-report.manage', 'academic.comment.manage', 'lms.review-video.manage')
  AND r.code IN ('HEAD_ACADEMIC', 'SYS_ADMIN');

-- Sửa lỗ hổng V106: SUPER_ADMIN không tồn tại trong DB này, gán bổ sung SYS_ADMIN.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.teaching-plan.manage' AND r.code = 'SYS_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SYS_ADMIN đang thiếu lms.review-video.assign (chặn ngay ở @PreAuthorize
-- trước khi tới được Service) — bổ sung để nhất quán với lms.review-video.create/update.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.review-video.assign' AND r.code = 'SYS_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
