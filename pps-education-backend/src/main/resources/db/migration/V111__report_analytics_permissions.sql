-- =====================================================================
-- V111: Them permissions cho cac bao cao & thong ke moi (Phan he 11)
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('report.daily-comment.view', 'Xem thong ke nhan xet ngay', 'ACADEMIC', 'Quyen xem bieu do va danh sach nhan xet ngay'),
('report.grade.view', 'Xem thong ke diem so', 'ACADEMIC', 'Quyen xem pho diem va diem trung binh'),
('report.student-progress.view', 'Xem ho so qua trinh hoc tap', 'ACADEMIC', 'Quyen xem ho so tong hop cua hoc sinh');

-- Cap quyen cho cac role quan tri (SYS_ADMIN, HEAD_ACADEMIC, SITE_MANAGER)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('SYS_ADMIN', 'HEAD_ACADEMIC', 'SITE_MANAGER')
  AND p.code IN ('report.daily-comment.view', 'report.grade.view', 'report.student-progress.view');
