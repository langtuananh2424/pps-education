-- =====================================================================
-- V17: PHAN HE 7 - NGAN HANG CAU HOI (nen tang cho UC-40 FR-LMS-10)
-- =====================================================================

-- a) question_banks -- Ngan hang cau hoi
CREATE TABLE question_banks (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code            VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(300) NOT NULL,
    curriculum_id   BIGINT NULL REFERENCES curriculums(id),
    subject_id      BIGINT NULL REFERENCES curriculum_subjects(id),
    level           VARCHAR(50) NULL, -- A1/A2/B1/B2...
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

-- b) questions -- Cau hoi
CREATE TABLE questions (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    question_bank_id    BIGINT NOT NULL REFERENCES question_banks(id),
    question_type       VARCHAR(30) NOT NULL, -- MULTIPLE_CHOICE / MULTIPLE_ANSWER / TRUE_FALSE / FILL_IN_BLANK / ESSAY / SPEAKING
    skill               VARCHAR(20) NULL, -- LISTENING / READING / WRITING / SPEAKING / GRAMMAR / OTHER
    difficulty          VARCHAR(20) NULL, -- EASY / MEDIUM / HARD
    content             TEXT NOT NULL,
    audio_url           VARCHAR(1000) NULL,
    image_url           VARCHAR(1000) NULL,
    reference_passage   TEXT NULL,
    explanation         TEXT NULL,
    default_points      DECIMAL(5,2) NOT NULL DEFAULT 1.0,
    tags                JSONB NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / ARCHIVED
    created_by          BIGINT NOT NULL REFERENCES users(id)
);
CREATE INDEX idx_questions_bank ON questions(question_bank_id);

-- c) question_choices -- Dap an trac nghiem
CREATE TABLE question_choices (
    id              BIGSERIAL PRIMARY KEY,
    question_id     BIGINT NOT NULL REFERENCES questions(id),
    choice_label    VARCHAR(10) NOT NULL, -- A/B/C/D hoac TRUE/FALSE
    content         TEXT NOT NULL,
    is_correct      BOOLEAN NOT NULL DEFAULT FALSE,
    display_order   INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_question_choices_question ON question_choices(question_id);

-- d) questions_history -- SDD chi ghi "Co questions_history" khong dinh
-- nghia cot - ap dung lai dung pattern JSONB diff-log.
CREATE TABLE questions_history (
    id             BIGSERIAL PRIMARY KEY,
    question_id    BIGINT NOT NULL REFERENCES questions(id),
    changed_by     BIGINT NOT NULL REFERENCES users(id),
    action         VARCHAR(20) NOT NULL,
    details        JSONB NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_questions_history_question ON questions_history(question_id);
