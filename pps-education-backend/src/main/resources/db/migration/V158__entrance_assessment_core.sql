-- =====================================================================
-- V158: DANH GIA DAU VAO (ky thi dau vao) - UC-18c
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-28.
--
-- Trung tam co ky thi dau vao lam co so xep lop. Diem dau vao co cau
-- truc GIONG so diem theo ky (tu setup ky nang + diem) nhung KHONG neo
-- vao lop/ky hoc vi thi sinh chua duoc xep lop:
--   * Bo de (setup) gioi han theo diem truong + nam hoc.
--   * Doi tuong cham: lead (CRM) HOAC student - dung 1 trong 2 (CHECK).
--   * KHONG co quy trinh duyet (DRAFT/SUBMITTED/OFFICIAL) - nhap truc tiep,
--     audit qua entered_by + timestamps.
--   * Ket qua luu them recommended_level + recommended_class_id + co
--     hanh dong "chuyen sang xep lop" (mark-placed).
-- =====================================================================

-- ===================== 1. entrance_assessment_setups =====================
CREATE TABLE entrance_assessment_setups (
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    site_id            BIGINT NOT NULL REFERENCES sites(id),
    academic_year_id   BIGINT NOT NULL REFERENCES academic_years(id),
    name               VARCHAR(200) NOT NULL,
    scale_type         VARCHAR(20) NOT NULL DEFAULT 'POINT_10', -- POINT_10 / PERCENT / IELTS (tai dung GradeComponentSetup.ScaleType)
    created_by         BIGINT NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ NULL,
    CONSTRAINT chk_entrance_setup_scale_type CHECK (scale_type IN ('POINT_10', 'PERCENT', 'IELTS')),
    UNIQUE (site_id, academic_year_id, name)
);
CREATE INDEX idx_entrance_setups_site_year ON entrance_assessment_setups(site_id, academic_year_id);

-- ===================== 2. entrance_assessment_components (dau diem / ky nang) =====================
CREATE TABLE entrance_assessment_components (
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    setup_id           BIGINT NOT NULL REFERENCES entrance_assessment_setups(id),
    skill_id           BIGINT NULL REFERENCES skills(id),
    code               VARCHAR(50) NOT NULL,  -- SPEAKING / WRITING / LISTENING / READING / GRAMMAR / OTHER
    name               VARCHAR(200) NOT NULL,
    max_score          DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    display_order      INT NOT NULL DEFAULT 0,
    UNIQUE (setup_id, code)
);
CREATE INDEX idx_entrance_components_setup ON entrance_assessment_components(setup_id);

-- ===================== 3. entrance_assessment_results (1 dong = 1 thi sinh / 1 setup) =====================
CREATE TABLE entrance_assessment_results (
    id                    BIGSERIAL PRIMARY KEY,
    uuid                  UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    setup_id              BIGINT NOT NULL REFERENCES entrance_assessment_setups(id),
    lead_id               BIGINT NULL REFERENCES leads(id),
    student_id            BIGINT NULL REFERENCES students(id),
    candidate_name        VARCHAR(200) NOT NULL,  -- denormalize de hien thi nhanh
    assessed_date         DATE NOT NULL,
    overall_score         DECIMAL(5,2) NULL,      -- nhap tay, khong auto tinh tu component (mirror so diem)
    recommended_level     VARCHAR(100) NULL,
    recommended_class_id  BIGINT NULL REFERENCES classes(id),
    placed_flag           BOOLEAN NOT NULL DEFAULT FALSE, -- da chuyen sang xep lop chua
    note                  TEXT NULL,
    entered_by            BIGINT NOT NULL REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Dung 1 trong 2: lead HOAC student
    CONSTRAINT chk_entrance_result_subject CHECK ((lead_id IS NOT NULL) <> (student_id IS NOT NULL))
);
CREATE INDEX idx_entrance_results_setup ON entrance_assessment_results(setup_id);
-- 1 thi sinh chi 1 ket qua / setup (partial unique vi 2 cot nullable)
CREATE UNIQUE INDEX uq_entrance_results_setup_lead ON entrance_assessment_results(setup_id, lead_id) WHERE lead_id IS NOT NULL;
CREATE UNIQUE INDEX uq_entrance_results_setup_student ON entrance_assessment_results(setup_id, student_id) WHERE student_id IS NOT NULL;

-- ===================== 4. entrance_assessment_scores (diem tung dau diem) =====================
CREATE TABLE entrance_assessment_scores (
    id             BIGSERIAL PRIMARY KEY,
    result_id      BIGINT NOT NULL REFERENCES entrance_assessment_results(id) ON DELETE CASCADE,
    component_id   BIGINT NOT NULL REFERENCES entrance_assessment_components(id),
    score          DECIMAL(5,2) NULL,
    absence_flag   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (result_id, component_id)
);
CREATE INDEX idx_entrance_scores_result ON entrance_assessment_scores(result_id);

-- ===================== 5. Permissions (UC-18c) =====================
-- Tach rieng khoi academic.grade.* vi day la nhom hanh dong khac (danh gia
-- truoc khi xep lop, chua co lop/ky). setup.* cho HEAD_ACADEMIC + SITE_MANAGER;
-- score.manage them TEACHER (giao vien tham gia cham dau vao).
INSERT INTO permissions (code, name, module, description) VALUES
('academic.entrance.setup.create', 'Tạo bộ đề đánh giá đầu vào', 'ACADEMIC', 'UC-18c (bổ sung ngoài SDD gốc)'),
('academic.entrance.setup.update', 'Sửa bộ đề đánh giá đầu vào', 'ACADEMIC', 'UC-18c (bổ sung ngoài SDD gốc)'),
('academic.entrance.setup.delete', 'Xoá bộ đề đánh giá đầu vào', 'ACADEMIC', 'UC-18c (bổ sung ngoài SDD gốc)'),
('academic.entrance.score.manage', 'Nhập/sửa điểm & kết quả đánh giá đầu vào', 'ACADEMIC', 'UC-18c (bổ sung ngoài SDD gốc)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN ('academic.entrance.setup.create', 'academic.entrance.setup.update',
                 'academic.entrance.setup.delete', 'academic.entrance.score.manage')
  AND r.code IN ('HEAD_ACADEMIC', 'SITE_MANAGER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.entrance.score.manage'
  AND r.code = 'TEACHER';
