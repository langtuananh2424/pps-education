-- =====================================================================
-- V12: PHAN HE 6 - KHUNG CHUONG TRINH & LOP HOC (UC-16 FR-ACA-01, UC-18 FR-ACA-02)
-- Pham vi phien nay: chi khung chuong trinh CHUAN (site_id NULL) - UC-16b
-- (tuy bien theo diem truong) va UC-17 (phe duyet) CHUA lam, de sau khi
-- co nhu cau that. class_sessions/attendance (Lich day & Diem danh) va
-- grade_* (So diem) cung CHUA lam trong migration nay.
-- =====================================================================

-- a) curriculums -- Khung chuong trinh (UC-16)
CREATE TABLE curriculums (
    id                             BIGSERIAL PRIMARY KEY,
    uuid                           UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code                           VARCHAR(50) UNIQUE NOT NULL, -- VD EN-G8-STD-2026
    name                           VARCHAR(300) NOT NULL,
    site_id                        BIGINT NULL REFERENCES sites(id), -- NULL = khung chuan
    parent_curriculum_id           BIGINT NULL REFERENCES curriculums(id),
    class_category                 VARCHAR(30) NOT NULL, -- MAIN / SUPPLEMENTARY / EXAM_PREP / OTHER
    level                          VARCHAR(50) NULL,
    total_periods                  INT NULL,
    default_grade_pass_threshold   DECIMAL(4,2) NULL DEFAULT 5.0,
    status                         VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PENDING_APPROVAL / ACTIVE / ARCHIVED
    created_by                     BIGINT NOT NULL REFERENCES users(id),
    approved_by                    BIGINT NULL REFERENCES users(id),
    approved_at                    TIMESTAMPTZ NULL,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                     TIMESTAMPTZ NULL,
    CONSTRAINT chk_custom_has_parent CHECK (
        (site_id IS NULL AND parent_curriculum_id IS NULL) OR
        (site_id IS NOT NULL AND parent_curriculum_id IS NOT NULL)
    )
);

-- b) curriculum_subjects -- Hoc phan trong khung
CREATE TABLE curriculum_subjects (
    id              BIGSERIAL PRIMARY KEY,
    curriculum_id   BIGINT NOT NULL REFERENCES curriculums(id),
    subject_code    VARCHAR(50) NOT NULL, -- SPEAKING / LISTENING / READING / WRITING / GRAMMAR / OTHER
    name            VARCHAR(200) NOT NULL,
    period_count    INT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    UNIQUE(curriculum_id, subject_code)
);

-- c) classes -- Lop hoc thuc te (UC-18)
CREATE TABLE classes (
    id               BIGSERIAL PRIMARY KEY,
    uuid             UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    class_code       VARCHAR(50) UNIQUE NOT NULL, -- VD 8A2-NGDU-26-27
    name             VARCHAR(300) NOT NULL,
    site_id          BIGINT NOT NULL REFERENCES sites(id),
    curriculum_id    BIGINT NOT NULL REFERENCES curriculums(id),
    class_type       VARCHAR(20) NOT NULL, -- LINKED / OPEN
    class_category   VARCHAR(30) NULL, -- copy tu curriculum tai thoi diem tao
    max_students     INT NOT NULL CHECK (max_students > 0),
    min_students     INT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NULL,
    academic_year    VARCHAR(20) NULL,
    semester         VARCHAR(20) NULL, -- S1 / S2 / SUMMER
    status           VARCHAR(20) NOT NULL DEFAULT 'PLANNED', -- PLANNED / OPEN_ENROLLMENT / IN_PROGRESS / COMPLETED / CANCELLED
    created_by       BIGINT NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ NULL
);

