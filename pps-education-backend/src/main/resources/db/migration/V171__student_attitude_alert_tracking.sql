-- =====================================================================
-- V171: Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-12.
--
-- Yeu cau khach hang: 1 buoi nhan xet hang ngay (student_comments) co
-- attitude = WEAK/AVERAGE (Yeu/Trung binh) thi bao ngay cho Phu huynh;
-- neu WEAK/AVERAGE lien tuc 3 buoi (tinh tren nhan xet da duoc Quan ly
-- diem truong DUYET) thi gui them 1 canh bao escalation (email+push)
-- nhung phai qua Quan ly diem truong DUYET truoc -- mirror dung cau truc
-- V92__homework_alert_tracking.sql + homework_parent_meeting_invites (bo
-- khai niem "channel" vi attitude chi co dung 1 luong canh bao).
-- =====================================================================

CREATE TABLE student_attitude_alert_state (
    id                      BIGSERIAL PRIMARY KEY,
    student_id              BIGINT NOT NULL REFERENCES students(id),
    school_class_id         BIGINT NOT NULL REFERENCES classes(id),
    academic_term_id        BIGINT NULL REFERENCES academic_terms(id),
    consecutive_low_count   INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (student_id, school_class_id, academic_term_id)
);
CREATE INDEX idx_attitude_alert_state_student ON student_attitude_alert_state(student_id);

-- KHONG dung chung approval_flows (submitted_by NOT NULL, ban ghi nay do
-- he thong tu sinh khi streak cham moc 3, khong co "nguoi nop" that) --
-- mirror dung homework_parent_meeting_invites (V170).
CREATE TABLE student_attitude_escalations (
    id                BIGSERIAL PRIMARY KEY,
    student_id        BIGINT NOT NULL REFERENCES students(id),
    school_class_id   BIGINT NOT NULL REFERENCES classes(id),
    streak_count      INT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by        BIGINT REFERENCES users(id),
    decided_at        TIMESTAMPTZ,
    rejection_reason  TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attitude_escalation_status ON student_attitude_escalations(status);
CREATE INDEX idx_attitude_escalation_class ON student_attitude_escalations(school_class_id);
