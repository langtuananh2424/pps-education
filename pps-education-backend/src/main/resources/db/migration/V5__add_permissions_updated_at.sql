-- =====================================================================
-- V5: Bổ sung ngoài SDD gốc — bảng permissions (V1) thiếu cột updated_at
-- so với quy ước BaseAuditEntity (created_at/updated_at) mà entity Permission
-- đang kế thừa. Không sửa V1 vì migration đó có thể đã chạy ở môi trường khác.
-- =====================================================================

ALTER TABLE permissions
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
