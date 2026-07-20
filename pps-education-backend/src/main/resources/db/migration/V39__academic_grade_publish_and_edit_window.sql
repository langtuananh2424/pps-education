-- =====================================================================
-- V39: PHAN HE 6 - CONG BO DIEM THAY THE DUYET DIEM + CUA SO CHINH SUA
-- X NGAY (UC-19/UC-20). BO SUNG NGOAI SDD GOC - da xac nhan voi nguoi
-- dung qua nhieu vong hoi (xem docs/uc/phan-he-06-hoc-thuat.md UC-19/20).
-- Ly do: nghiep vu khong can "duyet dung/sai" nua, chi can (1) GV toan
-- quyen tu sua diem lop minh trong X ngay ke tu lan dau nhap cho 1
-- lop+ky (X cau hinh duoc), (2) permission ngoai le de chu dong cap
-- "toan quyen sua bat ke han" cho nguoi cu the, (3) "cong bo diem" thay
-- "duyet diem" - khong con khai niem tu choi, sua diem sau cong bo (con
-- trong han) phu huynh thay ngay, khong can cong bo lai.
-- =====================================================================

-- a) Doi ten permission approve -> publish (UPDATE giu nguyen id, khong
-- dut lien ket role_permissions da gan o V38).
UPDATE permissions SET
    code = 'academic.grade.publish',
    name = 'Cong bo diem hoc phan',
    description = 'FR-ACA-03 UC-20 (cong bo)'
WHERE code = 'academic.grade.approve';

-- b) Permission ngoai le: toan quyen sua diem bat ke han X ngay. Mac
-- dinh gan HEAD_ACADEMIC + SITE_MANAGER; van gan them duoc cho nhan vien
-- bat ky qua UC-04 (user_permission_overrides, khong can code them gi).
INSERT INTO permissions (code, name, module, description) VALUES
    ('academic.grade.edit.override', 'Toan quyen sua diem (bo qua han X ngay)', 'ACADEMIC', 'FR-ACA-03');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('HEAD_ACADEMIC', 'SITE_MANAGER') AND p.code = 'academic.grade.edit.override';

-- c) Cau hinh so ngay X (mac dinh 7 - nguoi dung tu doi qua API moi
-- PUT /api/academic/settings/grade-edit-window-days ngay sau khi trien khai).
INSERT INTO system_settings (setting_key, setting_value, category, description) VALUES
    ('academic.grade_edit_window_days', '7', 'ACADEMIC',
     'So ngay GV toan quyen sua diem ke tu lan dau nhap cho 1 lop+ky danh gia (FR-ACA-03)');

-- d) Moc "lan dau nhap" theo (lop, ky danh gia) - chua co gi tuong duong
-- truoc day (grade_periods thuoc curriculum dung chung nhieu lop, khong
-- co san diem neo theo tung lop).
CREATE TABLE grade_period_edit_windows (
    id               BIGSERIAL PRIMARY KEY,
    class_id         BIGINT NOT NULL REFERENCES classes(id),
    grade_period_id  BIGINT NOT NULL REFERENCES grade_periods(id),
    first_entered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(class_id, grade_period_id)
);

-- e) Doi du lieu status cu: PENDING/REJECTED -> DRAFT, APPROVED -> PUBLISHED.
UPDATE grade_entries SET status = 'PUBLISHED' WHERE status = 'APPROVED';
UPDATE grade_entries SET status = 'DRAFT' WHERE status IN ('PENDING', 'REJECTED');
UPDATE grade_period_results SET status = 'PUBLISHED' WHERE status = 'APPROVED';
UPDATE grade_period_results SET status = 'DRAFT' WHERE status IN ('PENDING', 'REJECTED');

-- f) Doi ten cot cho dung nghia moi (khong mat du lieu).
ALTER TABLE grade_entries RENAME COLUMN approved_at TO published_at;
ALTER TABLE grade_entries RENAME COLUMN approved_by TO published_by;
ALTER TABLE grade_period_results RENAME COLUMN approved_at TO published_at;
ALTER TABLE grade_period_results RENAME COLUMN approved_by TO published_by;
-- submitted_at, approval_flow_id: GIU NGUYEN trong DB o ca 2 bang (khong
-- drop, bao toan lich su cu) - chi bo field khoi Java entity, khong con
-- dung trong code moi (khong con submit/approval_flow cho diem nua).
