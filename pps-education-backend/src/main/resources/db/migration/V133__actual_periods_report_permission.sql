-- =====================================================================
-- V130: Permission cho bao cao "So tiet thuc te theo lop" (bo sung ngoai
-- SDD goc, da xac nhan voi nguoi dung 2026-08-20). Theo dung pattern da
-- dung o V114 (report.enrollment-stats.view) - 1 permission duy nhat,
-- SITE_MANAGER bi gioi han theo site_managers o Service
-- (ActualPeriodsReportService), SYS_ADMIN/HEAD_ACADEMIC khong co row
-- site_managers nen khong bi gioi han.
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('report.actual-periods.view', 'Xem thong ke so tiet thuc te theo lop', 'ACADEMIC', 'Bo sung ngoai SDD goc, xac nhan 2026-08-20');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('SYS_ADMIN', 'HEAD_ACADEMIC', 'SITE_MANAGER')
  AND p.code = 'report.actual-periods.view';
