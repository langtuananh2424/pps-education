-- =====================================================================
-- V3: HỒ SƠ NHÂN SỰ CƠ BẢN (dữ liệu nền bắt buộc để gán Giáo viên vào lớp - Phân hệ 6)
-- Kéo sớm lên Phase A theo đánh giá phụ thuộc dữ liệu. Chấm công/Đơn từ/Lương
-- (phần còn lại của Phân hệ 4) triển khai ở Backend Phase C.
-- =====================================================================

-- a) employees -- Hồ sơ nhân sự (UC-08)
CREATE TABLE employees (
    id                           BIGSERIAL PRIMARY KEY,
    user_id                      BIGINT UNIQUE NOT NULL REFERENCES users(id),
    employee_code                VARCHAR(20) UNIQUE NOT NULL, -- VD NV2026-0001
    date_of_birth                DATE NOT NULL,
    id_card_number               VARCHAR(20) UNIQUE NULL,
    id_card_issued_date          DATE NULL,
    id_card_issued_place         VARCHAR(200) NULL,
    permanent_address            VARCHAR(500) NULL,
    current_address              VARCHAR(500) NULL,
    bank_account_number          VARCHAR(50) NULL,
    bank_name                    VARCHAR(200) NULL,
    tax_code                     VARCHAR(20) NULL,
    social_insurance_number      VARCHAR(20) NULL,
    employee_type                VARCHAR(20) NOT NULL, -- TEACHER / STAFF / MANAGER
    position_title                VARCHAR(200) NULL,
    is_default_shift_required    BOOLEAN NOT NULL DEFAULT TRUE,
    hire_date                    DATE NOT NULL,
    termination_date             DATE NULL,
    status                       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / ON_LEAVE / TERMINATED
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                   TIMESTAMPTZ NULL
);

-- b) employment_contracts -- Hợp đồng lao động
CREATE TABLE employment_contracts (
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    employee_id       BIGINT NOT NULL REFERENCES employees(id),
    contract_number   VARCHAR(100) UNIQUE NOT NULL,
    contract_type     VARCHAR(30) NOT NULL, -- PROBATION / FIXED_TERM / INDEFINITE / SEASONAL
    start_date        DATE NOT NULL,
    end_date          DATE NULL, -- NULL nếu INDEFINITE
    base_salary       DECIMAL(15,2) NOT NULL,
    salary_type       VARCHAR(20) NOT NULL, -- MONTHLY / HOURLY
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / ACTIVE / EXPIRED / TERMINATED
    file_url          VARCHAR(500) NULL,
    created_by        BIGINT NULL REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ NULL
);
CREATE UNIQUE INDEX idx_employment_contracts_active ON employment_contracts(employee_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- c) qualifications -- Bằng cấp/Chứng chỉ
CREATE TABLE qualifications (
    id                   BIGSERIAL PRIMARY KEY,
    employee_id          BIGINT NOT NULL REFERENCES employees(id),
    qualification_type   VARCHAR(30) NOT NULL, -- DEGREE / PEDAGOGY_CERT / LANGUAGE_CERT / OTHER
    title                VARCHAR(300) NOT NULL,
    issuer               VARCHAR(300) NULL,
    issued_date          DATE NULL,
    expiry_date          DATE NULL,
    file_url             VARCHAR(500) NULL
);

-- d) commendations -- Khen thưởng/Kỷ luật
CREATE TABLE commendations (
    id            BIGSERIAL PRIMARY KEY,
    employee_id   BIGINT NOT NULL REFERENCES employees(id),
    record_type   VARCHAR(20) NOT NULL, -- COMMENDATION / DISCIPLINE
    record_date   DATE NOT NULL,
    title         VARCHAR(300) NOT NULL,
    amount        DECIMAL(15,2) NULL,
    decided_by    BIGINT NULL REFERENCES users(id)
);
