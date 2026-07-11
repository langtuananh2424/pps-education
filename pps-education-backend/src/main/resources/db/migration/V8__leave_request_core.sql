-- =====================================================================
-- V8: UC-10/UC-11 (Đơn từ) - leave_requests, leave_request_approvals
-- =====================================================================

-- a) leave_requests -- Đơn từ
CREATE TABLE leave_requests (
    id                    BIGSERIAL PRIMARY KEY,
    uuid                  UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    employee_id           BIGINT NOT NULL REFERENCES employees(id),
    leave_type            VARCHAR(20) NOT NULL, -- ANNUAL / SICK / UNPAID / LATE / EARLY_LEAVE / PERSONAL
    start_date            DATE NOT NULL,
    end_date              DATE NOT NULL,
    start_time            TIME NULL, -- áp dụng LATE/EARLY_LEAVE
    end_time              TIME NULL,
    total_days            DECIMAL(4,2) NOT NULL, -- tính bởi service
    reason                TEXT NOT NULL,
    attachment_url        VARCHAR(500) NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / APPROVED / REJECTED / CANCELLED
    current_step          INT NOT NULL DEFAULT 1,
    current_approver_id   BIGINT NULL REFERENCES users(id),
    submitted_at          TIMESTAMPTZ NULL,
    finalized_at          TIMESTAMPTZ NULL
);
CREATE INDEX idx_leave_requests_employee ON leave_requests(employee_id);
CREATE INDEX idx_leave_requests_status ON leave_requests(status);

-- b) leave_request_approvals -- Các bước duyệt
CREATE TABLE leave_request_approvals (
    id                  BIGSERIAL PRIMARY KEY,
    leave_request_id    BIGINT NOT NULL REFERENCES leave_requests(id),
    step_order          INT NOT NULL,
    approver_role       VARCHAR(30) NOT NULL, -- DEPARTMENT_HEAD / OPERATIONS_MANAGER / EXECUTIVE
    approver_user_id    BIGINT NULL REFERENCES users(id), -- điền khi có người vào duyệt
    decision            VARCHAR(20) NULL, -- APPROVED / REJECTED
    comment             TEXT NULL,
    decided_at          TIMESTAMPTZ NULL,
    UNIQUE(leave_request_id, step_order)
);

-- leave_requests_history -- SDD chỉ ghi "Có leave_requests_history", không
-- định nghĩa cột; áp dụng lại pattern JSONB diff-log (employees_history V6,
-- attendance_records_history V7).
CREATE TABLE leave_requests_history (
    id                  BIGSERIAL PRIMARY KEY,
    leave_request_id    BIGINT NOT NULL REFERENCES leave_requests(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    action               VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details              JSONB NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_leave_requests_history_request ON leave_requests_history(leave_request_id);
