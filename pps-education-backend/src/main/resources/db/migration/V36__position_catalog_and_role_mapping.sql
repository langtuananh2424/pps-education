-- V36: Danh mục Chức vụ (positions) + ánh xạ role mặc định theo chức vụ
-- (position_default_roles) — bổ sung ngoài SDD gốc (employees.position_title
-- vốn là text tự do, không có danh mục). Xác nhận với người dùng ngày
-- 2026-07-17 sau khi phát hiện gap: tạo nhân viên qua UC-08 không hề tự gán
-- vai trò (role), phải thao tác thủ công qua UC-46. FR-HRM-06/UC-52 — xem
-- docs/uc/phan-he-04-nhan-su.md.

CREATE TABLE positions (
    id          BIGSERIAL PRIMARY KEY,
    uuid        UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE position_default_roles (
    id           BIGSERIAL PRIMARY KEY,
    position_id  BIGINT NOT NULL REFERENCES positions(id),
    role_id      BIGINT NOT NULL REFERENCES roles(id),
    UNIQUE (position_id, role_id)
);

-- employees.position_title (VARCHAR text tự do) -> position_id (FK danh mục
-- chuẩn hóa). Dự án chưa có dữ liệu production thật (Trạng thái hiện tại:
-- Sprint 0 + khung Sprint 1), không cố gắng map ngược text cũ sang danh mục
-- mới (không có quy tắc chuẩn để suy đoán) — nhân sự hiện có sẽ có
-- position_id = NULL, Quản lý nhân sự gán lại thủ công qua UC-08.
ALTER TABLE employees ADD COLUMN position_id BIGINT NULL REFERENCES positions(id);
ALTER TABLE employees DROP COLUMN position_title;

-- user_roles.granted_via_position_id -- đánh dấu role nào do hệ thống tự
-- gán theo chức vụ (khác role gán tay qua UC-46/FR-PER-05) để khi đổi chức
-- vụ (UC-08 A5) chỉ thu hồi đúng role hệ thống từng tự gán theo chức vụ cũ,
-- không đụng tới role gán tay.
ALTER TABLE user_roles ADD COLUMN granted_via_position_id BIGINT NULL REFERENCES positions(id);
