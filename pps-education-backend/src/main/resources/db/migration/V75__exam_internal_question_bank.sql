-- UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04):
-- mỗi Đề (exams) sở hữu đúng 1 Ngân hàng câu hỏi nội bộ. Giáo viên chỉ
-- thao tác theo exam_id; question_bank_id là chi tiết lưu trữ không hiển thị.
--
-- Backfill CHỈ tạo bank mới + link Exam cũ. Không đổi/xóa/clone bank, câu
-- hỏi, exercise_questions hay student_answers legacy.
ALTER TABLE exams
    ADD COLUMN question_bank_id BIGINT NULL REFERENCES question_banks(id);

INSERT INTO question_banks (code, name, curriculum_id, subject_id, level, is_active)
SELECT 'EXAM-' || e.uuid,
       'Câu hỏi nội bộ - ' || e.code,
       e.curriculum_id,
       NULL,
       NULL,
       TRUE
FROM exams e;

UPDATE exams e
SET question_bank_id = qb.id
FROM question_banks qb
WHERE qb.code = 'EXAM-' || e.uuid;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM exams WHERE question_bank_id IS NULL) THEN
        RAISE EXCEPTION 'Không thể tạo ngân hàng câu hỏi nội bộ cho tất cả Đề';
    END IF;
END $$;

ALTER TABLE exams
    ALTER COLUMN question_bank_id SET NOT NULL,
    ADD CONSTRAINT uq_exams_question_bank_id UNIQUE (question_bank_id);
