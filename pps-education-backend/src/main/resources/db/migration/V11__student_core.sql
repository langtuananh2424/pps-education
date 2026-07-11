-- =====================================================================
-- V11: PHÂN HỆ 5 - HỒ SƠ HỌC SINH (UC-13 FR-STU-01, UC-14 FR-STU-02)
-- Nền tảng bắt buộc trước khi Phân hệ 6 (Học thuật, classes/class_enrollments)
-- có thể xây tiếp UC-42 (Chọn lớp đang xem).
-- =====================================================================

-- a) students -- Hồ sơ điện tử học sinh (UC-13)
CREATE TABLE students (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT UNIQUE NOT NULL REFERENCES users(id),
    student_code      VARCHAR(20) UNIQUE NOT NULL, -- VD HS2026-0001, hệ thống tự sinh (UC-13 Main Flow bước 3)
    date_of_birth     DATE NOT NULL,
    gender            VARCHAR(10) NULL, -- MALE / FEMALE / OTHER
    portrait_url      VARCHAR(500) NULL,
    primary_site_id   BIGINT NULL REFERENCES sites(id),
    original_school   VARCHAR(200) NULL,
    original_class    VARCHAR(50) NULL,
    -- ACTIVE / SUSPENDED / EXPELLED / GRADUATED / WITHDRAWN / DEFERRAL
    -- (DEFERRAL bổ sung ngoài SDD gốc cho UC-14 "Bảo lưu" - xem docs/sdd-groups/04-hoc-sinh-and-phu-huynh.md)
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    enrollment_date   DATE NOT NULL,
    graduation_date   DATE NULL,
    notes             TEXT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ NULL
);

-- b) parents -- Hồ sơ phụ huynh (UC-13 Main Flow bước 2)
CREATE TABLE parents (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT UNIQUE NOT NULL REFERENCES users(id),
    occupation    VARCHAR(200) NULL,
    workplace     VARCHAR(500) NULL,
    address       VARCHAR(500) NULL,
    notes         TEXT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- c) parent_student -- Liên kết Phụ huynh <-> Học sinh (M-N có thuộc tính)
CREATE TABLE parent_student (
    id                          BIGSERIAL PRIMARY KEY,
    parent_id                   BIGINT NOT NULL REFERENCES parents(id),
    student_id                  BIGINT NOT NULL REFERENCES students(id),
    relationship                VARCHAR(20) NOT NULL, -- FATHER / MOTHER / GUARDIAN / OTHER
    is_primary_contact          BOOLEAN NOT NULL DEFAULT FALSE,
    is_financial_responsible    BOOLEAN NOT NULL DEFAULT FALSE,
    notes                       TEXT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(parent_id, student_id)
);
-- Mỗi học sinh tối đa 1 primary contact và tối đa 1 người chịu trách nhiệm tài chính (SDD)
CREATE UNIQUE INDEX idx_parent_student_primary ON parent_student(student_id) WHERE is_primary_contact = TRUE;
CREATE UNIQUE INDEX idx_parent_student_financial ON parent_student(student_id) WHERE is_financial_responsible = TRUE;

-- d) student_status_history -- Lịch sử thay đổi trạng thái học tập (UC-14 Main Flow bước 4)
CREATE TABLE student_status_history (
    id                BIGSERIAL PRIMARY KEY,
    student_id        BIGINT NOT NULL REFERENCES students(id),
    old_status        VARCHAR(20) NULL, -- NULL nếu là record đầu tiên
    new_status        VARCHAR(20) NOT NULL,
    reason            TEXT NULL,
    effective_date    DATE NOT NULL,
    changed_by        BIGINT NOT NULL REFERENCES users(id),
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_student_status_history_student ON student_status_history(student_id, changed_at DESC);

-- e) student_transfer_history -- Lịch sử chuyển lớp/chuyển điểm trường (UC-13 Main Flow bước 4, A1)
-- from_class_id/to_class_id: KHÔNG có FK constraint tới classes(id) vì bảng classes
-- (Phân hệ 6 - Học thuật) chưa migrate. Sẽ bổ sung FK bằng migration mới (ALTER TABLE)
-- khi Phân hệ 6 triển khai - không sửa lại file migration này (theo CONTRIBUTING.md).
CREATE TABLE student_transfer_history (
    id                BIGSERIAL PRIMARY KEY,
    student_id        BIGINT NOT NULL REFERENCES students(id),
    transfer_type     VARCHAR(20) NOT NULL, -- CLASS_CHANGE / SITE_CHANGE / BOTH
    from_class_id     BIGINT NULL,
    to_class_id       BIGINT NULL,
    from_site_id      BIGINT NULL REFERENCES sites(id),
    to_site_id        BIGINT NULL REFERENCES sites(id),
    effective_date    DATE NOT NULL,
    reason            TEXT NULL,
    approved_by       BIGINT NOT NULL REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transfer_history_student ON student_transfer_history(student_id, effective_date DESC);

-- f) students_history / parents_history -- SDD (ERD-Nhom3-HocSinhPhuHuynh.mmd) chỉ ghi
-- "Có students_history"/"Có parents_history" mà không định nghĩa cột — áp dụng lại đúng
-- pattern JSONB diff-log đã dùng ở employees_history (V6), đã xác nhận với PM.
CREATE TABLE students_history (
    id            BIGSERIAL PRIMARY KEY,
    student_id    BIGINT NOT NULL REFERENCES students(id),
    changed_by    BIGINT NOT NULL REFERENCES users(id),
    action        VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details       JSONB NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_students_history_student ON students_history(student_id);

CREATE TABLE parents_history (
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT NOT NULL REFERENCES parents(id),
    changed_by    BIGINT NOT NULL REFERENCES users(id),
    action        VARCHAR(20) NOT NULL, -- CREATED / UPDATED
    details       JSONB NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_parents_history_parent ON parents_history(parent_id);

-- g) Quyền student.manage (UC-13 Precondition: Nhân viên Giáo vụ hoặc Quản lý điểm trường)
INSERT INTO permissions (code, name, module, description) VALUES
('student.manage', 'Quản lý hồ sơ học sinh', 'STUDENT', 'FR-STU-01');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('STAFF', 'SITE_MANAGER') AND p.code = 'student.manage';
