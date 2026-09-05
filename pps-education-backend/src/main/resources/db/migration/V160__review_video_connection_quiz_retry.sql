-- =====================================================================
-- V160: Video ket noi (CONNECTION) - cho lam lai ca form khi sai - UC-23a
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-05.
--
-- 1 luot xem chi tinh vao viewCount khi hoc sinh tra loi DUNG 100% cau hoi
-- cua luot do. Sai thi cho lam lai CA FORM (khong phai rieng cau sai) toi
-- da 1 lan nua (2 lan thu/luot). Sai ca 2 lan -> luot khong dat, dong luot,
-- tu mo luot xem moi. Chi giu ket qua lan nop cuoi cung (khong luu lich su
-- lan sai dau tien) - da xac nhan voi nguoi dung.
-- =====================================================================

ALTER TABLE review_video_watch_sessions
    ADD COLUMN quiz_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN quiz_passed BOOLEAN NOT NULL DEFAULT FALSE;
