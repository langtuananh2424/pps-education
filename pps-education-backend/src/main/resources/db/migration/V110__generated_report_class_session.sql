-- =====================================================================
-- V110: PHAN HE 6 - XUAT BAO CAO THEO BUOI HOC (UC-68, FR-ACA-08). Bo
-- sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-09.
--
-- Them scope moi CLASS_SESSION cho generated_reports (Java enum, khong
-- can CHECK constraint - cot scope van la VARCHAR tu do o V109): mot so
-- loai bao cao (VD DAILY_REPORT - co bang dong liet ke hoc sinh) sinh ra
-- DUNG 1 tai lieu cho ca buoi hoc, khong phai 1 tai lieu/hoc sinh - can
-- tham chieu rieng toi class_sessions, khac voi student_id/class_id da co.
-- =====================================================================

ALTER TABLE generated_reports ADD COLUMN class_session_id BIGINT NULL REFERENCES class_sessions(id);
CREATE INDEX idx_generated_reports_class_session ON generated_reports(class_session_id, generated_at DESC);
