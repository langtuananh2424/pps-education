-- =====================================================================
-- V6: UC-08 (Quản lý hồ sơ nhân sự) - lịch sử phiên bản + quyền hrm.manage
-- =====================================================================

-- employees_history / employment_contracts_history: SDD (docs/sdd-groups/
-- 05-nhan-su.md + ERD-Nhom4A-HoSoNhanSu.mmd) chỉ ghi "Có employees_history"/
-- "Có employment_contracts_history" mà không định nghĩa cột — áp dụng lại
-- đúng pattern JSONB diff-log đã dùng ở permission_audit_log (V1), đã xác
-- nhận với PM thay vì tự sáng tác schema mới.

-- a) employees_history -- Lịch sử thay đổi hồ sơ nhân sự (UC-08 Main Flow bước 5)
CREATE TABLE employees_history (
    id            BIGSERIAL PRIMARY KEY,
    employee_id   BIGINT NOT NULL REFERENCES employees(id),
    changed_by    BIGINT NOT NULL REFERENCES users(id),
    action        VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details       JSONB NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_employees_history_employee ON employees_history(employee_id);

-- b) employment_contracts_history -- Lịch sử thay đổi hợp đồng lao động
CREATE TABLE employment_contracts_history (
    id            BIGSERIAL PRIMARY KEY,
    contract_id   BIGINT NOT NULL REFERENCES employment_contracts(id),
    changed_by    BIGINT NOT NULL REFERENCES users(id),
    action        VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details       JSONB NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_employment_contracts_history_contract ON employment_contracts_history(contract_id);

-- c) Quyền hrm.manage (UC-08 Precondition: role HR_MANAGER + quyền hrm.manage)
INSERT INTO permissions (code, name, module, description) VALUES
('hrm.manage', 'Quản lý hồ sơ nhân sự', 'HRM', 'FR-HRM-01');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'HR_MANAGER' AND p.code = 'hrm.manage';
