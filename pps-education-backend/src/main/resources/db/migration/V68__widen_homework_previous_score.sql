-- Fix bug phat hien khi verify Kho de (2026-07-30, ngoai pham vi UC-40 nhung
-- chan luong xac nhan): HomeworkProgressService.grammarProgressLabel/
-- videoProgressLabel co the tra ve "Chua lam bai" (12 ky tu) / "Dang cho
-- cham" (13 ky tu) lam gia tri mac dinh cho cot homework_previous_score /
-- homework_previous_speaking_score khi giao vien chua tu nhap tay (xem
-- StudentCommentService.resolvedGrammarPrevious/resolvedSpeakingPrevious).
-- Cot dang VARCHAR(10) (thiet ke ban dau chi danh cho diem ngan kieu
-- "80%") nen bi loi "value too long for type character varying(10)" khi
-- import lai chinh gia tri mac dinh do template tu dien. Noi rong de chua
-- duoc ca 2 nhan chu + diem % trong tuong lai.
ALTER TABLE student_comments ALTER COLUMN homework_previous_score TYPE VARCHAR(30);
ALTER TABLE student_comments ALTER COLUMN homework_previous_speaking_score TYPE VARCHAR(30);
