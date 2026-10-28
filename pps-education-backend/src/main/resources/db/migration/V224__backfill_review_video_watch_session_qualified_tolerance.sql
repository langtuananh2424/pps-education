-- =====================================================================
-- V224: Backfill is_qualified cho cac luot xem Video On tap bi ket do
-- lech thoi luong giua YouTube Data API (luc import) va YouTube IFrame
-- Player API (luc phat that) - bo sung ngoai SDD goc, da xac nhan voi
-- nguoi dung 2026-10-28.
--
-- ReviewVideoService.reportProgress truoc day so sanh TUYET DOI, khong
-- dung sai, giua watched_seconds (do tu player luc video ENDED) va
-- duration_seconds (do tu YouTube Data API luc import) - 2 nguon nay co
-- the lech nhau vai giay cho CUNG 1 video (da xac nhan bang du lieu that:
-- nhieu luot CONNECTION ket dung watched_seconds = duration_seconds - 1).
-- Video da ENDED khong tua lai duoc nen cac luot nay ket vinh vien, hoc
-- sinh khong co cach nao tu phuc hoi. Code da sua them dung sai 2 giay
-- (QUALIFIED_WATCH_TOLERANCE_SECONDS) cho ca CONNECTION va REFLEX - migration
-- nay backfill lai cho cac luot CU da ket truoc khi co fix, van con mo
-- (quiz_completed_at IS NULL, hoc sinh van co the vao lam cau hoi tiep).
-- =====================================================================
UPDATE review_video_watch_sessions s
SET is_qualified = TRUE
FROM review_videos v
JOIN review_video_sets rs ON rs.id = v.review_video_set_id
WHERE s.review_video_id = v.id
  AND s.is_qualified = FALSE
  AND s.quiz_completed_at IS NULL
  AND s.archived_at IS NULL
  AND (
      (rs.video_type = 'CONNECTION' AND s.watched_seconds >= v.duration_seconds - 2)
      OR
      (rs.video_type <> 'CONNECTION'
          AND s.watched_seconds >= v.duration_seconds * (v.completion_threshold_percent / 100.0) - 2)
  );
