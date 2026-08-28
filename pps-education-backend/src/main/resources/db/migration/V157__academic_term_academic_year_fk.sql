-- =====================================================================
-- V157: THEM FK academic_terms.academic_year_id -> academic_years(id)
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-28.
--
-- Truoc day academic_terms (Ky hoc, gioi han theo diem truong - V71) va
-- academic_years (Nam hoc, danh muc dung chung toan he thong - V103) hoan
-- toan roi nhau. Nay them FK de xac dinh ro 1 ky hoc thuoc nam hoc nao -
-- quan he 1-N on dinh (khac quan he lop <-> ky co tinh KHONG dat FK).
--
-- Cot NULL o DB: du lieu ky cu backfill theo khoang ngay khong chac khop
-- (academic_years.start_date/end_date nullable). Ky tao moi bat buoc chon
-- nam hoc (validate o CreateAcademicTermRequest / AcademicTermService).
-- =====================================================================

ALTER TABLE academic_terms ADD COLUMN academic_year_id BIGINT REFERENCES academic_years(id);

-- Backfill: gan nam hoc co khoang ngay bao trum start_date cua ky. Chi
-- match khi nam hoc co ca start_date lan end_date; con lai de NULL cho
-- giao vu gan tay (cung tien le V71 de trong classes.semester cu).
UPDATE academic_terms t
SET academic_year_id = ay.id
FROM academic_years ay
WHERE ay.start_date IS NOT NULL
  AND ay.end_date IS NOT NULL
  AND t.start_date BETWEEN ay.start_date AND ay.end_date;

CREATE INDEX idx_academic_terms_academic_year ON academic_terms(academic_year_id);
