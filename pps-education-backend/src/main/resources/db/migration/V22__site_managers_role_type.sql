-- =====================================================================
-- V22: PHAN HE 10 - PHAN BIET VAI TRO TRONG site_managers (UC-29 FR-LMS-08)
--
-- SDD goc: site_managers chi thiet ke cho Quan ly diem truong (UC-36),
-- unique index (site_id) WHERE assigned_to IS NULL chi cho phep DUNG 1
-- nguoi active/site. UC-29 can Dai dien truong lien ket (PARTNER_REP)
-- cung duoc gan vao site do, dong thoi voi Quan ly diem truong - khong co
-- bang nao khac trong SDD cho quan he nay. Da xac nhan voi user: tai su
-- dung site_managers, them cot phan biet vai tro thay vi tao bang moi.
-- =====================================================================

ALTER TABLE site_managers
    ADD COLUMN role_type VARCHAR(20) NOT NULL DEFAULT 'SITE_MANAGER'
        CHECK (role_type IN ('SITE_MANAGER', 'PARTNER_REP'));

DROP INDEX idx_site_managers_active;

-- 1 site co the co dong thoi 1 SITE_MANAGER active + 1 PARTNER_REP active.
CREATE UNIQUE INDEX idx_site_managers_active ON site_managers(site_id, role_type) WHERE assigned_to IS NULL;
