-- =====================================================================
-- V95: Kho Video On tap (UC-23/23a) - doi mo hinh gan lop giong het Kho de
-- (exams/exam_class_assignments, V66/V74) - bo sung ngoai SDD goc, da xac
-- nhan voi nguoi dung 2026-08-06.
--
-- Thay doi:
--   1. them teacher_type (VIETNAMESE/FOREIGN) cho review_video_sets, dung
--      de loc khi giao bai - mirror exams.teacher_type (V74).
--   2. bo han che "dung 1 trong 2: curriculum_id (chung) HOAC class_id
--      (rieng 1 lop)" (chk_review_video_set_scope) - moi Bo tu nay LUON
--      gan 1 curriculum_id (CHI de loc/tim kiem trong Kho Video, khong con
--      la dieu kien hien thi) + gan tuong minh N-N cho tung lop cu the qua
--      bang moi review_video_set_class_assignments (dieu kien hien thi
--      DUY NHAT cho hoc sinh cua lop do, mirror exam_class_assignments).
--
-- Backfill du lieu cu (Sprint 0/1, chua co du lieu that - xac nhan qua DB
-- dev, tuong tu ghi chu o V66):
--   - teacher_type: suy tu video_type hien co (CONNECTION=VIETNAMESE,
--     REFLEX=FOREIGN) - dung DUNG logic matchesSessionTeacherType cu dang
--     ap dung o StudentCommentService, giu nguyen hanh vi loc buoi hoc cho
--     du lieu cu thay vi mac dinh 1 gia tri co dinh.
--   - curriculum_id: Bo truoc day gan rieng 1 lop (class_id, curriculum_id
--     NULL) thi suy curriculum_id tu curriculum cua lop do.
--   - review_video_set_class_assignments: Bo rieng 1 lop -> gan dung lop
--     do; Bo dung chung theo khung chuong trinh -> gan tat ca lop dang
--     thuoc dung khung do tai thoi diem migrate (giu nguyen pham vi hien
--     thi hien tai, khong lam mat quyen xem cua lop nao).
-- =====================================================================

ALTER TABLE review_video_sets ADD COLUMN teacher_type VARCHAR(20);

UPDATE review_video_sets
SET teacher_type = CASE WHEN video_type = 'CONNECTION' THEN 'VIETNAMESE' ELSE 'FOREIGN' END;

ALTER TABLE review_video_sets ALTER COLUMN teacher_type SET NOT NULL;

CREATE TABLE review_video_set_class_assignments (
    id                   BIGSERIAL PRIMARY KEY,
    review_video_set_id  BIGINT NOT NULL REFERENCES review_video_sets(id),
    class_id             BIGINT NOT NULL REFERENCES classes(id),
    assigned_by          BIGINT NOT NULL REFERENCES users(id),
    assigned_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_video_set_id, class_id)
);
CREATE INDEX idx_review_video_set_class_assignments_class ON review_video_set_class_assignments(class_id);

-- Backfill gan lop: Bo rieng 1 lop -> gan dung lop do.
INSERT INTO review_video_set_class_assignments (review_video_set_id, class_id, assigned_by, assigned_at)
SELECT s.id, s.class_id, s.created_by, s.created_at
FROM review_video_sets s
WHERE s.class_id IS NOT NULL;

-- Backfill gan lop: Bo dung chung theo khung chuong trinh -> gan moi lop hien dang thuoc khung do.
INSERT INTO review_video_set_class_assignments (review_video_set_id, class_id, assigned_by, assigned_at)
SELECT s.id, c.id, s.created_by, s.created_at
FROM review_video_sets s
JOIN classes c ON c.curriculum_id = s.curriculum_id
WHERE s.curriculum_id IS NOT NULL;

-- Go CHECK scope cu TRUOC khi backfill curriculum_id cho Bo rieng lop (buoc duoi) - Bo dang co
-- class_id NOT NULL/curriculum_id NULL, backfill se lam CA HAI cung NOT NULL tam thoi (truoc khi
-- drop han class_id o cuoi file) nen phai go rang buoc XOR nay truoc, khong thi UPDATE se vi pham
-- chinh chk_review_video_set_scope (da xac nhan bug that qua log Flyway that bai tren Docker dev).
ALTER TABLE review_video_sets DROP CONSTRAINT chk_review_video_set_scope;

-- Backfill curriculum_id con thieu (Bo truoc day chi gan class_id).
UPDATE review_video_sets s
SET curriculum_id = c.curriculum_id
FROM classes c
WHERE s.class_id = c.id AND s.curriculum_id IS NULL;

ALTER TABLE review_video_sets ALTER COLUMN curriculum_id SET NOT NULL;
DROP INDEX IF EXISTS idx_review_video_sets_class;
ALTER TABLE review_video_sets DROP COLUMN class_id;

-- Permission gan/go lop (mirror lms.exam.assign, V67) - chi gan TEACHER, dung tien le V63/V67.
INSERT INTO permissions (code, name, module, description) VALUES
('lms.review-video.assign', 'Gán/gỡ bộ video ôn tập cho lớp', 'LMS', 'UC-23');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.review-video.assign' AND r.code = 'TEACHER';
