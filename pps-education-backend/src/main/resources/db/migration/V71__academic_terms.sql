-- UC-18 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
-- "Giai đoạn/Học kỳ" (VD: Giữa kỳ 1, Cuối kỳ 1) là 1 khoảng thời gian có
-- tên, giới hạn theo ĐIỂM TRƯỜNG (mỗi trường mỗi khác lịch), KHÔNG gắn
-- cứng vào 1 lớp cụ thể — 1 lớp tồn tại xuyên suốt nhiều kỳ, sĩ số/giáo
-- viên phụ trách của lớp trong 1 kỳ được TÍNH RA (derived) từ các bảng đã
-- có sẵn ngày tháng (class_enrollments, class_teachers, class_sessions,
-- student_comments...) lọc theo khoảng [start_date, end_date] của kỳ,
-- không cần bảng snapshot/join riêng. Không có term_type phân loại
-- (Giữa kỳ/Cuối kỳ chỉ là quy ước đặt tên tự do qua "name").
CREATE TABLE academic_terms (
    id          BIGSERIAL PRIMARY KEY,
    uuid        UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    site_id     BIGINT NOT NULL REFERENCES sites(id),
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    created_by  BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (site_id, code)
);
CREATE INDEX idx_academic_terms_site ON academic_terms(site_id);

-- classes.semester (VARCHAR tự do, không có business logic nào đọc) bỏ hẳn,
-- thay bằng khái niệm academic_terms ở trên — không phải cột FK trên
-- classes vì 1 lớp không thuộc CỐ ĐỊNH 1 kỳ (đã xác nhận với người dùng:
-- ví dụ 1 học sinh đổi lớp 8A-1 -> 8A-2 -> 8A-1 giữa các kỳ trong 1 năm).
-- Dữ liệu lớp cũ để trống hoàn toàn (không tự convert semester cũ sang kỳ
-- mới) cho giáo vụ gán tay lại — đã xác nhận với người dùng.
ALTER TABLE classes DROP COLUMN semester;
