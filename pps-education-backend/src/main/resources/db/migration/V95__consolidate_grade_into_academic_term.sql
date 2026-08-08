-- =====================================================================
-- V95: PHAN HE 6 - CONSOLIDATE SO DIEM VAO ACADEMIC TERM + TICH HOP
-- NHAN XET KY (UC-16/17/19/20/53, bo sung ngoai SDD goc, da xac nhan voi
-- nguoi dung qua nhieu vong AskUserQuestion 2026-08-06).
--
-- Thay grade_periods/grade_components (gan curriculum_id, dung chung
-- nhieu lop qua 1 khung chuong trinh) bang grade_component_setups/
-- grade_evaluation_components (gan TRUC TIEP class_id + academic_term_id
-- + evaluation_type MID_TERM/END_TERM) - moi lop tu quyet dinh danh gia
-- Giua ky/Cuoi ky/ca hai qua tung setup rieng (VD lop bo tro chi tao 1
-- setup END_TERM, khong tao setup MID_TERM), khong con dung chung 1 cau
-- truc theo curriculum nua.
--
-- Doi ten grade_period_results -> grade_evaluation_results, them
-- comment (Nhan xet) + note (Ghi chu) - tich hop nhan xet ky vao so diem,
-- hien thi cho Phu huynh cung Overall/Level khi da OFFICIAL.
--
-- KHONG doi state machine duyet diem (DRAFT -> SUBMITTED -> OFFICIAL/
-- REJECTED, dung bang approval_flows co san tu V1) - da dung y nguoi
-- dung sau khi doi chieu code that (GradeService.java), giu nguyen 100%.
--
-- Khong co du lieu that can bao toan o grade_periods/grade_components/
-- grade_entries/grade_period_results (da xac nhan voi nguoi dung) -
-- TRUNCATE truoc khi ALTER de tranh vi pham NOT NULL/FK khi doi cau truc,
-- khong viet logic migrate du lieu.
-- =====================================================================

-- ===================== 1. Don du lieu cu (khong can bao toan) =====================
TRUNCATE TABLE grade_entries_history;
TRUNCATE TABLE grade_entries CASCADE;
TRUNCATE TABLE grade_period_results;
TRUNCATE TABLE grade_period_edit_windows;
UPDATE student_comments SET grade_period_id = NULL WHERE grade_period_id IS NOT NULL;

-- ===================== 2. Bang moi: grade_component_setups =====================
-- Thay grade_periods - gan truc tiep (class_id, academic_term_id,
-- evaluation_type) thay vi curriculum_id dung chung nhieu lop.
CREATE TABLE grade_component_setups (
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    class_id           BIGINT NOT NULL REFERENCES classes(id),
    academic_term_id   BIGINT NOT NULL REFERENCES academic_terms(id),
    evaluation_type    VARCHAR(20) NOT NULL, -- MID_TERM / END_TERM
    weight_in_final    DECIMAL(5,2) NULL,    -- trong so trong diem tong ket (VD MID_TERM=30, END_TERM=70)
    roster_as_of_date  DATE NOT NULL,        -- ngay chot danh sach HS (class_enrollments active tai ngay nay)
    comment_required   BOOLEAN NOT NULL DEFAULT FALSE,
    created_by         BIGINT NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ NULL,
    CONSTRAINT chk_grade_component_setup_evaluation_type CHECK (evaluation_type IN ('MID_TERM', 'END_TERM')),
    UNIQUE(class_id, academic_term_id, evaluation_type)
);
CREATE INDEX idx_grade_component_setups_class ON grade_component_setups(class_id);
CREATE INDEX idx_grade_component_setups_term ON grade_component_setups(academic_term_id);

-- Rang buoc nghiep vu (validate o service, khong phai SQL constraint):
-- tong weight_in_final cua cac setup cung (class_id, academic_term_id)
-- phai <= 100 - mirror dung rule cu cua grade_periods.weight_in_final
-- nhung doi pham vi tu "theo curriculum" sang "theo (lop, ky)".

