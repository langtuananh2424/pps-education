-- =====================================================================
-- V103: DANH MUC "academic_years" + CHUYEN 5 COT academic_year (VARCHAR)
-- SANG FK academic_year_id
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-07.
-- academic_years la danh muc DUNG CHUNG TOAN HE THONG (khong site-scoped,
-- khac AcademicTerm). Chuyen doi 5 bang: classes, grade_entries,
-- student_comments, class_enrollments (V102), va teaching_plans (V21).
-- =====================================================================

CREATE TABLE academic_years (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    code VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_by BIGINT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Backfill danh muc tu toan bo gia tri chuoi da dung (DISTINCT qua UNION),
-- danh dau ACTIVE vi day la nam hoc da/dang dung, khong phai ke hoach tuong lai.
INSERT INTO academic_years (code, name, status)
SELECT v.code, v.code, 'ACTIVE'
FROM (
    SELECT DISTINCT academic_year AS code FROM classes WHERE academic_year IS NOT NULL
    UNION
    SELECT DISTINCT academic_year FROM grade_entries WHERE academic_year IS NOT NULL
    UNION
    SELECT DISTINCT academic_year FROM student_comments WHERE academic_year IS NOT NULL
    UNION
    SELECT DISTINCT academic_year FROM class_enrollments WHERE academic_year IS NOT NULL
    UNION
    SELECT DISTINCT academic_year FROM teaching_plans WHERE academic_year IS NOT NULL
) v;

-- classes
ALTER TABLE classes ADD COLUMN academic_year_id BIGINT REFERENCES academic_years(id);
UPDATE classes c SET academic_year_id = ay.id FROM academic_years ay WHERE c.academic_year = ay.code;
ALTER TABLE classes DROP COLUMN academic_year;

-- grade_entries
ALTER TABLE grade_entries ADD COLUMN academic_year_id BIGINT REFERENCES academic_years(id);
UPDATE grade_entries t SET academic_year_id = ay.id FROM academic_years ay WHERE t.academic_year = ay.code;
ALTER TABLE grade_entries DROP COLUMN academic_year;
CREATE INDEX idx_grade_entries_student_academic_year ON grade_entries(student_id, academic_year_id);

-- student_comments
ALTER TABLE student_comments ADD COLUMN academic_year_id BIGINT REFERENCES academic_years(id);
UPDATE student_comments t SET academic_year_id = ay.id FROM academic_years ay WHERE t.academic_year = ay.code;
ALTER TABLE student_comments DROP COLUMN academic_year;
CREATE INDEX idx_student_comments_student_academic_year ON student_comments(student_id, academic_year_id);

-- class_enrollments
ALTER TABLE class_enrollments ADD COLUMN academic_year_id BIGINT REFERENCES academic_years(id);
UPDATE class_enrollments t SET academic_year_id = ay.id FROM academic_years ay WHERE t.academic_year = ay.code;
ALTER TABLE class_enrollments DROP COLUMN academic_year;
CREATE INDEX idx_class_enrollments_student_academic_year ON class_enrollments(student_id, academic_year_id);

-- teaching_plans (V21, chi dung cho plan_type=YEARLY) -- chk_plan_period (V21)
-- tham chieu truc tiep cot academic_year nen phai drop constraint truoc,
-- tao lai theo academic_year_id sau khi chuyen cot.
ALTER TABLE teaching_plans DROP CONSTRAINT chk_plan_period;
ALTER TABLE teaching_plans ADD COLUMN academic_year_id BIGINT REFERENCES academic_years(id);
UPDATE teaching_plans t SET academic_year_id = ay.id FROM academic_years ay WHERE t.academic_year = ay.code;
ALTER TABLE teaching_plans DROP COLUMN academic_year;
ALTER TABLE teaching_plans ADD CONSTRAINT chk_plan_period CHECK (
    (plan_type = 'WEEKLY' AND week_start_date IS NOT NULL AND week_end_date IS NOT NULL) OR
    (plan_type = 'YEARLY' AND academic_year_id IS NOT NULL)
);
