-- =====================================================================
-- V108: PHAN HE 6 - QUAN LY MAU BAO CAO / MAIL MERGE (UC-67, UC-68,
-- FR-ACA-08). Bo sung ngoai SDD goc, da xac nhan voi nguoi dung
-- 2026-08-09 (xem docs/uc/phan-he-06-hoc-thuat.md).
--
-- Pham vi migration nay: chi report_templates + history + field_mappings +
-- 5 permission CRUD hat nhan cho UC-67 (Quan ly mau bao cao). Bang
-- generated_reports (luu lich su xuat bao cao thuc te, UC-68) se them o
-- migration rieng khi trien khai tinh nang xuat bao cao (chua can ngay -
-- tranh tao bang chua co Service/Repository nao dung toi).
-- =====================================================================

-- a) report_templates -- Mau bao cao (DOCX/PDF) da danh dau placeholder
CREATE TABLE report_templates (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    template_type       VARCHAR(50) NOT NULL, -- TRANSCRIPT / DAILY_REPORT / STUDENT_PROFILE / GRADE_REPORT
    description         TEXT NULL,
    file_url            VARCHAR(1000) NOT NULL, -- CDN R2, giong curriculum_documents.file_url (NFR-TECH-07)
    file_format         VARCHAR(20) NOT NULL,   -- DOCX / PDF
    original_filename   VARCHAR(255) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    placeholder_keys    JSONB NOT NULL DEFAULT '[]'::jsonb, -- danh sach [FIELD]/[[formula]]/[[TABLE:x]] phat hien duoc
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          BIGINT NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_templates_type ON report_templates(template_type) WHERE is_active = TRUE;

-- b) report_templates_history -- JSONB diff-log, dung pattern da dung o
-- students_history/employees_history (da xac nhan voi PM).
CREATE TABLE report_templates_history (
    id            BIGSERIAL PRIMARY KEY,
    template_id   BIGINT NOT NULL REFERENCES report_templates(id),
    changed_by    BIGINT NOT NULL REFERENCES users(id),
    action        VARCHAR(20) NOT NULL, -- CREATED / UPDATED / DELETED
    details       JSONB NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_templates_history_template ON report_templates_history(template_id);

-- c) report_template_field_mappings -- Anh xa moi placeholder toi nguon du lieu
CREATE TABLE report_template_field_mappings (
    id                BIGSERIAL PRIMARY KEY,
    template_id       BIGINT NOT NULL REFERENCES report_templates(id),
    placeholder_key   VARCHAR(200) NOT NULL, -- co the la bieu thuc [[...]] hoac ten bang [[TABLE:x]]
    data_path         VARCHAR(500) NULL,     -- NULL neu placeholder_key la formula/table thuan
    field_type        VARCHAR(20) NOT NULL,  -- FIELD / FORMULA / TABLE
    description       TEXT NULL,
    UNIQUE(template_id, placeholder_key)
);

-- d) Quyen CRUD hat nhan (UC-67) -- khong gop 1 quyen "manage", dung dung
-- pattern hat nhan da ap dung tu V49/V51 cho cac entity khac. report.generate
-- (UC-68) khai bao san o day de FE/Controller dung duoc ngay khi Phase 2
-- trien khai xuat bao cao, du Service/Controller generate chua co trong
-- migration nay.
INSERT INTO permissions (code, name, module, description) VALUES
('report.template.create', 'Tao mau bao cao (upload file + danh dau placeholder)', 'ACADEMIC', 'UC-67'),
('report.template.view',   'Xem danh sach/chi tiet mau bao cao',                   'ACADEMIC', 'UC-67'),
('report.template.update', 'Sua mau bao cao / cau hinh field mapping',             'ACADEMIC', 'UC-67'),
('report.template.delete', 'Xoa mau bao cao',                                       'ACADEMIC', 'UC-67'),
('report.generate',        'Tao va xuat bao cao tu mau',                            'ACADEMIC', 'UC-68');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HEAD_ACADEMIC', 'SITE_MANAGER', 'SYS_ADMIN')
  AND p.code IN ('report.template.create', 'report.template.view', 'report.template.update', 'report.template.delete');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('TEACHER', 'HEAD_ACADEMIC', 'SITE_MANAGER', 'SYS_ADMIN')
  AND p.code = 'report.generate';
