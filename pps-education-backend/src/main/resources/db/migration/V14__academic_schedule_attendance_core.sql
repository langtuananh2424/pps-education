-- =====================================================================
-- V14: PHAN HE 6/5 - LICH DAY & DIEM DANH HOC SINH (nen tang cho UC-15)
--
-- Khong co UC nao trong docs/uc/ mo ta Main Flow cho viec tao class_sessions/
-- session_periods (UC-15 va UC-37 deu tro ve "UC-18" nhung UC-18 that su chi
-- lam classes/class_teachers/class_enrollments, khong lien quan xep lich
-- buoi hoc - da xac nhan lai voi user). Coi day la phan mo rong ngam cua
-- Phan he 6 (Nhan vien giao vu tao lich, cung actor voi UC-18), bam dung
-- schema SDD (docs/sdd-groups/06-hoc-thuat.md > "Lich day & Diem danh") +
-- FR-FAC-03 (kiem tra trung phong).
-- =====================================================================

-- a) class_sessions -- Buoi hoc
CREATE TABLE class_sessions (
    id                            BIGSERIAL PRIMARY KEY,
    uuid                          UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    class_id                      BIGINT NOT NULL REFERENCES classes(id),
    session_date                  DATE NOT NULL,
    start_time                    TIME NOT NULL,
    end_time                      TIME NOT NULL,
    room_id                       BIGINT NULL REFERENCES rooms(id),
    primary_teacher_id            BIGINT NOT NULL REFERENCES users(id),
    session_type                  VARCHAR(20) NOT NULL DEFAULT 'REGULAR', -- REGULAR / MAKEUP / EXAM / SPECIAL
    status                        VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED / IN_PROGRESS / COMPLETED / CANCELLED / RESCHEDULED
    cancellation_reason           TEXT NULL,
    rescheduled_to_session_id     BIGINT NULL REFERENCES class_sessions(id),
    created_by                    BIGINT NOT NULL REFERENCES users(id),
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_session_time CHECK (end_time > start_time)
);
CREATE INDEX idx_class_sessions_date ON class_sessions(session_date);
CREATE INDEX idx_class_sessions_teacher_date ON class_sessions(primary_teacher_id, session_date DESC);

-- b) session_periods -- Tiet hoc trong buoi
CREATE TABLE session_periods (
    id                  BIGSERIAL PRIMARY KEY,
    class_session_id    BIGINT NOT NULL REFERENCES class_sessions(id),
    period_number       INT NOT NULL,
    start_time          TIME NOT NULL,
    end_time            TIME NOT NULL,
    teacher_id          BIGINT NULL REFERENCES users(id), -- NULL = dung primary_teacher cua session
    subject_id          BIGINT NULL REFERENCES curriculum_subjects(id),
    content_note        TEXT NULL,
    UNIQUE(class_session_id, period_number)
);

-- c) attendance_sessions -- Buoi diem danh (header)
CREATE TABLE attendance_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    class_session_id    BIGINT NOT NULL UNIQUE REFERENCES class_sessions(id),
    mode                VARCHAR(20) NOT NULL DEFAULT 'SESSION_LEVEL', -- SESSION_LEVEL / PERIOD_LEVEL
    marked_by           BIGINT NOT NULL REFERENCES users(id),
    marked_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / SUBMITTED / LOCKED
    submitted_at         TIMESTAMPTZ NULL
);

-- d) attendance_marks -- Diem danh cap buoi (moi HS) - UC-15
CREATE TABLE attendance_marks (
    id                       BIGSERIAL PRIMARY KEY,
    attendance_session_id    BIGINT NOT NULL REFERENCES attendance_sessions(id),
    student_id               BIGINT NOT NULL REFERENCES students(id),
    status                   VARCHAR(20) NOT NULL, -- PRESENT / ABSENT / EXCUSED / LATE / EARLY_LEAVE
    check_in_time            TIMESTAMPTZ NULL,
    minutes_late             INT NULL,
    minutes_early_leave      INT NULL,
    absence_reason           TEXT NULL,
    notified_parent_at       TIMESTAMPTZ NULL,
    UNIQUE(attendance_session_id, student_id)
);
CREATE INDEX idx_attendance_marks_student ON attendance_marks(student_id);

-- e) attendance_period_marks -- Diem danh chi tiet theo tiet
CREATE TABLE attendance_period_marks (
    id                    BIGSERIAL PRIMARY KEY,
    attendance_mark_id    BIGINT NOT NULL REFERENCES attendance_marks(id),
    session_period_id     BIGINT NOT NULL REFERENCES session_periods(id),
    status                VARCHAR(20) NOT NULL, -- PRESENT / ABSENT / EXCUSED / LATE
    note                  TEXT NULL,
    UNIQUE(attendance_mark_id, session_period_id)
);

-- f) *_history -- SDD chi ghi "Co xxx_history" khong dinh nghia cot (class_sessions,
-- session_periods, attendance_marks) - ap dung lai dung pattern JSONB diff-log.
-- attendance_sessions/attendance_period_marks KHONG co history rieng (SDD:
-- "chi tiet thay doi da co o attendance_marks_history").
CREATE TABLE class_sessions_history (
    id                BIGSERIAL PRIMARY KEY,
    class_session_id  BIGINT NOT NULL REFERENCES class_sessions(id),
    changed_by        BIGINT NOT NULL REFERENCES users(id),
    action            VARCHAR(20) NOT NULL,
    details           JSONB NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_class_sessions_history_session ON class_sessions_history(class_session_id);

CREATE TABLE session_periods_history (
    id                  BIGSERIAL PRIMARY KEY,
    session_period_id   BIGINT NOT NULL REFERENCES session_periods(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    action              VARCHAR(20) NOT NULL,
    details             JSONB NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_periods_history_period ON session_periods_history(session_period_id);

CREATE TABLE attendance_marks_history (
    id                  BIGSERIAL PRIMARY KEY,
    attendance_mark_id  BIGINT NOT NULL REFERENCES attendance_marks(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    action              VARCHAR(20) NOT NULL,
    details             JSONB NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendance_marks_history_mark ON attendance_marks_history(attendance_mark_id);

-- g) Cau hinh so tiet mac dinh moi buoi (SDD: "tu dong sinh mac dinh 2
-- tiet/buoi theo system_settings" - khong co setting_key cu the nao duoc
-- tai lieu hoa, da xac nhan voi user dat ten key + gia tri nay).
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('academic.default_periods_per_session', '2', 'So tiet mac dinh tu sinh khi tao 1 buoi hoc (class_sessions)', 'ACADEMIC');
