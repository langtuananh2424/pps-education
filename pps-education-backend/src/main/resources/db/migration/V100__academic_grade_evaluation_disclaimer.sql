-- =====================================================================
-- V100: PHAN HE 6 - GRADE EVALUATION DISCLAIMER
-- Them cot "disclaimer" (Luu y) vao grade_evaluation_results de luu tru
-- thong tin bo sung dac thu cho moi ky danh gia (VD pham vi de thi, dieu
-- kien dieu chinh score, ...). Hien thi ở header UI nhap diem cua GV,
-- va o UI hoc sinh xem diem (GradesTab).
-- =====================================================================

ALTER TABLE grade_evaluation_results
ADD COLUMN disclaimer TEXT NULL; -- Luu y bo sung cho diem (khong xuat Excel, chi hien UI)