-- d) class_teachers -- Gan GV cho lop
CREATE TABLE class_teachers (
    id                BIGSERIAL PRIMARY KEY,
    class_id          BIGINT NOT NULL REFERENCES classes(id),
    teacher_user_id   BIGINT NOT NULL REFERENCES users(id),
    teacher_role      VARCHAR(20) NOT NULL DEFAULT 'PRIMARY', -- PRIMARY / ASSISTANT / SUBSTITUTE
    subject_id        BIGINT NULL REFERENCES curriculum_subjects(id),
    assigned_from     DATE NULL,
    assigned_to       DATE NULL, -- NULL = dang phu trach
    assigned_by       BIGINT NOT NULL REFERENCES users(id)
);
CREATE UNIQUE INDEX idx_class_teacher_primary_active
    ON class_teachers(class_id, COALESCE(subject_id, 0))
    WHERE teacher_role = 'PRIMARY' AND assigned_to IS NULL;

-- e) class_enrollments -- Hoc sinh trong lop
CREATE TABLE class_enrollments (
    id               BIGSERIAL PRIMARY KEY,
    class_id         BIGINT NOT NULL REFERENCES classes(id),
    student_id       BIGINT NOT NULL REFERENCES students(id),
    enrolled_date    DATE NOT NULL,
    withdrawn_date   DATE NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / WITHDRAWN / TRANSFERRED / COMPLETED
    withdraw_reason  TEXT NULL,
    enrolled_by      BIGINT NOT NULL REFERENCES users(id),
    import_job_id    BIGINT NULL REFERENCES import_jobs(id)
);
CREATE UNIQUE INDEX idx_enrollment_active ON class_enrollments(class_id, student_id) WHERE status = 'ACTIVE';

-- f) *_history -- SDD (ERD-Nhom5A-KhungChuongTrinh.mmd) chi ghi "Co xxx_history"
-- khong dinh nghia cot - ap dung lai dung pattern JSONB diff-log da dung o
-- employees_history/students_history.
CREATE TABLE curriculums_history (
    id             BIGSERIAL PRIMARY KEY,
    curriculum_id  BIGINT NOT NULL REFERENCES curriculums(id),
    changed_by     BIGINT NOT NULL REFERENCES users(id),
    action         VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details        JSONB NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_curriculums_history_curriculum ON curriculums_history(curriculum_id);

CREATE TABLE curriculum_subjects_history (
    id                     BIGSERIAL PRIMARY KEY,
    curriculum_subject_id  BIGINT NOT NULL REFERENCES curriculum_subjects(id),
    changed_by             BIGINT NOT NULL REFERENCES users(id),
    action                 VARCHAR(20) NOT NULL,
    details                JSONB NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_curriculum_subjects_history_subject ON curriculum_subjects_history(curriculum_subject_id);

CREATE TABLE classes_history (
    id            BIGSERIAL PRIMARY KEY,
    class_id      BIGINT NOT NULL REFERENCES classes(id),
    changed_by    BIGINT NOT NULL REFERENCES users(id),
    action        VARCHAR(20) NOT NULL,
    details       JSONB NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_classes_history_class ON classes_history(class_id);

CREATE TABLE class_teachers_history (
    id                BIGSERIAL PRIMARY KEY,
    class_teacher_id  BIGINT NOT NULL REFERENCES class_teachers(id),
    changed_by        BIGINT NOT NULL REFERENCES users(id),
    action            VARCHAR(20) NOT NULL,
    details           JSONB NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_class_teachers_history_teacher ON class_teachers_history(class_teacher_id);

CREATE TABLE class_enrollments_history (
    id                  BIGSERIAL PRIMARY KEY,
    class_enrollment_id BIGINT NOT NULL REFERENCES class_enrollments(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    action              VARCHAR(20) NOT NULL,
    details             JSONB NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_class_enrollments_history_enrollment ON class_enrollments_history(class_enrollment_id);

-- g) Bo sung FK con thieu tu V11 (student_transfer_history.from_class_id/to_class_id)
-- da ghi ro trong V11 se lam khi Phan he 6 co bang classes - khong sua V11, chi ALTER them.
ALTER TABLE student_transfer_history
    ADD CONSTRAINT fk_transfer_from_class FOREIGN KEY (from_class_id) REFERENCES classes(id),
    ADD CONSTRAINT fk_transfer_to_class FOREIGN KEY (to_class_id) REFERENCES classes(id);
