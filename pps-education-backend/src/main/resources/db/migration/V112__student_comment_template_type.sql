-- =====================================================================
-- V112: Bo sung gia tri STUDENT_COMMENT vao template_type
-- De phan biet ro 2 loai bao cao trong Thong ke nhan xet:
--   1. DAILY_REPORT  -> Bao cao ngay cua lop (scope: CLASS_SESSION)
--   2. STUDENT_COMMENT -> Bao cao nhan xet cua 1 hoc sinh (scope: SINGLE)
--
-- Enum PostgreSQL da duoc khai bao o V108 la VARCHAR(50) (khong dung
-- Postgres ENUM type) nen chi can constraint check neu co, khong can
-- ALTER TYPE. Kiem tra lai V108: template_type la VARCHAR(50) NOT NULL
-- nen khong can migrate schema — chi add comment de document.
-- =====================================================================

-- Khong co thay doi schema can thiet vi template_type la VARCHAR(50).
-- Migration nay chi luu vet de trac quet: gia tri 'STUDENT_COMMENT'
-- duoc dung tu version nay.
DO $$
BEGIN
    RAISE NOTICE 'V112: STUDENT_COMMENT templateType registered as valid value.';
END $$;
