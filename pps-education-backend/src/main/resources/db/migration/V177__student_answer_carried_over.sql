-- =====================================================================
-- V177: UC-24/UC-27 "Lam lai" (retake) chi can lam lai CAU SAI, cau da
-- dung o luot truoc duoc mang qua luot moi (carry-forward) va khoa lai,
-- khong phai lam lai toan bo de nhu truoc.
--
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-15. Xem
-- ExerciseAttemptService#startAttempt (doan copy StudentAnswer da dung
-- tu lastAttempt) va saveAnswer (chan sua len cau da carry-forward).
-- =====================================================================

ALTER TABLE student_answers
    ADD COLUMN carried_over_from_previous_attempt BOOLEAN NOT NULL DEFAULT FALSE;
