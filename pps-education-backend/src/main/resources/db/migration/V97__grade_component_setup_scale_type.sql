-- =====================================================================
-- V97: THAY "trong so (%)" TREN grade_component_setups BANG "thang diem"
-- (UC-19, bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-06).
--
-- Ly do: he thong khong tinh OVERALL ca ky tu Giua ky + Cuoi ky (khong
-- can weight_in_final nua) - FE hien thi rieng Overall Giua ky VA Overall
-- Cuoi ky khi chon 1 hoc ky, khong gop lam 1 con so. Thay vao do, moi
-- setup (Giua ky/Cuoi ky) tu chon 1 thang diem rieng, moi thanh phan diem
-- (grade_evaluation_components) trong setup phai co max_score dung theo
-- thang da chon:
--   POINT_10 -> max_score = 10
--   PERCENT  -> max_score = 100
--   IELTS    -> max_score = 9 (band 1.0 - 9.0, cho phep nhap le 0.5 o
--               grade_entries.score - da la DECIMAL(5,2) tu truoc, khong
--               can doi kieu du lieu).
-- =====================================================================

ALTER TABLE grade_component_setups DROP COLUMN weight_in_final;

ALTER TABLE grade_component_setups ADD COLUMN scale_type VARCHAR(20) NOT NULL DEFAULT 'POINT_10';
ALTER TABLE grade_component_setups ADD CONSTRAINT chk_grade_component_setups_scale_type
    CHECK (scale_type IN ('POINT_10', 'PERCENT', 'IELTS'));
ALTER TABLE grade_component_setups ALTER COLUMN scale_type DROP DEFAULT;
