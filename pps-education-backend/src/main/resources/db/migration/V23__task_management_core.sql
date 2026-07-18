-- =====================================================================
-- V23: PHAN HE 3 - QUAN LY CONG VIEC (UC-06 FR-TSK-01, UC-07 FR-TSK-02)
--
-- assignment_status co them PENDING_REVIEW ngoai SDD goc (chi liet ke
-- PENDING/ACCEPTED/IN_PROGRESS/COMPLETED/DECLINED) - da xac nhan voi user:
-- UC-07/FR-TSK-02 mo ta Kanban 4 cot "Can lam -> Dang lam -> Cho duyet ->
-- Hoan thanh" va A2 "chuyen nguoc ve Dang lam khi tu choi ket qua o Cho
-- duyet", nhung enum SDD khong co gia tri nao khop "Cho duyet". Bo sung
-- PENDING_REVIEW de dung state machine, khong doi y nghia cac gia tri con
-- lai.
-- =====================================================================

-- a) tasks -- Cong viec
CREATE TABLE tasks (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    task_code           VARCHAR(50) UNIQUE NOT NULL,
    title               VARCHAR(500) NOT NULL,
    description         TEXT NULL,
    created_by          BIGINT NOT NULL REFERENCES users(id),
    department_id       BIGINT NULL REFERENCES departments(id),
    task_type           VARCHAR(30) NOT NULL DEFAULT 'GENERAL', -- GENERAL / URGENT / RECURRING / PROJECT
    priority            VARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- LOW / NORMAL / HIGH / URGENT
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN / IN_PROGRESS / COMPLETED / CANCELLED / OVERDUE
    due_at              TIMESTAMPTZ NULL,
    completed_at        TIMESTAMPTZ NULL,
    parent_task_id      BIGINT NULL REFERENCES tasks(id),
    tags                JSONB NULL
);
CREATE INDEX idx_tasks_overdue ON tasks(status, due_at)
    WHERE status IN ('OPEN', 'IN_PROGRESS') AND due_at IS NOT NULL;
CREATE INDEX idx_tasks_created_by ON tasks(created_by);

-- b) task_assignments -- Gan task cho nguoi thuc hien
CREATE TABLE task_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL REFERENCES tasks(id),
    assignee_user_id    BIGINT NOT NULL REFERENCES users(id),
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    assignment_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / ACCEPTED / IN_PROGRESS / PENDING_REVIEW / COMPLETED / DECLINED
    progress_percent    DECIMAL(5,2) NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ NULL,
    completed_at        TIMESTAMPTZ NULL,
    decline_reason      TEXT NULL,
    UNIQUE(task_id, assignee_user_id)
);
CREATE INDEX idx_task_assignments_assignee ON task_assignments(assignee_user_id);

-- c) task_attachments -- File dinh kem
CREATE TABLE task_attachments (
    id              BIGSERIAL PRIMARY KEY,
    task_id         BIGINT NOT NULL REFERENCES tasks(id),
    file_url        VARCHAR(1000) NOT NULL,
    file_name       VARCHAR(500) NOT NULL,
    uploaded_by     BIGINT NULL REFERENCES users(id)
);

-- d) task_comments -- Trao doi trong task (khong history, dang log)
CREATE TABLE task_comments (
    id                  BIGSERIAL PRIMARY KEY,
    task_id             BIGINT NOT NULL REFERENCES tasks(id),
    commenter_user_id   BIGINT NOT NULL REFERENCES users(id),
    content             TEXT NOT NULL,
    attachment_url      VARCHAR(1000) NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_comments_task ON task_comments(task_id, created_at);

-- e) tasks_history / task_assignments_history -- JSONB diff-log pattern
CREATE TABLE tasks_history (
    id             BIGSERIAL PRIMARY KEY,
    task_id        BIGINT NOT NULL REFERENCES tasks(id),
    changed_by     BIGINT NOT NULL REFERENCES users(id),
    action         VARCHAR(20) NOT NULL,
    details        JSONB NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_history_task ON tasks_history(task_id);

CREATE TABLE task_assignments_history (
    id                  BIGSERIAL PRIMARY KEY,
    task_assignment_id  BIGINT NOT NULL REFERENCES task_assignments(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    action              VARCHAR(20) NOT NULL,
    details             JSONB NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_assignments_history_assignment ON task_assignments_history(task_assignment_id);

-- f) Quyen task.create (UC-06 Precondition) - gan cho cac role "cap quan
-- ly" da dung lam chuan trong LeaveRequestService.MANAGEMENT_ROLE_CODES
-- (SITE_MANAGER/HEAD_ACADEMIC/OPS_MANAGER/HR_MANAGER) cong them EXECUTIVE
-- (ro rang la "cap quan ly", chi khong nam trong tap do vi ly do rieng
-- cua workflow duyet don tu).
INSERT INTO permissions (code, name, module, description) VALUES
('task.create', 'Giao việc', 'TASK', 'FR-TSK-01');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('SITE_MANAGER', 'HEAD_ACADEMIC', 'OPS_MANAGER', 'HR_MANAGER', 'EXECUTIVE')
AND p.code = 'task.create';

-- g) Nguong nhac nho sap tre han (UC-07 A1) - SDD chi noi "gan den
-- deadline" khong neu so gio cu the, da xac nhan voi user dung 24h,
-- luu vao system_settings de PM doi duoc sau khong can sua code.
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('task.due_soon_reminder_hours', '24', 'So gio truoc due_at de gui thong bao nhac nho sap tre han (UC-07 A1)', 'TASK');
