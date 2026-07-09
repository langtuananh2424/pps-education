-- =====================================================================
-- V1: NHÓM NỀN TẢNG (SDD > Thiết kế cơ sở dữ liệu > Nền tảng)
-- Phân hệ 1 (Đăng nhập & Xác thực) + Phân hệ 2 (Quản trị người dùng & Phân quyền)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

-- a) departments -- Phòng ban
CREATE TABLE departments (
    id                    BIGSERIAL PRIMARY KEY,
    uuid                  UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code                  VARCHAR(50) UNIQUE NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    head_user_id          BIGINT NULL, -- FK -> users(id), thêm constraint sau khi seed (quan hệ vòng)
    parent_department_id  BIGINT NULL REFERENCES departments(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- b) users -- Tài khoản người dùng (bảng trung tâm)
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(100) UNIQUE NOT NULL,
    email               VARCHAR(255) UNIQUE NOT NULL,
    password_hash       VARCHAR(255) NULL, -- NULL nếu chỉ đăng nhập Google; BCrypt (NFR-SEC-01)
    full_name           VARCHAR(200) NOT NULL,
    phone               VARCHAR(20) NULL,
    department_id       BIGINT NULL REFERENCES departments(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / INACTIVE / SUSPENDED
    is_management       BOOLEAN NOT NULL DEFAULT FALSE, -- Miễn trừ chấm công / duyệt đơn
    google_id           VARCHAR(255) UNIQUE NULL,
    last_login_at        TIMESTAMPTZ NULL,
    failed_login_count   INT NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_head_user FOREIGN KEY (head_user_id) REFERENCES users(id);

-- c) roles -- Vai trò (11 role gốc hệ thống)
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    uuid        UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(50) UNIQUE NOT NULL, -- STUDENT, PARENT, TEACHER, HEAD_ACADEMIC, SITE_MANAGER,
                                              -- OPS_MANAGER, HR_MANAGER, STAFF, SYS_ADMIN, PARTNER_REP, EXECUTIVE
    name        VARCHAR(200) NOT NULL,
    description TEXT NULL,
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- d) permissions -- Danh mục quyền chi tiết (UC-02)
CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100) UNIQUE NOT NULL, -- <module>.<action>, VD class.create
    name        VARCHAR(200) NOT NULL,
    module      VARCHAR(50) NOT NULL, -- AUTH, USER, TASK, HRM, STUDENT, ACADEMIC, LMS, FINANCE, CRM, FACILITY
    description TEXT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- e) user_roles -- Gán role cho user (M-N)
CREATE TABLE user_roles (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    role_id     BIGINT NOT NULL REFERENCES roles(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by BIGINT NOT NULL REFERENCES users(id),
    UNIQUE (user_id, role_id)
);

-- f) role_permissions -- Role <-> Permission (UC-03)
CREATE TABLE role_permissions (
    id            BIGSERIAL PRIMARY KEY,
    role_id       BIGINT NOT NULL REFERENCES roles(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    UNIQUE (role_id, permission_id)
);

-- g) user_permission_overrides -- Quyền ngoại lệ theo tài khoản (UC-04)
CREATE TABLE user_permission_overrides (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    override_type VARCHAR(20) NOT NULL CHECK (override_type IN ('GRANT', 'REVOKE')),
    reason        TEXT NULL,
    granted_by    BIGINT NOT NULL REFERENCES users(id),
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NULL,
    UNIQUE (user_id, permission_id)
);

-- h) permission_audit_log -- Nhật ký thay đổi phân quyền (UC-05)
CREATE TABLE permission_audit_log (
    id                    BIGSERIAL PRIMARY KEY,
    actor_user_id         BIGINT NOT NULL REFERENCES users(id),
    target_user_id        BIGINT NOT NULL REFERENCES users(id),
    action                VARCHAR(50) NOT NULL, -- ROLE_GRANTED / ROLE_REVOKED / PERM_OVERRIDE_ADDED / PERM_OVERRIDE_REMOVED
    target_role_id        BIGINT NULL REFERENCES roles(id),
    target_permission_id  BIGINT NULL REFERENCES permissions(id),
    details               JSONB NULL,
    ip_address            INET NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- i) refresh_tokens -- Refresh token cho JWT (UC-01)
CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    token_hash    VARCHAR(255) UNIQUE NOT NULL, -- SHA-256
    device_info   VARCHAR(500) NULL,
    ip_address    INET NULL,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ NULL,
    last_used_at  TIMESTAMPTZ NULL
);
CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE revoked_at IS NULL;

-- j) login_attempts -- Nhật ký đăng nhập (FR-AUT-02 chống Brute-Force)
CREATE TABLE login_attempts (
    id                  BIGSERIAL PRIMARY KEY,
    username_or_email   VARCHAR(255) NOT NULL,
    user_id             BIGINT NULL REFERENCES users(id),
    ip_address          INET NOT NULL,
    user_agent          VARCHAR(500) NULL,
    success             BOOLEAN NOT NULL,
    failure_reason      VARCHAR(50) NULL, -- WRONG_PASSWORD / USER_NOT_FOUND / USER_LOCKED / USER_INACTIVE
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- k) system_settings -- Cấu hình hệ thống (bật/tắt tính năng)
CREATE TABLE system_settings (
    id             BIGSERIAL PRIMARY KEY,
    setting_key    VARCHAR(100) UNIQUE NOT NULL,
    setting_value  JSONB NOT NULL,
    description    TEXT NULL,
    category       VARCHAR(50) NOT NULL, -- ATTENDANCE / SECURITY / NOTIFICATION / FEATURE_FLAG
    updated_by     BIGINT NULL REFERENCES users(id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- l) import_jobs -- Hạ tầng import Excel dùng chung (UC-35 và các luồng import khác)
CREATE TABLE import_jobs (
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    import_type        VARCHAR(50) NOT NULL, -- SHIFTS / EMPLOYEE_SHIFTS / WORK_CALENDAR / STUDENTS / ATTENDANCE / TEACHING_SCHEDULE
    source_file_name   VARCHAR(500) NOT NULL,
    source_file_url    VARCHAR(500) NOT NULL,
    total_rows         INT NULL,
    success_rows       INT NOT NULL DEFAULT 0,
    failed_rows        INT NOT NULL DEFAULT 0,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / PROCESSING / COMPLETED / FAILED / PARTIAL_SUCCESS
    error_summary      JSONB NULL,
    result_details     JSONB NULL,
    uploaded_by        BIGINT NULL REFERENCES users(id),
    started_at         TIMESTAMPTZ NULL,
    finished_at        TIMESTAMPTZ NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- m) approval_flows -- Luồng duyệt 1 bước dùng chung (UC-19/20, UC-21/22, UC-17...)
CREATE TABLE approval_flows (
    id            BIGSERIAL PRIMARY KEY,
    entity_type   VARCHAR(50) NOT NULL, -- CURRICULUM / STUDENT_COMMENT / GRADE_ENTRY / TEACHING_PLAN
    entity_id     BIGINT NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / APPROVED / REJECTED / CANCELLED
    submitted_by  BIGINT NOT NULL REFERENCES users(id),
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    approver_id   BIGINT NULL REFERENCES users(id),
    decision      VARCHAR(20) NULL,
    comment       TEXT NULL,
    decided_at    TIMESTAMPTZ NULL,
    batch_id      UUID NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_approval_entity ON approval_flows(entity_type, entity_id);
CREATE INDEX idx_approval_pending ON approval_flows(status, submitted_at) WHERE status = 'PENDING';
CREATE INDEX idx_approval_batch ON approval_flows(batch_id) WHERE batch_id IS NOT NULL;
