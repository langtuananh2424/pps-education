-- =====================================================================
-- V37: PHAN HE 6 - DANH MUC KY NANG + NHAP DIEM QUA EXCEL
-- (UC-54 FR-ACA-06 Quan ly danh muc ky nang, UC-53 FR-ACA-03 Nhap diem
-- qua Excel, UC-16/A2 bo sung thanh phan diem giua ky).
-- BO SUNG NGOAI SDD GOC - da xac nhan voi nguoi dung (xem
-- docs/uc/phan-he-06-hoc-thuat.md UC-53/UC-54 va
-- docs/sdd-groups/06-hoc-thuat.md muc f/h). Ly do: moi lop/chuong
-- trinh/truong co tap ky nang thi + thang band/level khac nhau, thay
-- doi thuong xuyen - can danh muc ky nang data-driven (khong sua enum
-- Java moi lan them) va cho luu Overall/Level GV da tinh san trong
-- Excel (he thong KHONG tu tinh lai cong thuc).
-- KHONG dung toi enum SubjectCode/ComponentCode hien co - 2 cot
-- subject_code/code van la VARCHAR tu do, skills chi la danh muc tham
-- chieu bo sung qua cot skill_id (nullable).
-- =====================================================================

-- a) skills -- Danh muc ky nang dung chung (UC-54)
CREATE TABLE skills (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed 7 gia tri khop dung enum SubjectCode + ComponentCode hien co.
INSERT INTO skills(code, name) VALUES
    ('SPEAKING',  'Noi'),
    ('LISTENING', 'Nghe'),
    ('READING',   'Doc'),
    ('WRITING',   'Viet'),
    ('GRAMMAR',   'Ngu phap'),
    ('PROJECT',   'Du an'),
    ('OTHER',     'Khac');

-- b) Cot skill_id tham chieu danh muc (nullable, backfill theo code cu)
ALTER TABLE curriculum_subjects ADD COLUMN skill_id BIGINT NULL REFERENCES skills(id);
ALTER TABLE grade_components   ADD COLUMN skill_id BIGINT NULL REFERENCES skills(id);

UPDATE curriculum_subjects cs SET skill_id = s.id FROM skills s WHERE cs.subject_code = s.code;
UPDATE grade_components   gc SET skill_id = s.id FROM skills s WHERE gc.code = s.code;

-- c) scale_type -- chi phuc vu hien thi dung dinh dang o FE
-- (NUMERIC / PERCENTAGE / BAND); max_score/pass_threshold hien co van la
-- can tren thuc te dung de validate, he thong khong tu tinh lai theo
-- scale_type.
ALTER TABLE grade_components ADD COLUMN scale_type VARCHAR(20) NOT NULL DEFAULT 'NUMERIC';

-- d) grade_period_results -- Overall/Level theo ky danh gia (UC-53).
-- KHAC voi grade_final_summaries (SDD muc d - tong ket toan hoc phan,
-- he thong tu tinh, CHUA trien khai): bang nay luu NGUYEN gia tri
-- Overall/Level GV da tinh san (band IELTS / % / thang rieng) cho TUNG
-- ky danh gia; vong doi trang thai/duyet giong het grade_entries, dung
-- chung approval_flows voi entity_type='GRADE_PERIOD_RESULT'.
CREATE TABLE grade_period_results (
    id               BIGSERIAL PRIMARY KEY,
    uuid             UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    class_id         BIGINT NOT NULL REFERENCES classes(id),
    student_id       BIGINT NOT NULL REFERENCES students(id),
    grade_period_id  BIGINT NOT NULL REFERENCES grade_periods(id),
    overall_score    DECIMAL(5,2) NULL,
    scale_type       VARCHAR(20) NOT NULL DEFAULT 'NUMERIC', -- NUMERIC / PERCENTAGE / BAND
    level            VARCHAR(100) NULL,                      -- VD "B2", "Band 6.5" - tu do
    source           VARCHAR(20) NOT NULL DEFAULT 'MANUAL',  -- MANUAL / EXCEL_IMPORT
    import_job_id    BIGINT NULL REFERENCES import_jobs(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',   -- DRAFT / PENDING / APPROVED / REJECTED
    entered_by       BIGINT NOT NULL REFERENCES users(id),
    entered_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    approval_flow_id BIGINT NULL REFERENCES approval_flows(id),
    submitted_at     TIMESTAMPTZ NULL,
    approved_at      TIMESTAMPTZ NULL,
    approved_by      BIGINT NULL REFERENCES users(id),
    UNIQUE(class_id, student_id, grade_period_id)
);
CREATE INDEX idx_grade_period_results_class_student ON grade_period_results(class_id, student_id);
