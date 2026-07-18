-- =====================================================================
-- V18: PHAN HE 7 - DE ON TAP / GIAO DE (UC-40 FR-LMS-10)
-- =====================================================================

-- a) exercises -- De on tap / bai tap
CREATE TABLE exercises (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code                    VARCHAR(50) UNIQUE NOT NULL,
    title                   VARCHAR(500) NOT NULL,
    curriculum_id           BIGINT NULL REFERENCES curriculums(id),
    subject_id              BIGINT NULL REFERENCES curriculum_subjects(id),
    exercise_type           VARCHAR(30) NOT NULL, -- SELF_PRACTICE / ASSIGNED / MOCK_TEST / SKILL_PRACTICE
    total_points            DECIMAL(6,2) NOT NULL,
    time_limit_minutes      INT NULL, -- NULL = khong gioi han
    allow_retake            BOOLEAN NOT NULL DEFAULT TRUE,
    max_attempts            INT NULL,
    show_correct_answers    BOOLEAN NOT NULL DEFAULT TRUE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PUBLISHED / ARCHIVED
    created_by              BIGINT NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- b) exercise_questions -- Cau hoi thuoc de
CREATE TABLE exercise_questions (
    id                BIGSERIAL PRIMARY KEY,
    exercise_id       BIGINT NOT NULL REFERENCES exercises(id),
    question_id       BIGINT NOT NULL REFERENCES questions(id),
    display_order     INT NOT NULL,
    points            DECIMAL(5,2) NOT NULL,
    UNIQUE(exercise_id, question_id)
);

-- c) exercise_assignments -- Giao de cho lop (ASSIGNED, deadline)
CREATE TABLE exercise_assignments (
    id                          BIGSERIAL PRIMARY KEY,
    uuid                        UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    exercise_id                 BIGINT NOT NULL REFERENCES exercises(id),
    class_id                    BIGINT NOT NULL REFERENCES classes(id),
    assigned_by                 BIGINT NOT NULL REFERENCES users(id),
    available_from              TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at                      TIMESTAMPTZ NULL,
    late_submission_allowed     BOOLEAN NOT NULL DEFAULT FALSE,
    late_penalty_percent        DECIMAL(5,2) NULL,
    target_student_ids          JSONB NULL, -- NULL = ca lop
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' -- ACTIVE / CANCELLED / COMPLETED
);
CREATE INDEX idx_exercise_assignments_class ON exercise_assignments(class_id);
