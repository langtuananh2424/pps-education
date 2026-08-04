-- =====================================================================
-- V77: PHAN HE 6 - LUONG DUYET/TU CHOI DIEM THAY THE "CONG BO DU KIEN +
-- PHUC KHAO" (UC-19/UC-20 sua doi lan 3 - V44, bo UC-62). BO SUNG NGOAI
-- SDD GOC - da xac nhan voi nguoi dung (xem docs/uc/phan-he-06-hoc-thuat.md
-- UC-19/UC-20). Luong moi: DRAFT -> SUBMITTED (GV gui duyet) -> OFFICIAL
-- (Quan ly duyet, hien thi ngay cho Phu huynh) / REJECTED (Quan ly tu
-- choi, GV hoac Quan ly sua roi gui/duyet lai). Khong con "cong bo du
-- kien" va khong con "phuc khao" - moi diem chi hien thi cho Phu huynh
-- khi da OFFICIAL (duyet).
-- =====================================================================

-- a) Cot status van la VARCHAR(30) (du cho SUBMITTED/REJECTED, da doi
-- rong o V43). Doi du lieu cu (neu co, moi trieu khai chua co du lieu
-- that) ve DRAFT - khong con PROVISIONAL_PUBLISHED/APPEAL trong luong moi.
UPDATE grade_entries SET status = 'DRAFT' WHERE status IN ('PROVISIONAL_PUBLISHED', 'APPEAL');
UPDATE grade_period_results SET status = 'DRAFT' WHERE status IN ('PROVISIONAL_PUBLISHED', 'APPEAL');

COMMENT ON TABLE grade_entries IS 'V44: Luong 4 trang thai - DRAFT -> SUBMITTED -> OFFICIAL/REJECTED';
COMMENT ON TABLE grade_period_results IS 'V44: Luong 4 trang thai - DRAFT -> SUBMITTED -> OFFICIAL/REJECTED';

-- b) Doi ten permission publish -> approve (dung nhu ten V38 ban dau,
-- truoc khi V39 doi thanh publish). Giu nguyen id, khong dut lien ket
-- role_permissions da gan (SITE_MANAGER, HEAD_ACADEMIC).
UPDATE permissions SET
    code = 'academic.grade.approve',
    name = 'Duyet/Tu choi diem hoc phan',
    description = 'FR-ACA-03 UC-20 (duyet/tu choi)'
WHERE code = 'academic.grade.publish';

-- c) Bo UC-62 Phuc khao - da xac nhan voi nguoi dung: xoa bang
-- grade_appeal_requests va setting han Y ngay phuc khao (chi phuc vu
-- UC-62, khong con dung). Giu nguyen grade_period_edit_windows +
-- academic.grade_edit_window_days (X ngay) - hien la moc "lan dau nhap"
-- mang tinh thong tin, khong con gan voi job tu dong nao.
DROP TABLE IF EXISTS grade_appeal_requests;

DELETE FROM system_settings WHERE setting_key = 'academic.grade_appeal_window_days';
