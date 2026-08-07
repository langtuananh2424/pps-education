-- =====================================================================
-- V101: Giam nguong "dat" cua Video On tap (Ket noi + Phan xa) tu 80%
-- xuong 70% de dong bo voi V100 (da giam nguong dat BTVN Ngu phap tu 80%
-- xuong 70%) - bo sung ngoai SDD goc, da xac nhan voi nguoi dung
-- 2026-08-07. V93 truoc do co chu dich dung CHUNG 1 nguong voi
-- exercises.pass_threshold_percent de nhat quan "dat BTVN = X%" toan he
-- thong, nen giam BTVN ma khong giam theo se pha vo tinh nhat quan do.
-- =====================================================================

UPDATE system_settings SET setting_value = '70', updated_at = now()
    WHERE setting_key = 'review_video.completion_pass_threshold_percent';

UPDATE system_settings SET setting_value = '70', updated_at = now()
    WHERE setting_key = 'homework_alert.reflex_pass_threshold_percent';
