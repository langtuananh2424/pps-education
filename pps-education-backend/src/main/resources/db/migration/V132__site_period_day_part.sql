-- =====================================================================
-- V129: "Buoi" (Sang/Chieu/Toi) cho tiet hoc — moi buoi danh so tiet
-- RIENG 1-N, khop dung thoi khoa bieu giay thuc te cua nha truong (VD
-- Tiet 1 sang khac Tiet 1 chieu) — bo sung ngoai SDD goc, xac nhan voi
-- nguoi dung 2026-08-20.
-- =====================================================================

ALTER TABLE site_period_templates ADD COLUMN day_part VARCHAR(20) NOT NULL DEFAULT 'MORNING';
ALTER TABLE site_period_templates ALTER COLUMN day_part DROP DEFAULT;

DROP INDEX idx_site_period_template_unique;

-- Cho phep cung 1 period_number ton tai o nhieu buoi khac nhau (VD Tiet 1
-- sang VA Tiet 1 chieu) — unique doi tuong theo (site, buoi, tiet).
CREATE UNIQUE INDEX idx_site_period_template_unique
    ON site_period_templates(site_id, day_part, period_number)
    WHERE deleted_at IS NULL;

-- session_periods can luu lai buoi cua tiet da chon luc tao buoi hoc, de
-- lay lai dung nhom khi hien thi/sua (period_number khong con duy nhat
-- theo site sau khi tach buoi). Du lieu cu toan bo la buoi sang (thoi
-- diem migration nay, tiet 1-5 cua cac site da cau hinh deu 07:30-11:50).
ALTER TABLE session_periods ADD COLUMN day_part VARCHAR(20);
UPDATE session_periods SET day_part = 'MORNING' WHERE day_part IS NULL;
ALTER TABLE session_periods ALTER COLUMN day_part SET NOT NULL;
