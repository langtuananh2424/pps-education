-- =====================================================================
-- V165: Cho phep nop bai muon - BTVN Video On tap (REFLEX/CONNECTION)
-- Bo sung ngoai SDD goc, dao nguoc quyet dinh 2026-07-30 (V65 - "khong
-- ap dung cho video"), da xac nhan lai voi nguoi dung 2026-09-07.
--
-- Mirror exercise_assignments.late_submission_allowed (V18): giao vien
-- co the cho phep 1 ban giao Video On tap duoc nop sau han - hoc sinh
-- van nop duoc, he thong danh dau "nop muon" de giao vien biet, KHONG
-- tru diem. Xem ReviewVideoService/ReflexSequentialGradingService.
-- =====================================================================

ALTER TABLE review_video_assignments
    ADD COLUMN late_submission_allowed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE reflex_question_progress
    ADD COLUMN is_late_submission BOOLEAN NOT NULL DEFAULT FALSE;

-- Luu lua chon "cho phep nop muon" CHUA giao (con DRAFT/REJECTED) o Nhan xet hoc vien - mirror
-- pending_homework_next_due_date (StudentComment).
ALTER TABLE student_comments
    ADD COLUMN pending_homework_next_late_submission_allowed BOOLEAN;
