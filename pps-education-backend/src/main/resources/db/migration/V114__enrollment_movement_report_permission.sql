-- =====================================================================
-- V114: Permission cho UC-69 - Thong ke bien dong hoc sinh cac lop theo ky
-- (FR-ACA-09, bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-11)
-- Theo dung pattern da dung o V111 (report.daily-comment.view/report.grade.view/
-- report.student-progress.view) - 1 permission duy nhat, khong tach view/view-all;
-- SITE_MANAGER bi gioi han theo site_managers o Service (EnrollmentMovementReportService),
-- SYS_ADMIN/HEAD_ACADEMIC khong co row site_managers nen khong bi gioi han.
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('report.enrollment-stats.view', 'Xem thong ke bien dong hoc sinh cac lop theo ky', 'ACADEMIC', 'UC-69/FR-ACA-09');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('SYS_ADMIN', 'HEAD_ACADEMIC', 'SITE_MANAGER')
  AND p.code = 'report.enrollment-stats.view';
