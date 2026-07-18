-- =====================================================================
-- V21: PHAN HE 7 - KE HOACH GIANG DAY (UC-28 FR-LMS-08)
-- =====================================================================

-- a) teaching_plans -- Ke hoach giang day (tuan/nam)
CREATE TABLE teaching_plans (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    class_id            BIGINT NOT NULL REFERENCES classes(id),
    teacher_user_id     BIGINT NOT NULL REFERENCES users(id),
    plan_type           VARCHAR(20) NOT NULL, -- WEEKLY / YEARLY
    academic_year       VARCHAR(20) NULL, -- cho YEARLY
    week_number         INT NULL, -- cho WEEKLY
    week_start_date     DATE NULL,
    week_end_date       DATE NULL,
    summary             TEXT NULL,
    objectives          TEXT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PUBLISHED
    visible_to_partner  BOOLEAN NOT NULL DEFAULT TRUE,
    published_at        TIMESTAMPTZ NULL,
    CONSTRAINT chk_plan_period CHECK (
        (plan_type = 'WEEKLY' AND week_start_date IS NOT NULL AND week_end_date IS NOT NULL) OR
        (plan_type = 'YEARLY' AND academic_year IS NOT NULL)
    )
);
CREATE INDEX idx_teaching_plans_class ON teaching_plans(class_id);

-- b) teaching_plan_items -- Chi tiet ke hoach (khong history)
CREATE TABLE teaching_plan_items (
    id                  BIGSERIAL PRIMARY KEY,
    teaching_plan_id    BIGINT NOT NULL REFERENCES teaching_plans(id),
    item_order          INT NOT NULL,
    planned_date        DATE NULL,
    topic               VARCHAR(500) NOT NULL,
    objectives          TEXT NULL,
    content_outline     TEXT NULL,
    skills_focus        VARCHAR(200) NULL,
    homework_note       TEXT NULL,
    class_session_id    BIGINT NULL REFERENCES class_sessions(id)
);
CREATE INDEX idx_teaching_plan_items_plan ON teaching_plan_items(teaching_plan_id);

-- c) teaching_plans_history -- SDD chi ghi "Co teaching_plans_history"
-- khong dinh nghia cot - ap dung lai dung pattern JSONB diff-log.
CREATE TABLE teaching_plans_history (
    id                  BIGSERIAL PRIMARY KEY,
    teaching_plan_id    BIGINT NOT NULL REFERENCES teaching_plans(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    action              VARCHAR(20) NOT NULL,
    details             JSONB NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_teaching_plans_history_plan ON teaching_plans_history(teaching_plan_id);
