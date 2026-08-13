-- Bo sung ngoai SDD goc, xac nhan 2026-08-13 (UC-18, docs/uc/phan-he-06-hoc-thuat.md):
-- Them loai giao vien (VIETNAMESE/FOREIGN) cho giao vien CHINH cua lop, de
-- phan biet GV chinh nguoi Viet Nam va GV chinh nguoi nuoc ngoai cua cung 1
-- lop (truoc day chi cho phep 1 PRIMARY active/lop/subject). Vai tro CM
-- (Class Manager) duoc them nhu 1 gia tri teacher_role moi (khong tao role
-- he thong moi trong bang roles). Du lieu PRIMARY hien co duoc de
-- teacher_type = NULL, khong tu doan VN/nuoc ngoai -- giao vu can gan lai
-- thu cong qua UC-18.

ALTER TABLE class_teachers ADD COLUMN teacher_type VARCHAR(20) NULL; -- VIETNAMESE / FOREIGN, chi co y nghia voi teacher_role='PRIMARY'

DROP INDEX idx_class_teacher_primary_active;

-- Cho phep dong thoi 1 PRIMARY active loai VIETNAMESE + 1 PRIMARY active
-- loai FOREIGN cho cung (class_id, subject_id). COALESCE(teacher_type,'NONE')
-- de giu dung invariant "1 PRIMARY active/lop/subject" cho du lieu cu chua
-- gan teacher_type (Postgres partial unique index khong so sanh duoc NULL).
CREATE UNIQUE INDEX idx_class_teacher_primary_active
    ON class_teachers(class_id, COALESCE(subject_id, 0), COALESCE(teacher_type, 'NONE'))
    WHERE teacher_role = 'PRIMARY' AND assigned_to IS NULL;
