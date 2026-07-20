-- =====================================================================
-- V41: PHAN HE 7 - KHO TAI LIEU THAM KHAO (UC-60, FR-LMS-13 MOI).
-- BO SUNG NGOAI SDD GOC - da xac nhan voi nguoi dung.
--
-- Khai niem doc lap voi lessons/lesson_materials (UC-23) - khong gan voi
-- 1 bai giang cu the, chi gan theo curriculum. GV/Admin (Head
-- Academic/Site Manager duoc cap qua role_permissions/UC-04 override)
-- upload, Hoc sinh xem theo curriculum cua (cac) lop dang ghi danh.
-- =====================================================================

CREATE TABLE curriculum_documents (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    curriculum_id       BIGINT NOT NULL REFERENCES curriculums(id),
    title               VARCHAR(500) NOT NULL,
    description         TEXT NULL,
    document_type       VARCHAR(30) NOT NULL, -- VIDEO / PDF / AUDIO / SLIDE / IMAGE / OTHER
    file_url            VARCHAR(1000) NOT NULL, -- CDN, giong lesson_materials.file_url (NFR-TECH-07)
    display_order       INT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PUBLISHED / ARCHIVED
    created_by          BIGINT NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_curriculum_documents_curriculum ON curriculum_documents(curriculum_id);

-- Permission rieng (khong tai dung lms.exercise.manage vi mo ta permission
-- do da khai ro "ngan hang cau hoi & de kiem tra", khac ngu nghia voi
-- "kho tai lieu tham khao").
INSERT INTO permissions (code, name, module, description) VALUES
('lms.document.manage', 'Quản lý kho tài liệu tham khảo', 'LMS', 'UC-60');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.document.manage' AND r.code IN ('TEACHER');
