-- =====================================================================
-- V16: PHAN HE 7 - BAI GIANG & KHO HOC LIEU (UC-23 FR-LMS-01)
-- =====================================================================

-- a) lessons -- Bai giang (chung theo curriculum hoac rieng theo class)
CREATE TABLE lessons (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code                VARCHAR(50) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    curriculum_id       BIGINT NULL REFERENCES curriculums(id),
    class_id            BIGINT NULL REFERENCES classes(id),
    subject_id          BIGINT NULL REFERENCES curriculum_subjects(id),
    lesson_order        INT NULL,
    lesson_type         VARCHAR(30) NOT NULL, -- VIDEO_LECTURE / PDF_DOCUMENT / MIXED / LIVE_RECORDING
    duration_minutes    INT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PUBLISHED / ARCHIVED
    published_at        TIMESTAMPTZ NULL,
    created_by          BIGINT NOT NULL REFERENCES users(id),
    CONSTRAINT chk_lesson_scope CHECK (
        (curriculum_id IS NOT NULL AND class_id IS NULL) OR
        (curriculum_id IS NULL AND class_id IS NOT NULL)
    )
);
CREATE INDEX idx_lessons_curriculum ON lessons(curriculum_id);
CREATE INDEX idx_lessons_class ON lessons(class_id);

-- b) lesson_materials -- Tep dinh kem (khong history, sua tao ban ghi moi)
CREATE TABLE lesson_materials (
    id                  BIGSERIAL PRIMARY KEY,
    lesson_id           BIGINT NOT NULL REFERENCES lessons(id),
    material_type       VARCHAR(30) NOT NULL, -- VIDEO / PDF / AUDIO / SLIDE / IMAGE / OTHER
    title               VARCHAR(300) NOT NULL,
    file_url            VARCHAR(1000) NOT NULL,
    file_size_bytes     BIGINT NULL,
    duration_seconds    INT NULL,
    display_order       INT NOT NULL DEFAULT 0,
    is_downloadable     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_lesson_materials_lesson ON lesson_materials(lesson_id);

-- c) lessons_history -- SDD chi ghi "Co lessons_history" khong dinh nghia
-- cot - ap dung lai dung pattern JSONB diff-log.
CREATE TABLE lessons_history (
    id             BIGSERIAL PRIMARY KEY,
    lesson_id      BIGINT NOT NULL REFERENCES lessons(id),
    changed_by     BIGINT NOT NULL REFERENCES users(id),
    action         VARCHAR(20) NOT NULL,
    details        JSONB NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lessons_history_lesson ON lessons_history(lesson_id);
