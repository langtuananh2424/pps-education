-- =====================================================================
-- V19: PHAN HE 7 - LUOT LAM BAI (nen tang cho UC-24/UC-26/UC-27 FR-LMS-02/04/06)
--
-- Tao truoc trong cung dot voi UC-40 (chi schema, chua co Service/Controller
-- doc/ghi UC-24/26/27 - se lam o batch ke tiep) vi student_answers can
-- ton tai de UC-40 (questions) thuc hien dung quy tac SDD "cau hoi da co
-- student_answers thi khong cho sua noi dung/dap an dung".
-- =====================================================================

-- a) exercise_attempts -- Luot hoc sinh lam bai
CREATE TABLE exercise_attempts (
    id                          BIGSERIAL PRIMARY KEY,
    uuid                        UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    exercise_id                 BIGINT NOT NULL REFERENCES exercises(id),
    exercise_assignment_id      BIGINT NULL REFERENCES exercise_assignments(id), -- NULL cho SELF_PRACTICE
    student_id                  BIGINT NOT NULL REFERENCES students(id),
    attempt_number              INT NOT NULL DEFAULT 1,
    started_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at                TIMESTAMPTZ NULL, -- NULL = dang lam do
    auto_grade_score            DECIMAL(6,2) NULL, -- trac nghiem
    manual_grade_score          DECIMAL(6,2) NULL, -- tu luan + noi
    total_score                 DECIMAL(6,2) NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS / SUBMITTED / AUTO_GRADED / FULLY_GRADED / EXPIRED
    is_late_submission           BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_exercise_attempts_student ON exercise_attempts(student_id);
CREATE INDEX idx_exercise_attempts_assignment ON exercise_attempts(exercise_assignment_id);

-- b) student_answers -- Cau tra loi tung cau
CREATE TABLE student_answers (
    id                      BIGSERIAL PRIMARY KEY,
    exercise_attempt_id     BIGINT NOT NULL REFERENCES exercise_attempts(id),
    question_id             BIGINT NOT NULL REFERENCES questions(id),
    answer_text             TEXT NULL, -- FILL_IN_BLANK, ESSAY
    selected_choice_ids     JSONB NULL, -- MULTIPLE_CHOICE/MULTIPLE_ANSWER
    audio_answer_url        VARCHAR(1000) NULL, -- SPEAKING - audio HS upload
    is_auto_gradable        BOOLEAN NOT NULL,
    auto_score               DECIMAL(5,2) NULL,
    is_correct               BOOLEAN NULL,
    UNIQUE(exercise_attempt_id, question_id)
);
CREATE INDEX idx_student_answers_question ON student_answers(question_id);

-- c) exercise_attempts_history -- SDD chi ghi "Co exercise_attempts_history"
-- khong dinh nghia cot - ap dung lai dung pattern JSONB diff-log.
CREATE TABLE exercise_attempts_history (
    id                     BIGSERIAL PRIMARY KEY,
    exercise_attempt_id    BIGINT NOT NULL REFERENCES exercise_attempts(id),
    changed_by             BIGINT NOT NULL REFERENCES users(id),
    action                 VARCHAR(20) NOT NULL,
    details                JSONB NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_exercise_attempts_history_attempt ON exercise_attempts_history(exercise_attempt_id);
