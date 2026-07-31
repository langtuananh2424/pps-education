-- Fix bug: "Da nop bai" hien sai cho lan giao moi (bao cao 2026-07-31, ngoai
-- pham vi 1 UC cu the, backend tu quyet dinh huong sua theo de xuat cua
-- nguoi dung). Truoc day review_video_question_submissions chi gan voi
-- reviewVideoQuestionId + studentId, khong biet gi ve LAN GIAO
-- (review_video_assignments) nao da cap quyen nop -> khi giao lai dung 1
-- bo REFLEX cho cung 1 lop (VD buoi 7 sau khi da giao o buoi truoc), he
-- thong van thay "da co cau tra loi cu" -> hien nham "Da nop bai".
--
-- NULL cho du lieu cu truoc V69 (khong xac dinh duoc dung lan giao nao) --
-- co tinh KHONG backfill, vi 1 lan giao lai la "lam lai tu dau" (attempt
-- lai bat dau tu 1), ung xu dung dan la coi du lieu cu la "khong thuoc ve
-- lan giao hien tai nao" thay vi doan mo ho.
ALTER TABLE review_video_question_submissions
    ADD COLUMN review_video_assignment_id BIGINT NULL REFERENCES review_video_assignments(id);
CREATE INDEX idx_review_video_qsub_assignment ON review_video_question_submissions(review_video_assignment_id);

-- Doi UNIQUE(question,student,attempt_number) thanh co them assignment_id
-- de attempt_number duoc phep bat dau lai tu 1 o moi lan giao moi (khong
-- dam vao lich su lan giao truoc, kha thi vi NULL khong dung do trong
-- UNIQUE constraint cua Postgres).
ALTER TABLE review_video_question_submissions
    DROP CONSTRAINT review_video_question_submiss_review_video_question_id_stud_key;
ALTER TABLE review_video_question_submissions
    ADD CONSTRAINT review_video_qsub_question_student_assignment_attempt_key
    UNIQUE (review_video_question_id, student_id, review_video_assignment_id, attempt_number);
