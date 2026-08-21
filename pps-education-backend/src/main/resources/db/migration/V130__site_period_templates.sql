-- =====================================================================
-- V127: Cau hinh "Tiet hoc theo diem truong" — moi hoan toan, bo sung
-- ngoai SDD/SRS goc, xac nhan voi nguoi dung 2026-08-19. Moi diem truong
-- (site) tu dinh nghia rieng danh sach tiet hoc co dinh (VD Tiet 1:
-- 07:00-07:45), thay cho co che chia deu theo phut cua
-- system_settings.academic.default_periods_per_session (xem V?? cu, gio
-- khong con dung). Xem docs/uc/phan-he-06-hoc-thuat.md (UC-48/56/57).
-- =====================================================================

CREATE TABLE site_period_templates (
    id             BIGSERIAL PRIMARY KEY,
    uuid           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    site_id        BIGINT NOT NULL REFERENCES sites(id),
    period_number  INT NOT NULL,
    label          VARCHAR(50),
    start_time     TIME NOT NULL,
    end_time       TIME NOT NULL,
    created_by     BIGINT NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_site_period_template_unique
    ON site_period_templates(site_id, period_number)
    WHERE deleted_at IS NULL;

-- Khong them permission moi — CRUD tiet hoc dung lai facility.site.update
-- (cung trang "Diem truong", cung nhom actor Quan ly diem truong/OPS),
-- gate truc tiep o SitePeriodTemplateController.