CREATE TABLE grade_component_setups_history (
    id                        BIGSERIAL PRIMARY KEY,
    grade_component_setup_id BIGINT NOT NULL REFERENCES grade_component_setups(id),
    changed_by                BIGINT NOT NULL REFERENCES users(id),
    action                     VARCHAR(20) NOT NULL,
    details                    JSONB NULL,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grade_component_setups_history_setup ON grade_component_setups_history(grade_component_setup_id);

-- ===================== 3. Bang moi: grade_evaluation_components =====================
-- Thay grade_components - gan theo grade_component_setups thay vi
-- grade_periods. Giu nguyen cau truc cot (khong con weight_in_period -
-- da bo o V40, khong khoi phuc lai).
CREATE TABLE grade_evaluation_components (
    id                          BIGSERIAL PRIMARY KEY,
    uuid                        UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    grade_component_setup_id   BIGINT NOT NULL REFERENCES grade_component_setups(id),
    subject_id                  BIGINT NULL REFERENCES curriculum_subjects(id),
    skill_id                    BIGINT NULL REFERENCES skills(id),
    code                         VARCHAR(50) NOT NULL, -- SPEAKING / WRITING / LISTENING / READING / GRAMMAR / PROJECT / OTHER
    name                         VARCHAR(200) NOT NULL,
    max_score                    DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    pass_threshold               DECIMAL(5,2) NULL,
    scale_type                   VARCHAR(20) NOT NULL DEFAULT 'NUMERIC', -- NUMERIC / PERCENTAGE / BAND
    display_order                INT NOT NULL DEFAULT 0,
    UNIQUE(grade_component_setup_id, code)
);

CREATE TABLE grade_evaluation_components_history (
    id                             BIGSERIAL PRIMARY KEY,
    grade_evaluation_component_id BIGINT NOT NULL REFERENCES grade_evaluation_components(id),
    changed_by                     BIGINT NOT NULL REFERENCES users(id),
    action                          VARCHAR(20) NOT NULL,
    details                         JSONB NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grade_evaluation_components_history_component ON grade_evaluation_components_history(grade_evaluation_component_id);

-- ===================== 4. grade_entries: repoint FK + them academic_term_id/evaluation_type =====================
-- Denormalize academic_term_id/evaluation_type truc tiep tren grade_entries
-- (khong chi qua join grade_evaluation_components -> grade_component_setups)
-- de phuc vu bao cao/thong ke truy van nhanh theo ky - da xac nhan voi
-- nguoi dung (muc dich chinh cua toan bo thay doi nay).
ALTER TABLE grade_entries DROP COLUMN grade_component_id;
ALTER TABLE grade_entries ADD COLUMN grade_evaluation_component_id BIGINT NOT NULL REFERENCES grade_evaluation_components(id);
ALTER TABLE grade_entries ADD COLUMN academic_term_id BIGINT NOT NULL REFERENCES academic_terms(id);
ALTER TABLE grade_entries ADD COLUMN evaluation_type VARCHAR(20) NOT NULL;
ALTER TABLE grade_entries ADD CONSTRAINT chk_grade_entries_evaluation_type CHECK (evaluation_type IN ('MID_TERM', 'END_TERM'));
ALTER TABLE grade_entries ADD CONSTRAINT uq_grade_entries_class_student_component UNIQUE(class_id, student_id, grade_evaluation_component_id);
CREATE INDEX idx_grade_entries_term ON grade_entries(academic_term_id);

-- ===================== 5. grade_period_results -> grade_evaluation_results =====================
-- grade_period_results KHONG co bang history rieng (xac nhan qua doc
-- truc tiep GradeService.java - audit du qua entered_by/published_by/
-- timestamps) - khong can doi ten bang history nao o day.
ALTER TABLE grade_period_results RENAME TO grade_evaluation_results;
ALTER TABLE grade_evaluation_results DROP COLUMN grade_period_id;
ALTER TABLE grade_evaluation_results ADD COLUMN academic_term_id BIGINT NOT NULL REFERENCES academic_terms(id);
ALTER TABLE grade_evaluation_results ADD COLUMN evaluation_type VARCHAR(20) NOT NULL;
ALTER TABLE grade_evaluation_results ADD CONSTRAINT chk_grade_evaluation_results_evaluation_type CHECK (evaluation_type IN ('MID_TERM', 'END_TERM'));
ALTER TABLE grade_evaluation_results ADD COLUMN comment TEXT NULL; -- "Nhan xet" - hien thi PH khi OFFICIAL
ALTER TABLE grade_evaluation_results ADD COLUMN note TEXT NULL;    -- "Ghi chu" - hien thi PH khi OFFICIAL
ALTER TABLE grade_evaluation_results ADD CONSTRAINT uq_grade_evaluation_results_class_student_term UNIQUE(class_id, student_id, academic_term_id, evaluation_type);
ALTER INDEX idx_grade_period_results_class_student RENAME TO idx_grade_evaluation_results_class_student;
CREATE INDEX idx_grade_evaluation_results_term ON grade_evaluation_results(academic_term_id);

-- ===================== 6. grade_period_edit_windows: repoint FK (giu nguyen ten bang) =====================
-- Bang noi bo, chi mang tinh thong tin (moc "lan dau nhap"), khong con
-- gan voi job tu dong nao (xac nhan qua GradeService.java) - giu ten
-- bang de giam pham vi thay doi, chi doi FK.
ALTER TABLE grade_period_edit_windows DROP COLUMN grade_period_id;
ALTER TABLE grade_period_edit_windows ADD COLUMN grade_component_setup_id BIGINT NOT NULL REFERENCES grade_component_setups(id);
ALTER TABLE grade_period_edit_windows ADD CONSTRAINT uq_grade_period_edit_windows_class_setup UNIQUE(class_id, grade_component_setup_id);

-- ===================== 7. student_comments: grade_period_id -> academic_term_id =====================
ALTER TABLE student_comments DROP CONSTRAINT chk_comment_context;
ALTER TABLE student_comments DROP COLUMN grade_period_id;
ALTER TABLE student_comments ADD COLUMN academic_term_id BIGINT NULL REFERENCES academic_terms(id);
ALTER TABLE student_comments ADD CONSTRAINT chk_comment_context CHECK (
    (comment_type = 'DAILY' AND class_session_id IS NOT NULL AND academic_term_id IS NULL) OR
    (comment_type IN ('MID_TERM', 'END_TERM') AND academic_term_id IS NOT NULL AND class_session_id IS NULL)
);

-- ===================== 8. Xoa bang cu (khong con dung, khong co du lieu can giu) =====================
DROP TABLE grade_components_history;
DROP TABLE grade_periods_history;
DROP TABLE grade_components;
DROP TABLE grade_periods;

-- ===================== 9. Permission: doi ten "period" -> "setup" (giu id/role_permissions) =====================
-- Pattern giong V77 doi grade.publish -> grade.approve: UPDATE giu
-- nguyen id, khong dut lien ket role_permissions da gan (HEAD_ACADEMIC,
-- SUPER_ADMIN o V46). academic.grade.component.* giu nguyen (khai niem
-- "thanh phan diem" khong doi). academic.grade.manage/approve/
-- edit.override giu nguyen (khong lien quan cau truc ky danh gia).
UPDATE permissions SET
    code = 'academic.grade.setup.create',
    name = 'Tạo cấu hình sổ điểm (grade component setup)',
    description = 'UC-19 (tách từ academic.grade.manage, đổi từ academic.grade.period.create theo cấu trúc gắn academic_term)'
WHERE code = 'academic.grade.period.create';

UPDATE permissions SET
    code = 'academic.grade.setup.update',
    name = 'Sửa cấu hình sổ điểm (grade component setup)',
    description = 'UC-19 (tách từ academic.grade.manage, đổi từ academic.grade.period.update theo cấu trúc gắn academic_term)'
WHERE code = 'academic.grade.period.update';

UPDATE permissions SET
    code = 'academic.grade.setup.delete',
    name = 'Xoá cấu hình sổ điểm (grade component setup)',
    description = 'UC-19 (xoá setup rỗng, đổi từ academic.grade.period.delete theo cấu trúc gắn academic_term)'
WHERE code = 'academic.grade.period.delete';
