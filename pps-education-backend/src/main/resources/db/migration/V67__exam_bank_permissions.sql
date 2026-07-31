-- =====================================================================
-- V67: Bo sung permission cho ExamController (Kho de 2 cap De/Bai,
-- UC-40 - bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-07-30).
-- Chuc nang hoan toan moi, khong phai tach tu 1 bundled permission cu
-- (khac V62) nen khong can backfill user_permission_overrides.
--
-- Chi gan TEACHER, dung tien le V62/V63: lms.exercise.*/lms.review-video.*
-- deu chi gan TEACHER, khong gan them SYS_ADMIN/role khac.
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('lms.exam.create', 'Tạo Đề (Kho đề)',       'LMS', 'UC-40'),
('lms.exam.update', 'Sửa Đề',                 'LMS', 'UC-40'),
('lms.exam.assign', 'Gán/gỡ Đề cho lớp',      'LMS', 'UC-40');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN ('lms.exam.create', 'lms.exam.update', 'lms.exam.assign')
  AND r.code = 'TEACHER';
