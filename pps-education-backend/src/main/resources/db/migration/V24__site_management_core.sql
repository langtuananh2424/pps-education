-- =====================================================================
-- V24: PHAN HE 10 - UC-36 QUAN LY DIEM TRUONG (FR-FAC-01)
--
-- Bang sites/partner_school_info/site_managers da co san tu V2 (keo som
-- len Phase A vi Phan he 6 xep lop phu thuoc FK toi sites). V24 chi bo
-- sung phan con thieu de trien khai UC-36: sites_history (SDD "Co
-- sites_history" - .claude/rules/business-fidelity.md yeu cau khong bo
-- sot bang duoc liet ke trong SDD) va quyen facility.manage (Precondition
-- UC-36).
-- =====================================================================

-- a) sites_history -- JSONB diff-log pattern (giong tasks_history V23)
CREATE TABLE sites_history (
    id             BIGSERIAL PRIMARY KEY,
    site_id        BIGINT NOT NULL REFERENCES sites(id),
    changed_by     BIGINT NOT NULL REFERENCES users(id),
    action         VARCHAR(20) NOT NULL,
    details        JSONB NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sites_history_site ON sites_history(site_id);

-- b) Quyen facility.manage (UC-36 Precondition) - actor chinh la Quan ly
-- van hanh (OPS_MANAGER); Quan tri vien (SYS_ADMIN) la tac nhan
-- lien quan/ho tro duoc UC neu ro.
INSERT INTO permissions (code, name, module, description) VALUES
('facility.manage', 'Quản lý điểm trường', 'FACILITY', 'FR-FAC-01');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('OPS_MANAGER', 'SYS_ADMIN')
AND p.code = 'facility.manage';
