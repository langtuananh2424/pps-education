-- =====================================================================
-- V9: UC-12 (Xem bảng lương) - payroll_periods, payroll_entries
-- UC-12 CHỈ ĐỌC (Postcondition: "dữ liệu không bị thay đổi") - schema này
-- phục vụ đọc; việc TÍNH toán/ghi payroll_entries thuộc 1 cơ chế khác chưa
-- được đặc tả ở UC nào (Precondition UC-12 giả định "kỳ lương đã được hệ
-- thống tính toán" từ trước) - đã xác nhận với PM chỉ làm phần đọc ở đây.
-- =====================================================================

-- a) payroll_periods -- Kỳ lương
CREATE TABLE payroll_periods (
    id            BIGSERIAL PRIMARY KEY,
    period_code   VARCHAR(20) UNIQUE NOT NULL, -- VD 2026-07
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / LOCKED / PAID
    locked_at     TIMESTAMPTZ NULL,
    locked_by     BIGINT NULL REFERENCES users(id)
);

-- b) payroll_entries -- Chi tiết lương từng nhân sự
CREATE TABLE payroll_entries (
    id                        BIGSERIAL PRIMARY KEY,
    uuid                      UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    payroll_period_id         BIGINT NOT NULL REFERENCES payroll_periods(id),
    employee_id               BIGINT NOT NULL REFERENCES employees(id),
    base_salary               DECIMAL(15,2) NOT NULL, -- snapshot từ hợp đồng ACTIVE tại thời điểm chốt
    teaching_hours            DECIMAL(10,2) NULL, -- cho GV
    hourly_rate               DECIMAL(15,2) NULL,
    work_days                 DECIMAL(5,2) DEFAULT 0, -- từ attendance_records
    bonuses                   DECIMAL(15,2) DEFAULT 0, -- từ commendations
    penalties                 DECIMAL(15,2) DEFAULT 0,
    tax                       DECIMAL(15,2) DEFAULT 0,
    social_insurance          DECIMAL(15,2) DEFAULT 0,
    health_insurance          DECIMAL(15,2) DEFAULT 0,
    unemployment_insurance    DECIMAL(15,2) DEFAULT 0,
    gross_salary              DECIMAL(15,2) NOT NULL,
    total_deductions          DECIMAL(15,2) NOT NULL,
    net_salary                DECIMAL(15,2) NOT NULL,
    calculation_details       JSONB NULL, -- breakdown công thức tính
    status                    VARCHAR(20) NOT NULL DEFAULT 'CALCULATED', -- CALCULATED / APPROVED / PAID
    UNIQUE(payroll_period_id, employee_id)
);
CREATE INDEX idx_payroll_entries_employee ON payroll_entries(employee_id);

-- payroll_entries_history -- SDD chỉ ghi "Có payroll_entries_history",
-- không định nghĩa cột; giữ bảng cho lần triển khai cơ chế tính lương sau
-- này (chưa có code write-path trong PR này nên chưa map Java entity, xem
-- pattern JSONB diff-log đã dùng ở employees_history/attendance_records_history).
CREATE TABLE payroll_entries_history (
    id                  BIGSERIAL PRIMARY KEY,
    payroll_entry_id    BIGINT NOT NULL REFERENCES payroll_entries(id),
    changed_by          BIGINT NOT NULL REFERENCES users(id),
    action              VARCHAR(20) NOT NULL,
    details             JSONB NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payroll_entries_history_entry ON payroll_entries_history(payroll_entry_id);
