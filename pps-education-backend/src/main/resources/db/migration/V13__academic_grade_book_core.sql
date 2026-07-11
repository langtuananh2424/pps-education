-- =====================================================================
-- V13: PHAN HE 6 - SO DIEM (UC-19 FR-ACA-03 Nhap diem, UC-20 FR-ACA-03 Duyet diem)
-- Pham vi phien nay: grade_periods/grade_components/grade_entries. CHUA
-- lam grade_final_summaries (chot diem tong ket) - khong co UC nao trong
-- docs/uc/phan-he-06-hoc-thuat.md mo ta luong "chot diem", de lai khi co
-- UC ro rang hon.
-- =====================================================================

-- a) grade_periods -- Ky danh gia (VD Giua ky 1, Cuoi ky 1)
CREATE TABLE grade_periods (
    id                BIGSERIAL PRIMARY KEY,
    curriculum_id     BIGINT NOT NULL REFERENCES curriculums(id),
    code              VARCHAR(50) NOT NULL, -- MID_1 / END_1 / MID_2 / END_2 / OTHER
    name              VARCHAR(200) NOT NULL,
    display_order     INT NOT NULL DEFAULT 0,
    weight_in_final   DECIMAL(5,2) NOT NULL,
    start_date        DATE NULL,
    end_date          DATE NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / ARCHIVED
    UNIQUE(curriculum_id, code)
);

-- b) grade_components -- Thanh phan diem trong ky
CREATE TABLE grade_components (
    id                 BIGSERIAL PRIMARY KEY,
    grade_period_id    BIGINT NOT NULL REFERENCES grade_periods(id),
    subject_id         BIGINT NULL REFERENCES curriculum_subjects(id),
    code               VARCHAR(50) NOT NULL, -- SPEAKING / WRITING / LISTENING / READING / GRAMMAR / PROJECT / OTHER
    name               VARCHAR(200) NOT NULL,
    weight_in_period   DECIMAL(5,2) NOT NULL,
    max_score          DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    pass_threshold     DECIMAL(5,2) NULL,
    display_order      INT NOT NULL DEFAULT 0,
    UNIQUE(grade_period_id, code)
);

-- c) grade_entries -- Diem cu the cua hoc sinh (UC-19/UC-20)
CREATE TABLE grade_entries (
    id                    BIGSERIAL PRIMARY KEY,
    uuid                  UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    class_id              BIGINT NOT NULL REFERENCES classes(id),
    student_id            BIGINT NOT NULL REFERENCES students(id),
    grade_component_id    BIGINT NOT NULL REFERENCES grade_components(id),
    score                 DECIMAL(5,2) NOT NULL,
    absence_flag          BOOLEAN NOT NULL DEFAULT FALSE,
    teacher_note          TEXT NULL,
    entered_by            BIGINT NOT NULL REFERENCES users(id),
    entered_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PENDING / APPROVED / REJECTED
    approval_flow_id      BIGINT NULL REFERENCES approval_flows(id),
    submitted_at          TIMESTAMPTZ NULL,
    approved_at           TIMESTAMPTZ NULL,
    approved_by           BIGINT NULL REFERENCES users(id),
    UNIQUE(class_id, student_id, grade_component_id)
);
CREATE INDEX idx_grade_entries_class_student ON grade_entries(class_id, student_id);
CREATE INDEX idx_grade_entries_pending ON grade_entries(status) WHERE status = 'PENDING';

-- d) *_history -- SDD chi ghi "Co xxx_history" khong dinh nghia cot -
-- ap dung lai dung pattern JSONB diff-log da dung o cac bang khac.
CREATE TABLE grade_periods_history (
    id               BIGSERIAL PRIMARY KEY,
    grade_period_id  BIGINT NOT NULL REFERENCES grade_periods(id),
    changed_by       BIGINT NOT NULL REFERENCES users(id),
    action           VARCHAR(20) NOT NULL,
    details          JSONB NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grade_periods_history_period ON grade_periods_history(grade_period_id);

CREATE TABLE grade_components_history (
    id                  BIGSERIAL PRIMARY KEY,
    grade_component_id  BIGINT NOT NULL REFERENCES grade_components(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    action              VARCHAR(20) NOT NULL,
    details             JSONB NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grade_components_history_component ON grade_components_history(grade_component_id);

CREATE TABLE grade_entries_history (
    id               BIGSERIAL PRIMARY KEY,
    grade_entry_id   BIGINT NOT NULL REFERENCES grade_entries(id),
    changed_by       BIGINT NOT NULL REFERENCES users(id),
    action           VARCHAR(20) NOT NULL,
    details          JSONB NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grade_entries_history_entry ON grade_entries_history(grade_entry_id);
