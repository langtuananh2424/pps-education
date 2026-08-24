-- =====================================================================
-- V144: Kho de - chuyen nhom ky nang + nguong dat/luot lam lai tu cap Bai
-- (exercises) len cap De (exams) - bo sung ngoai SDD goc, da xac nhan voi
-- nguoi dung 2026-08-24. Ly do nghiep vu: giao bai gio giao CA DE (tat ca
-- Bai trong De cung luc), nen 1 De phai la 1 nhom ky nang thuan (Reading/
-- Writing/Vocab & Grammar/Listening) va cau hinh dat/lam lai phai ap dung
-- chung cho ca De, khong con hop ly o cap tung Bai rieng le.
-- =====================================================================

-- a) exams.skill_category - moi Bai trong 1 De se dong nhat 1 nhom ky nang
-- (khac voi truoc day tung Bai tu chon rieng, xem exercises.skill_category
-- cu, V136/V142). Backfill tu nhom ky nang PHO BIEN NHAT trong cac Bai da
-- co san cua tung De (De chua Bai nao tung phan loai thi giu NULL - "chua
-- phan loai", giong hanh vi cu).
ALTER TABLE exams ADD COLUMN skill_category VARCHAR(20);

WITH ranked AS (
    SELECT exam_id, skill_category, COUNT(*) AS cnt,
           ROW_NUMBER() OVER (PARTITION BY exam_id ORDER BY COUNT(*) DESC) AS rn
    FROM exercises
    WHERE skill_category IS NOT NULL
    GROUP BY exam_id, skill_category
)
UPDATE exams e
SET skill_category = r.skill_category
FROM ranked r
WHERE r.exam_id = e.id AND r.rn = 1;

COMMENT ON COLUMN exams.skill_category IS 'Nhom ky nang cua De: READING/WRITING/VOCAB_GRAMMAR/LISTENING, NULL = chua phan loai (du lieu cu). Bat buoc chon khi tao De moi (validate o CreateExamRequest) - xem Exam.SkillCategory.';

-- b) exams.pass_threshold_percent/allow_retake/max_attempts - chuyen tu
-- exercises (V89/V18), ap dung CHUNG cho tat ca Bai trong De khi tinh
-- dat/khong dat + luot lam lai (xem ExerciseAttemptService#applyPassOutcome).
ALTER TABLE exams
    ADD COLUMN pass_threshold_percent DECIMAL(5,2) NOT NULL DEFAULT 70.00,
    ADD COLUMN allow_retake           BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN max_attempts           INT NULL;

-- c) go cac cot tuong ung o exercises - chuyen han len exams, khong con y
-- nghia o cap tung Bai.
ALTER TABLE exercises
    DROP COLUMN skill_category,
    DROP COLUMN pass_threshold_percent,
    DROP COLUMN allow_retake,
    DROP COLUMN max_attempts;

-- d) student_comments - kenh Ngu phap/Reading/Writing o Nhan xet hoc vien
-- (UC-21) gio chon 1 De (giao CA De) thay vi 1 Bai le - cot MOI song song
-- voi homework_next_exercise_assignment_id/homework_next_reading_exercise_
-- assignment_id/homework_next_writing_exercise_assignment_id cu (VAN GIU
-- nguyen, dung cho duong "giao le 1 Bai" nang cao khi can - xem Javadoc
-- StudentCommentService). Committed (sau Gui) tro thang Exam; pending
-- (con Nhap/Bi tu choi) chi luu id tho, mirror pending_homework_next_
-- exercise_id da co san.
ALTER TABLE student_comments
    ADD COLUMN homework_next_exam_id                 BIGINT NULL REFERENCES exams(id),
    ADD COLUMN homework_next_reading_exam_id         BIGINT NULL REFERENCES exams(id),
    ADD COLUMN homework_next_writing_exam_id         BIGINT NULL REFERENCES exams(id),
    ADD COLUMN pending_homework_next_exam_id         BIGINT NULL,
    ADD COLUMN pending_homework_next_reading_exam_id BIGINT NULL,
    ADD COLUMN pending_homework_next_writing_exam_id BIGINT NULL;
