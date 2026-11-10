-- =====================================================================
-- V226: Phuong phap "Nghe ep" (bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-11-09).
-- Xem muc "Bo sung V226" trong docs/uc/phan-he-07-lms-portal.md va muc i-bis-2 trong
-- docs/sdd-groups/09-lms-and-portal.md.
--
-- 1 Bai nghe (exercises.forced_listening = TRUE) chua 1..12 bai tap con (exercise_sections), moi bai tap con
-- co muc can dat rieng va duoc cham rieng; bai nao chua dat thi phai lam lai cho toi khi dat.
-- Tapescript mo dan theo SO LUOT CHUA DAT cua tung bai tap con (exercises.tapescript_first_unlock_attempts,
-- tapescript_unlock_step), moi cau co tapescript rieng (questions.tapescript).
-- Gioi han toi da 12 bai tap con/Bai va viec section_id bat buoc voi Bai nghe ep duoc validate o Service.
-- =====================================================================

ALTER TABLE exercises
    ADD COLUMN forced_listening BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN tapescript_first_unlock_attempts INTEGER NOT NULL DEFAULT 3 CHECK (tapescript_first_unlock_attempts >= 1),
    ADD COLUMN tapescript_unlock_step INTEGER NOT NULL DEFAULT 2 CHECK (tapescript_unlock_step >= 1);

ALTER TABLE questions
    ADD COLUMN tapescript TEXT;

CREATE TABLE exercise_sections (
    id                     BIGSERIAL PRIMARY KEY,
    exercise_id            BIGINT       NOT NULL REFERENCES exercises(id),
    title                  VARCHAR(255) NOT NULL,
    display_order          INTEGER      NOT NULL,
    -- NULL = dung exercises.pass_threshold_percent
    pass_threshold_percent DECIMAL(5,2) CHECK (pass_threshold_percent IS NULL OR (pass_threshold_percent > 0 AND pass_threshold_percent <= 100)),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (exercise_id, display_order)
);

ALTER TABLE exercise_questions
    ADD COLUMN section_id BIGINT REFERENCES exercise_sections(id);
CREATE INDEX idx_exercise_questions_section ON exercise_questions(section_id);

CREATE TABLE exercise_section_results (
    id                     BIGSERIAL PRIMARY KEY,
    exercise_assignment_id BIGINT      NOT NULL REFERENCES exercise_assignments(id),
    student_id             BIGINT      NOT NULL REFERENCES students(id),
    exercise_section_id    BIGINT      NOT NULL REFERENCES exercise_sections(id),
    -- Luot nop gan nhat ma bai duoc mo, chi de hien thi.
    latest_score_percent   DECIMAL(5,2),
    -- NULL den khi DAT lan dau; sau do = max cac luot co diem >= muc can dat, khong bao gio giam.
    best_score_percent     DECIMAL(5,2),
    passed                 BOOLEAN     NOT NULL DEFAULT FALSE,
    -- So luot nop bai mo lai, chua tung dat, co tra loi >= 1 cau, diem < muc can dat (nop trang khong tinh).
    failed_attempts_count  INTEGER     NOT NULL DEFAULT 0,
    -- Cac cau da duoc mo tapescript, giu vinh vien.
    unlocked_question_ids  JSONB       NOT NULL DEFAULT '[]'::jsonb,
    first_passed_at        TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (exercise_assignment_id, student_id, exercise_section_id)
);
CREATE INDEX idx_exercise_section_results_student ON exercise_section_results(student_id);
