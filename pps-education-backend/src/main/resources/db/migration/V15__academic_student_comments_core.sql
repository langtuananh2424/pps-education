-- =====================================================================
-- V15: PHAN HE 6/7 - NHAN XET DINH KY (UC-21 FR-ACA-04 Viet nhan xet,
-- UC-22 FR-LMS-09 Duyet nhan xet)
-- =====================================================================

-- a) student_comments -- Nhan xet hoc sinh
CREATE TABLE student_comments (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    student_id              BIGINT NOT NULL REFERENCES students(id),
    class_id                BIGINT NOT NULL REFERENCES classes(id),
    teacher_user_id         BIGINT NOT NULL REFERENCES users(id),
    comment_type            VARCHAR(20) NOT NULL, -- DAILY / MID_TERM / END_TERM
    class_session_id        BIGINT NULL REFERENCES class_sessions(id), -- chi set khi comment_type=DAILY
    grade_period_id         BIGINT NULL REFERENCES grade_periods(id), -- chi set khi comment_type=MID_TERM/END_TERM
    comment_date            DATE NOT NULL,
    content                 TEXT NOT NULL,
    structured_content      JSONB NULL,
    severity                VARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- POSITIVE / NORMAL / CONCERN / WARNING
    is_warning               BOOLEAN NOT NULL DEFAULT FALSE,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PENDING / APPROVED / REJECTED
    approval_flow_id         BIGINT NULL REFERENCES approval_flows(id),
    submitted_at              TIMESTAMPTZ NULL,
    approved_at               TIMESTAMPTZ NULL,
    approved_by               BIGINT NULL REFERENCES users(id),
    visible_to_parent_at      TIMESTAMPTZ NULL,
    rejection_reason          TEXT NULL,
    CONSTRAINT chk_comment_context CHECK (
        (comment_type = 'DAILY' AND class_session_id IS NOT NULL AND grade_period_id IS NULL) OR
        (comment_type IN ('MID_TERM', 'END_TERM') AND grade_period_id IS NOT NULL AND class_session_id IS NULL)
    )
);
CREATE INDEX idx_comments_student_type ON student_comments(student_id, comment_type, comment_date DESC);
CREATE INDEX idx_comments_warnings ON student_comments(student_id, comment_date DESC)
    WHERE is_warning = TRUE AND status = 'APPROVED';

-- b) student_comments_history -- SDD chi ghi "Co student_comments_history"
-- khong dinh nghia cot - ap dung lai dung pattern JSONB diff-log.
CREATE TABLE student_comments_history (
    id                   BIGSERIAL PRIMARY KEY,
    student_comment_id   BIGINT NOT NULL REFERENCES student_comments(id),
    changed_by           BIGINT NOT NULL REFERENCES users(id),
    action                VARCHAR(20) NOT NULL,
    details               JSONB NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_student_comments_history_comment ON student_comments_history(student_comment_id);
