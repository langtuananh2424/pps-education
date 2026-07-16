-- Gán giáo viên (nhân viên) vào điểm trường — 1 giáo viên có thể được gán
-- vào nhiều điểm trường, áp dụng cho mọi loại site (OWNED lẫn PARTNER).
-- Không dùng chung site_managers vì đó là bảng cho vai trò QUẢN LÝ (1 active
-- SITE_MANAGER + 1 PARTNER_REP mỗi site — unique theo site_id/role_type),
-- còn site_teachers là quan hệ N-N thuần (nhiều giáo viên/site, nhiều
-- site/giáo viên), không có khái niệm "vai trò" hay giới hạn số lượng.
-- assigned_from/assigned_to tự thân là lịch sử (giống site_managers), không
-- cần bảng history riêng.
CREATE TABLE site_teachers (
    id                BIGSERIAL PRIMARY KEY,
    site_id           BIGINT NOT NULL REFERENCES sites(id),
    teacher_user_id   BIGINT NOT NULL REFERENCES users(id),
    assigned_from     DATE NOT NULL,
    assigned_to       DATE NULL, -- NULL = đang được gán tại điểm trường này
    assigned_by       BIGINT NOT NULL REFERENCES users(id),
    notes             TEXT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 1 giáo viên chỉ có 1 bản ghi active cho mỗi site (tránh gán trùng).
CREATE UNIQUE INDEX idx_site_teachers_active ON site_teachers(site_id, teacher_user_id)
    WHERE assigned_to IS NULL;

-- Tra cứu nhanh "danh sách site đang được gán" của 1 giáo viên (dùng để lọc
-- API liệt kê lớp học/điểm danh theo site).
CREATE INDEX idx_site_teachers_teacher_active ON site_teachers(teacher_user_id)
    WHERE assigned_to IS NULL;
