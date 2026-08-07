-- =====================================================================
-- V101: PHAN HE 6 - ACADEMIC YEAR TREN GRADE_ENTRIES / STUDENT_COMMENTS /
-- CLASS_ENROLLMENTS
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-07.
-- Denormalize academic_year tu classes.academic_year (copy tai thoi diem
-- tao record, cung pattern voi classes.class_category copy tu curriculum)
-- de UI hoc sinh/phu huynh loc/thong ke diem, nhan xet, lich su ghi danh
-- theo nam hoc ma khong can join qua classes/academic_terms.
-- =====================================================================

ALTER TABLE grade_entries ADD COLUMN academic_year VARCHAR(20) NULL;
ALTER TABLE student_comments ADD COLUMN academic_year VARCHAR(20) NULL;
ALTER TABLE class_enrollments ADD COLUMN academic_year VARCHAR(20) NULL;

-- Backfill du lieu da co tu classes.academic_year qua class_id
UPDATE grade_entries ge
SET academic_year = c.academic_year
FROM classes c
WHERE ge.class_id = c.id AND c.academic_year IS NOT NULL;

UPDATE student_comments sc
SET academic_year = c.academic_year
FROM classes c
WHERE sc.class_id = c.id AND c.academic_year IS NOT NULL;

UPDATE class_enrollments ce
SET academic_year = c.academic_year
FROM classes c
WHERE ce.class_id = c.id AND c.academic_year IS NOT NULL;

-- Index phuc vu query "diem/nhan xet/lich su cua 1 hoc sinh theo nam hoc"
CREATE INDEX idx_grade_entries_student_academic_year ON grade_entries(student_id, academic_year);
CREATE INDEX idx_student_comments_student_academic_year ON student_comments(student_id, academic_year);
CREATE INDEX idx_class_enrollments_student_academic_year ON class_enrollments(student_id, academic_year);
