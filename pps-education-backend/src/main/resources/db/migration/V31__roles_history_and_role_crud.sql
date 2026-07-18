-- =====================================================================
-- V31: UC-03 bo sung - tao/xoa vai tro (nhom quyen) tuy chinh qua UI.
--
-- SDD (02-nen-tang.md, bang roles): is_system = TRUE cho 11 role goc,
-- "khong cho xoa qua UI" - ngu y role is_system=FALSE (tuy chinh) XOA
-- duoc. SDD cung ghi "Co bang roles_history" nhung chua migration nao
-- tung tao bang nay - bo sung o day.
--
-- role_id NULL + ON DELETE SET NULL (khac cac *_history khac NOT NULL):
-- xoa vai tro la hard-delete that (khong soft-delete nhu partner_contracts),
-- nen history phai song sot doc lap voi role bi xoa - snapshot code/name
-- luu trong details JSONB thay vi dua vao FK con nguyen ven.
-- =====================================================================

CREATE TABLE roles_history (
    id           BIGSERIAL PRIMARY KEY,
    role_id      BIGINT NULL REFERENCES roles(id) ON DELETE SET NULL,
    changed_by   BIGINT NOT NULL REFERENCES users(id),
    action       VARCHAR(20) NOT NULL, -- CREATED / DELETED
    details      JSONB NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_roles_history_role ON roles_history(role_id);
