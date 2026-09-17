-- V183: audio_url/image_url (questions) va image_url (question_choices) tu
-- VARCHAR(1000) sang TEXT.
--
-- Ly do (fix bug that gap tren production 2026-09-17): 1 so loai cau hoi
-- HOP LE can luu NHIEU URL trong CUNG 1 truong "URL Hinh anh" (VD
-- DIEN_TU_HOP_TU_VUNG_ANH - 1 anh/1 cho trong, co the toi 10 anh noi bang
-- dau "|"; NGHE_CHON_HINH tuong tu voi audio/anh dap an) - voi URL dai (CDN
-- co token), tong do dai vuot 1000 ky tu la chuyen BINH THUONG, khong phai
-- du lieu loi. VARCHAR(1000) truoc day khien import bi tu choi hop le +
-- (truoc khi fix o QuestionImportService) con lam mat trang ca batch qua
-- UnexpectedRollbackException. Doi sang TEXT (khong gioi han) - mirror dung
-- reference_passage/explanation/correct_answer_text trong CUNG bang
-- (V17__lms_question_bank_core.sql/V54__question_fill_in_blank_grading.sql)
-- von khong co ly do nghiep vu nao can gioi han do dai ca.
ALTER TABLE questions ALTER COLUMN audio_url TYPE TEXT;
ALTER TABLE questions ALTER COLUMN image_url TYPE TEXT;
ALTER TABLE question_choices ALTER COLUMN image_url TYPE TEXT;
