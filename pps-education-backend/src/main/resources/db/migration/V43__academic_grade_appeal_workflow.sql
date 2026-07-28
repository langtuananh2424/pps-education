-- =====================================================================
-- V43: PHAN HE 6 - LUONG DIEM 4 TRANG THAI + PHUC KHAO (UC-19/20 sua doi,
-- UC-62 moi). BO SUNG NGOAI SDD GOC - da xac nhan voi nguoi dung qua
-- AskUserQuestion (xem docs/uc/phan-he-06-hoc-thuat.md UC-19/20/62).
-- Thay DRAFT/PUBLISHED (V39) bang DRAFT -> PROVISIONAL_PUBLISHED ->
-- APPEAL -> OFFICIAL. Han X ngay (grade_period_edit_windows) gio CHI con
-- y nghia "do tre tu dong cong bo diem du kien neu khong ai cong bo thu
-- cong" - khong con khoa sua theo thoi gian nua (GV toan quyen sua/xoa
-- DRAFT khong gioi han thoi gian, chi con gioi han theo trang thai).
-- =====================================================================

-- a) Cot status cu la VARCHAR(20) (V13/V37) - "PROVISIONAL_PUBLISHED" dai
-- 22 ky tu, phai noi rong truoc khi UPDATE gia tri moi.
ALTER TABLE grade_entries ALTER COLUMN status TYPE VARCHAR(30);
ALTER TABLE grade_period_results ALTER COLUMN status TYPE VARCHAR(30);

-- Du lieu cu: PUBLISHED -> PROVISIONAL_PUBLISHED (coi la da cong bo du
-- kien, published_at da co san dung nghia lam moc tinh han Y ngay).
UPDATE grade_entries SET status = 'PROVISIONAL_PUBLISHED' WHERE status = 'PUBLISHED';
UPDATE grade_period_results SET status = 'PROVISIONAL_PUBLISHED' WHERE status = 'PUBLISHED';

-- b) Moc chuyen Chinh thuc - chi he thong tu dong gan (khong co thao tac
-- thu cong nao chuyen OFFICIAL), khong can cot finalized_by tuong ung.
ALTER TABLE grade_entries ADD COLUMN finalized_at TIMESTAMPTZ NULL;
ALTER TABLE grade_period_results ADD COLUMN finalized_at TIMESTAMPTZ NULL;

-- c) Han Y ngay phuc khao ke tu luc cong bo diem du kien - doc/ghi qua
-- AcademicSettingsService, cung pattern voi academic.grade_edit_window_days
-- da co (V39).
INSERT INTO system_settings (setting_key, setting_value, category, description) VALUES
    ('academic.grade_appeal_window_days', '7', 'ACADEMIC',
     'So ngay hoc sinh/phu huynh duoc gui phuc khao ke tu luc cong bo diem du kien - het han thi he thong tu dong chuyen Chinh thuc du dang phuc khao hay khong (FR-ACA-03, UC-62)');

-- d) Bang grade_appeal_requests - yeu cau phuc khao cua Hoc sinh/Phu
-- huynh gui len, Giao vien phai TIEP NHAN (accept) truoc khi duoc sua
-- diem cua dung hoc sinh do (UC-62). entity_type/entity_id polymorphic -
-- khong FK cung vi tro toi 1 trong 2 bang grade_entries/grade_period_results
-- (giong pattern notifications.entity_type/entity_id da dung o V?? cho
-- module Notification). class_id/student_id denormalized de truy van
-- nhanh theo lop GV phu trach / theo hoc sinh, khong can join qua bang
-- da xac dinh boi entity_type.
CREATE TABLE grade_appeal_requests (
    id                     BIGSERIAL PRIMARY KEY,
    uuid                   UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    entity_type            VARCHAR(30) NOT NULL, -- GRADE_ENTRY / GRADE_PERIOD_RESULT
    entity_id              BIGINT NOT NULL,
    class_id               BIGINT NOT NULL REFERENCES classes(id),
    student_id             BIGINT NOT NULL REFERENCES students(id),
    requested_by_user_id   BIGINT NOT NULL REFERENCES users(id), -- hoc sinh hoac phu huynh
    reason                 TEXT NULL, -- tuy chon, hoc sinh/phu huynh co the de trong
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / ACCEPTED / RESOLVED
    accepted_by_user_id    BIGINT NULL REFERENCES users(id),
    accepted_at            TIMESTAMPTZ NULL,
    resolved_at            TIMESTAMPTZ NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grade_appeal_requests_entity ON grade_appeal_requests(entity_type, entity_id, status);
CREATE INDEX idx_grade_appeal_requests_class ON grade_appeal_requests(class_id, status);
CREATE INDEX idx_grade_appeal_requests_requester ON grade_appeal_requests(requested_by_user_id);

-- e) Doi mo ta 2 permission da co (V38/V39) - KHONG doi code, khong
-- insert permission moi, khong dut lien ket role_permissions da gan.
UPDATE permissions SET
    name = 'Cong bo diem du kien hoc phan',
    description = 'FR-ACA-03 UC-20 (cong bo diem du kien)'
WHERE code = 'academic.grade.publish';

UPDATE permissions SET
    name = 'Toan quyen them/sua/xoa diem bat ke trang thai',
    description = 'FR-ACA-03 UC-19/20/62 - bo qua moi rang buoc theo trang thai (DRAFT/PROVISIONAL_PUBLISHED/APPEAL/OFFICIAL), khong chi rieng han X ngay nhu truoc'
WHERE code = 'academic.grade.edit.override';
