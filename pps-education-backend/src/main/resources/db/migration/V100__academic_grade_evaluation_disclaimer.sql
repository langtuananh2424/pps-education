-- =====================================================================
-- V100: PHAN HE 6 - GRADE EVALUATION DISCLAIMER
-- Them cot "disclaimer" (Luu y) vao grade_evaluation_results de luu tru
-- thong tin bo sung dac thu cho moi ky danh gia (VD pham vi de thi, dieu
-- kien dieu chinh score, ...). Hien thi ở header UI nhap diem cua GV,
-- va o UI hoc sinh xem diem (GradesTab).
-- =====================================================================

ALTER TABLE grade_evaluation_results
ADD COLUMN disclaimer TEXT NULL; -- Luu y bo sung cho diem (khong xuat Excel, chi hien UI)

-- =====================================================================
-- (Gop chung V100, tranh trung version - 2 PR rieng biet cung merge V100)
-- Giam nguong dat BTVN mac dinh tu 80% xuong 70% (bo sung ngoai SDD goc,
-- da xac nhan voi nguoi dung 2026-08-07) - ap dung cho TAT CA Bai hien co
-- (chua tung co duong nao trong FE cho phep chinh khac mac dinh, nen moi
-- Bai dang o dung 80.00 deu la gia tri mac dinh cu, khong phai gia tri
-- Giao vien chu dong chon).
-- =====================================================================

ALTER TABLE exercises ALTER COLUMN pass_threshold_percent SET DEFAULT 70.00;

UPDATE exercises SET pass_threshold_percent = 70.00 WHERE pass_threshold_percent = 80.00;
