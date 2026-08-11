-- =====================================================================
-- V109: PHAN HE 6 - XUAT BAO CAO TU MAU (UC-68, FR-ACA-08). Bo sung
-- ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-09 (xem
-- docs/uc/phan-he-06-hoc-thuat.md).
--
-- Pham vi Phase 2: chi resolver STUDENT_PROFILE duoc trien khai trong
-- ReportGenerationService o migration nay - TRANSCRIPT/DAILY_REPORT/
-- GRADE_REPORT can lam ro them cach chon ky danh gia/buoi hoc truoc khi
-- them resolver rieng (xem trao doi voi nguoi dung).
-- =====================================================================

CREATE TABLE generated_reports (
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    template_id       BIGINT NOT NULL REFERENCES report_templates(id),
    scope             VARCHAR(20) NOT NULL, -- SINGLE / BULK_CLASS
    student_id        BIGINT NULL REFERENCES students(id), -- NULL khi scope=BULK_CLASS (nhieu hoc sinh, gop ZIP)
    class_id          BIGINT NULL REFERENCES classes(id),
    file_url          VARCHAR(1000) NOT NULL, -- .docx neu SINGLE, .zip neu BULK_CLASS
    file_format       VARCHAR(20) NOT NULL,   -- DOCX / ZIP
    file_size_bytes   BIGINT NOT NULL,
    generated_by      BIGINT NOT NULL REFERENCES users(id),
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_generated_reports_student ON generated_reports(student_id, generated_at DESC);
CREATE INDEX idx_generated_reports_class ON generated_reports(class_id, generated_at DESC);
