-- =====================================================================
-- V26: PHAN HE 9 - TUYEN SINH & CRM (UC-33 quan ly lead & tu van tuyen
-- sinh, UC-34 chuyen doi lead thanh hoc sinh, UC-35 nhap hoc theo lo cho
-- lop lien ket - tai su dung import_jobs da co san tu V1, khong can bang
-- moi)
-- =====================================================================

-- a) lead_sources - Danh muc nguon lead, khong history
CREATE TABLE lead_sources (
    id                 BIGSERIAL PRIMARY KEY,
    code               VARCHAR(50) UNIQUE NOT NULL,
    name               VARCHAR(200) NOT NULL,
    channel_type       VARCHAR(30) NOT NULL, -- WEBSITE / SOCIAL / HOTLINE / MESSAGING / PARTNER_FORM / OFFLINE / OTHER
    referrer_site_id   BIGINT NULL REFERENCES sites(id), -- Chi set neu channel_type=PARTNER_FORM
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_partner_source CHECK (
        (channel_type = 'PARTNER_FORM' AND referrer_site_id IS NOT NULL) OR
        (channel_type != 'PARTNER_FORM')
    )
);

INSERT INTO lead_sources (code, name, channel_type) VALUES
('WEBSITE', 'Website PPS English', 'WEBSITE'),
('FANPAGE_MAIN', 'Fanpage Facebook chính', 'SOCIAL'),
('HOTLINE', 'Hotline tư vấn', 'HOTLINE'),
('ZALO_OA', 'Zalo Official Account', 'MESSAGING');

-- b) leads - Bang trung tam, gop thong tin lien he + hoc sinh quan tam +
-- trang thai xu ly + ket qua chuyen doi. Co leads_history.
CREATE TABLE leads (
    id                         BIGSERIAL PRIMARY KEY,
    uuid                       UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    lead_code                  VARCHAR(50) UNIQUE NOT NULL,
    full_name                  VARCHAR(200) NOT NULL,
    phone                      VARCHAR(20) NOT NULL,
    email                      VARCHAR(255) NULL,
    contact_relationship       VARCHAR(30) NULL, -- SELF / FATHER / MOTHER / GUARDIAN / OTHER
    student_name               VARCHAR(200) NULL,
    student_dob                DATE NULL,
    student_grade              VARCHAR(50) NULL,
    student_current_school     VARCHAR(300) NULL,
    lead_source_id             BIGINT NOT NULL REFERENCES lead_sources(id),
    interested_site_id         BIGINT NULL REFERENCES sites(id),
    interested_curriculum_id   BIGINT NULL REFERENCES curriculums(id),
    initial_message            TEXT NULL,
    status                     VARCHAR(20) NOT NULL DEFAULT 'NEW', -- NEW / CONTACTED / QUALIFIED / WON / LOST / DUPLICATE
    outcome                    VARCHAR(30) NULL, -- WON_ENROLLED / LOST_PRICE / LOST_LOCATION / LOST_TIMING / LOST_NO_INTEREST / LOST_OTHER
    final_note                 TEXT NULL,
    assigned_to                BIGINT NULL REFERENCES users(id),
    assigned_at                TIMESTAMPTZ NULL,
    converted_student_id       BIGINT NULL REFERENCES students(id),
    converted_at               TIMESTAMPTZ NULL,
    converted_by               BIGINT NULL REFERENCES users(id),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                 TIMESTAMPTZ NULL
);

-- Rang buoc UNIQUE tren phone (loai tru da xoa) - tu dong phat hien lead
-- trung, khong cho 2 lead active cung so dien thoai (UC-33 A1).
CREATE UNIQUE INDEX idx_leads_phone ON leads(phone) WHERE deleted_at IS NULL;
CREATE INDEX idx_leads_status ON leads(status, created_at DESC) WHERE status IN ('NEW','CONTACTED','QUALIFIED');

CREATE TABLE leads_history (
    id          BIGSERIAL PRIMARY KEY,
    lead_id     BIGINT NOT NULL REFERENCES leads(id),
    changed_by  BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(20) NOT NULL,
    details     JSONB NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_leads_history_lead ON leads_history(lead_id);
