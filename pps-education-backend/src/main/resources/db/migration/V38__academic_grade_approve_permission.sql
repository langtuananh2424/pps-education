-- =====================================================================
-- V38: PHAN HE 6 - QUYEN academic.grade.approve CHO UC-20 (Duyet diem)
-- BO SUNG NGOAI SDD GOC - da xac nhan voi nguoi dung (xem
-- docs/uc/phan-he-06-hoc-thuat.md UC-20). Truoc day UC-20 khong co
-- permission code nao - chi check row-level qua site_managers (Quan ly
-- diem truong dung site). Bo sung permission nay de: (a) hinh thuc hoa
-- quyen duyet cho SITE_MANAGER (van giu nguyen gioi han theo site rieng
-- - row-level check khong doi), (b) mo rong cho HEAD_ACADEMIC duyet
-- duoc MOI site (khong gioi han - dung nhu cach academic.grade.manage
-- da global san).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
    ('academic.grade.approve', 'Duyet diem hoc phan', 'ACADEMIC', 'FR-ACA-03 UC-20');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('SITE_MANAGER', 'HEAD_ACADEMIC') AND p.code = 'academic.grade.approve';
