-- =====================================================================
-- V27: PHAN HE 10 (phan con lai) - UC-36b (hop dong lien ket truong),
-- UC-37 (phong hoc & thiet bi), UC-38/39 (phan hoi truong lien ket)
--
-- Bang rooms/equipment/partner_contracts/partner_feedbacks da co san tu
-- V2 (keo som len Phase A) nhung chua tung co Controller/Service (chi
-- Room duoc dung toi thieu qua FK tu class_sessions - FR-FAC-03). V27 chi
-- bo sung 4 bang history ma SDD (docs/sdd-groups/03-co-so-vat-chat-and-diem-truong.md)
-- co ghi ro "Co X_history" nhung chua ai tao - phat hien khi doc lai SDD
-- truoc khi code (giong cach da phat hien gap sites_history o V24).
-- Pattern JSONB diff-log giong sites_history (V24).
-- =====================================================================

CREATE TABLE rooms_history (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL REFERENCES rooms(id),
    changed_by  BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(20) NOT NULL,
    details     JSONB NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rooms_history_room ON rooms_history(room_id);

CREATE TABLE partner_school_info_history (
    id                    BIGSERIAL PRIMARY KEY,
    partner_school_info_id BIGINT NOT NULL REFERENCES partner_school_info(id),
    changed_by            BIGINT NOT NULL REFERENCES users(id),
    action                VARCHAR(20) NOT NULL,
    details               JSONB NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_partner_school_info_history_info ON partner_school_info_history(partner_school_info_id);

CREATE TABLE partner_contracts_history (
    id           BIGSERIAL PRIMARY KEY,
    contract_id  BIGINT NOT NULL REFERENCES partner_contracts(id),
    changed_by   BIGINT NOT NULL REFERENCES users(id),
    action       VARCHAR(20) NOT NULL,
    details      JSONB NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_partner_contracts_history_contract ON partner_contracts_history(contract_id);

-- UC-39 A1: toan bo lich su trao doi qua lai giua Quan ly diem truong va
-- Dai dien truong lien ket duoc luu vao day (action=EXCHANGE), khong chi
-- CREATED/UPDATED nhu cac bang history khac.
CREATE TABLE partner_feedbacks_history (
    id           BIGSERIAL PRIMARY KEY,
    feedback_id  BIGINT NOT NULL REFERENCES partner_feedbacks(id),
    changed_by   BIGINT NOT NULL REFERENCES users(id),
    action       VARCHAR(20) NOT NULL, -- CREATED / UPDATED / EXCHANGE
    details      JSONB NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_partner_feedbacks_history_feedback ON partner_feedbacks_history(feedback_id);
