-- =====================================================================
-- V2: CƠ SỞ VẬT CHẤT & ĐIỂM TRƯỜNG (dữ liệu nền bắt buộc cho Phân hệ 6/Xếp lớp)
-- Kéo sớm lên Phase A theo đánh giá phụ thuộc dữ liệu (xem tài liệu kế hoạch)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS postgis; -- geography(POINT,4326) cho validate GPS chấm công

-- a) sites -- Điểm trường (thống nhất OWNED/PARTNER)
CREATE TABLE sites (
    id             BIGSERIAL PRIMARY KEY,
    uuid           UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code           VARCHAR(50) UNIQUE NOT NULL,
    name           VARCHAR(200) NOT NULL,
    site_type      VARCHAR(20) NOT NULL CHECK (site_type IN ('OWNED', 'PARTNER')),
    address        VARCHAR(500) NULL,
    district       VARCHAR(100) NULL,
    phone          VARCHAR(20) NULL,
    geo_location   GEOGRAPHY(POINT, 4326) NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / INACTIVE / PENDING
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- b) partner_school_info -- Thông tin liên hệ trường liên kết (1-1 với sites khi PARTNER)
CREATE TABLE partner_school_info (
    id                    BIGSERIAL PRIMARY KEY,
    site_id               BIGINT UNIQUE NOT NULL REFERENCES sites(id),
    contact_person_name   VARCHAR(200) NULL,
    contact_person_title  VARCHAR(100) NULL,
    contact_phone         VARCHAR(20) NULL,
    contact_email         VARCHAR(255) NULL,
    additional_info       TEXT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- c) partner_contracts -- Hợp đồng liên kết (UC-36b)
CREATE TABLE partner_contracts (
    id                    BIGSERIAL PRIMARY KEY,
    uuid                  UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    site_id               BIGINT NOT NULL REFERENCES sites(id),
    contract_number       VARCHAR(100) UNIQUE NOT NULL,
    contract_type         VARCHAR(30) NOT NULL, -- INITIAL / RENEWAL / AMENDMENT
    parent_contract_id    BIGINT NULL REFERENCES partner_contracts(id),
    start_date            DATE NOT NULL,
    end_date              DATE NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / ACTIVE / EXPIRED / TERMINATED
    terms_summary         TEXT NULL,
    file_url              VARCHAR(500) NULL,
    signed_at             DATE NULL,
    signed_by_center      VARCHAR(200) NULL,
    signed_by_partner     VARCHAR(200) NULL,
    revenue_share_notes   TEXT NULL,
    created_by            BIGINT NULL REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ NULL,
    CONSTRAINT chk_contract_dates CHECK (end_date > start_date)
);
CREATE UNIQUE INDEX idx_partner_contracts_active ON partner_contracts(site_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- d) site_managers -- Gán Quản lý điểm trường (UC-36)
CREATE TABLE site_managers (
    id             BIGSERIAL PRIMARY KEY,
    site_id        BIGINT NOT NULL REFERENCES sites(id),
    user_id        BIGINT NOT NULL REFERENCES users(id),
    assigned_from  DATE NOT NULL,
    assigned_to    DATE NULL, -- NULL = đang phụ trách
    assigned_by    BIGINT NOT NULL REFERENCES users(id),
    notes          TEXT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_site_managers_active ON site_managers(site_id) WHERE assigned_to IS NULL;

-- e) rooms -- Phòng học (FR-FAC-04, ràng buộc trùng phòng FR-FAC-03)
CREATE TABLE rooms (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    site_id             BIGINT NOT NULL REFERENCES sites(id),
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(200) NULL,
    room_type           VARCHAR(30) NOT NULL, -- THEORY / COMPUTER / LAB / OTHER
    capacity            INT NOT NULL CHECK (capacity > 0),
    is_flexible         BOOLEAN NOT NULL DEFAULT FALSE, -- TRUE = loại trừ khỏi cảnh báo trùng phòng
    managed_by_center   BOOLEAN NOT NULL DEFAULT TRUE,
    status              VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE / MAINTENANCE / DISABLED
    notes               TEXT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (site_id, code)
);

-- f) equipment -- Thiết bị dạy học (FR-FAC-02)
CREATE TABLE equipment (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    room_id         BIGINT NULL REFERENCES rooms(id),
    code            VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    equipment_type  VARCHAR(30) NOT NULL, -- PROJECTOR / SPEAKER / MIC / COMPUTER / OTHER
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE / IN_USE / MAINTENANCE / BROKEN
    notes           TEXT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- g) partner_feedbacks -- Kênh phản hồi từ trường liên kết (UC-38/UC-39)
CREATE TABLE partner_feedbacks (
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    site_id            BIGINT NOT NULL REFERENCES sites(id),
    submitted_by       BIGINT NOT NULL REFERENCES users(id),
    content            TEXT NOT NULL,
    feedback_type      VARCHAR(30) NOT NULL, -- TEACHER / CLASS / OPERATIONS / OTHER
    priority           VARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- LOW / NORMAL / HIGH / URGENT
    status             VARCHAR(20) NOT NULL DEFAULT 'NEW', -- NEW / IN_PROGRESS / RESOLVED / CLOSED
    assigned_to        BIGINT NULL REFERENCES users(id),
    resolution_notes   TEXT NULL,
    resolved_at        TIMESTAMPTZ NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
