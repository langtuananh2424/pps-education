-- =====================================================================
-- V225: cau hinh bo cham phat am PPS Hybrid v2 chinh duoc qua trang Quan tri he thong.
--
-- pps_scoring_config: DUNG MOT dong (id = 1). overrides chi luu cac tham so KHAC mac dinh, dang phang
-- {"word.ending_penalty": 2.0}; rong {} nghia la dung mac dinh cua dac ta. Gia tri mac dinh va khoang cho phep
-- nam trong ma (PpsConfig), nen doi mac dinh o phien ban sau khong can migration va khong lam hong du lieu cu.
--
-- pps_scoring_config_history: moi lan luu ghi mot dong (gia tri truoc, gia tri sau, ai doi, ghi chu).
-- Quyen dung lai system.settings.view / system.settings.manage (V105, V202), khong them quyen moi.
-- =====================================================================

CREATE TABLE pps_scoring_config (
    id          SMALLINT PRIMARY KEY CHECK (id = 1),
    overrides   JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_by  BIGINT REFERENCES users(id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO pps_scoring_config (id) VALUES (1);

CREATE TABLE pps_scoring_config_history (
    id          BIGSERIAL PRIMARY KEY,
    previous    JSONB NOT NULL,
    overrides   JSONB NOT NULL,
    changed_by  BIGINT NOT NULL REFERENCES users(id),
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pps_scoring_config_history_created ON pps_scoring_config_history(created_at DESC);
