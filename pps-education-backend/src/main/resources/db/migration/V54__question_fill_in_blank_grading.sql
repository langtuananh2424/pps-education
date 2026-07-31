-- =====================================================================
-- V54: TU DONG CHAM CAU HOI DIEN TU (FILL_IN_BLANK) - UC-27/UC-40
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-07-27.
--
-- SDD hien co questions (student_answers.answer_text danh cho FILL_IN_BLANK/
-- ESSAY) nhung KHONG co cot dap an dung tuong ung tren questions de so
-- khop tu dong - truoc day FILL_IN_BLANK luon roi vao hang cho Giao vien
-- cham tay (UC-41), giong ESSAY/SPEAKING.
--
-- correct_answer_text NULL = khong ap dung (khong phai FILL_IN_BLANK hoac
-- Giao vien chua nhap). So khop CHINH XAC (khong mo/gan dung), case-
-- insensitive + trim, thuc hien o tang Service
-- (ExerciseAttemptService.isAnswerCorrect) - khong dat CHECK theo
-- question_type vi chi mang tinh phong ve, khong chan cung du lieu cu.
-- =====================================================================

ALTER TABLE questions ADD COLUMN correct_answer_text TEXT NULL;
