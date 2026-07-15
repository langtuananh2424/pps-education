-- =====================================================================
-- V32: UC-47 (Khoa/Mo khoa tai khoan, FR-USR-04) - bang users_history.
--
-- SDD (02-nen-tang.md, bang users): "Khong soft-delete -- dung
-- status='INACTIVE' de vo hieu hoa. Co bang users_history." nhung chua
-- migration nao tung tao bang nay - bo sung o day, cung pattern voi
-- roles_history (V31).
--
-- user_id NOT NULL (khac roles_history.role_id nullable): users khong
-- bao gio hard-delete (chi doi status), nen FK luon con nguyen ven,
-- khong can ON DELETE SET NULL.
-- =====================================================================

CREATE TABLE users_history (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    changed_by   BIGINT NOT NULL REFERENCES users(id),
    action       VARCHAR(20) NOT NULL, -- STATUS_CHANGED
    details      JSONB NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_history_user ON users_history(user_id);
