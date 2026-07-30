-- =====================================================================
-- V64: Bo sung permission "Xem/chon moi lop hoc" (academic.class.view-all)
-- - bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-07-30.
--
-- Boi canh: pill "Lop" o Header (va cac trang dua vao selectedClassId dung
-- chung o Header nhu Sổ diem UC-19/20, Diem danh UC-15, Nhan xet UC-21/22,
-- Soan & giao de UC-40) chi hien/tra ve day du danh sach lop cho tai khoan
-- THAT SU dung ten trong class_teachers hoac site_managers - khong dua vao
-- quyen 'academic.class.manage' (UC-18, "Xep lop & gan khoa hoc") vi
-- permission do von mang y nghia khac (thao tac xep lop), khong phai "duoc
-- xem moi lop" - tranh lap lai dung 1 permission cho 2 muc dich khac nhau
-- (da tung gay nham lan: tai khoan demo "Super Admin" co quyen
-- academic.class.manage de test UC-18 nhung khong nen vi the ma thay het
-- moi lop o cac man khac).
--
-- Permission MOI, doc lap voi academic.class.manage - gan cho HEAD_ACADEMIC
-- (Truong phong dao tao) va SYS_ADMIN (Quan tri vien), dung theo yeu cau
-- nguoi dung: "them 1 quyen nua de co the truy cap duoc vao tat ca cac lop
-- - quyen do thuong se duoc su dung cho truong phong dao tao hoac quan tri
-- vien". Dung o ca backend (ClassService.resolveAllowedSiteIds - bo gioi
-- han site) lan frontend (Header.tsx showClassSelector,
-- useEligibleClasses.ts canSeeAllClasses).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('academic.class.view-all', 'Xem/chọn mọi lớp học (không giới hạn theo phân công)', 'ACADEMIC',
 'Bổ sung ngoài SDD gốc — cho phép Trưởng phòng đào tạo/Quản trị viên xem và chọn bất kỳ lớp nào ở các màn dùng chung selectedClassId (Sổ điểm, Điểm danh, Nhận xét, Soạn & giao đề...) mà không cần đứng tên class_teachers/site_managers');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.class.view-all' AND r.code IN ('HEAD_ACADEMIC', 'SYS_ADMIN');
