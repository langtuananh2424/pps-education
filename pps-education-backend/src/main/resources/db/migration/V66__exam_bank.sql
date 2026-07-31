-- UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
-- tái cấu trúc "Soạn & Giao đề" thành Kho đề 2 cấp — "Đề" (exams, VD:
-- IELTS Grade 6) chứa nhiều "Bài" (exercises, VD: Unit 1, đã có sẵn).
-- curriculum_id trên exams CHỈ dùng lọc/tìm kiếm trong Kho đề — điều kiện
-- hiển thị cho học sinh DUY NHẤT là Đề đã gán cho lớp
-- (exam_class_assignments), không còn khớp khung chương trình như trước.
-- Áp dụng cho MỌI exercise_type (không chỉ ASSIGNED) — SELF_PRACTICE mất
-- hẳn cơ chế "mở tự do sau khi Publish" (thay đổi hành vi UC-27 thật sự,
-- xem docs/uc/phan-he-07-lms-portal.md).

CREATE TABLE exams (
    id            BIGSERIAL PRIMARY KEY,
    uuid          UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code          VARCHAR(50) UNIQUE NOT NULL,
    title         VARCHAR(500) NOT NULL,
    curriculum_id BIGINT NOT NULL REFERENCES curriculums(id),
    created_by    BIGINT NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_exams_curriculum ON exams(curriculum_id);

-- Join thuần (không phải "bản giao" cần lưu lịch sử như exercise_assignments)
-- — gỡ lớp khỏi Đề là xóa cứng dòng này, không có status/uuid.
CREATE TABLE exam_class_assignments (
    id          BIGSERIAL PRIMARY KEY,
    exam_id     BIGINT NOT NULL REFERENCES exams(id),
    class_id    BIGINT NOT NULL REFERENCES classes(id),
    assigned_by BIGINT NOT NULL REFERENCES users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (exam_id, class_id)
);
CREATE INDEX idx_exam_class_assignments_class ON exam_class_assignments(class_id);

ALTER TABLE exercises ADD COLUMN exam_id BIGINT NULL REFERENCES exams(id);

-- Backfill: mỗi Exercise cũ (dữ liệu test, Sprint 0/1 — đã xác nhận với
-- người dùng không cần xử lý phức tạp) -> 1 Đề mặc định 1-1, giữ nguyên
-- dữ liệu thay vì xóa/bỏ qua. exercises.code vốn UNIQUE nên tái dùng làm
-- exams.code an toàn, không đụng ràng buộc UNIQUE nào.
INSERT INTO exams (code, title, curriculum_id, created_by, created_at, updated_at)
SELECT ex.code, ex.title, ex.curriculum_id, ex.created_by, ex.created_at, ex.updated_at
FROM exercises ex;

UPDATE exercises ex SET exam_id = exam.id
FROM exams exam WHERE exam.code = ex.code;

ALTER TABLE exercises ALTER COLUMN exam_id SET NOT NULL;
ALTER TABLE exercises DROP COLUMN curriculum_id;

-- Lớp đã có bản giao ACTIVE cho 1 Exercise coi như Đề cha đã "gán ngầm"
-- cho lớp đó — tránh chặn nhầm việc giao lại qua UC-21 sau khi đổi cơ chế
-- kiểm tra phạm vi từ "khớp khung chương trình" sang "Đề đã gán lớp".
INSERT INTO exam_class_assignments (exam_id, class_id, assigned_by, assigned_at)
SELECT DISTINCT ex.exam_id, ea.class_id, ea.assigned_by, ea.available_from
FROM exercise_assignments ea
JOIN exercises ex ON ex.id = ea.exercise_id
WHERE ea.status = 'ACTIVE'
ON CONFLICT (exam_id, class_id) DO NOTHING;
