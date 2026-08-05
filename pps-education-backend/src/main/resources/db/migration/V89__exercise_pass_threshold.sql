-- =====================================================================
-- V89: BTVN duoi nguong dat phai lam lai (bo sung ngoai SDD goc, da xac
-- nhan voi nguoi dung 2026-08-05) - ap dung cho moi exerciseType hoc sinh
-- lam (SELF_PRACTICE/ASSIGNED/MOCK_TEST/SKILL_PRACTICE), cau hinh theo
-- tung Bai (exercises), mac dinh 80%.
-- =====================================================================

ALTER TABLE exercises
    ADD COLUMN pass_threshold_percent DECIMAL(5,2) NOT NULL DEFAULT 80.00;

-- NULL = luot lam bai chua cham xong (chua co total_score) nen chua xac
-- dinh dat/chua dat.
ALTER TABLE exercise_attempts
    ADD COLUMN passed BOOLEAN NULL;
