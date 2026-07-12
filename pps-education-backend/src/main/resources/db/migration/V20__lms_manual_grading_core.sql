-- =====================================================================
-- V20: PHAN HE 7 - CHAM BAI THU CONG (UC-41 FR-LMS-11)
-- =====================================================================

-- a) student_answer_grading -- GV cham tu luan/noi. Khong history, sua
-- diem tao record moi (SDD) - cot is_final danh dau ban ghi hien hanh.
CREATE TABLE student_answer_grading (
    id                  BIGSERIAL PRIMARY KEY,
    student_answer_id   BIGINT NOT NULL REFERENCES student_answers(id),
    grader_user_id      BIGINT NOT NULL REFERENCES users(id),
    score               DECIMAL(5,2) NOT NULL,
    max_score           DECIMAL(5,2) NOT NULL,
    feedback            TEXT NULL,
    graded_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_final            BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_student_answer_grading_answer ON student_answer_grading(student_answer_id);
CREATE UNIQUE INDEX idx_student_answer_grading_current ON student_answer_grading(student_answer_id) WHERE is_final = TRUE;
