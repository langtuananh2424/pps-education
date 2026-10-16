-- =====================================================================
-- V214: Backfill hoan thanh video CONNECTION sau thay doi 2026-10-16
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-10-16.
--
-- "Hoan thanh" (is_completed) cua CONNECTION nay = du required_view_count luot
-- da xem het (is_qualified) VA lam xong cau hoi (quiz_completed_at khac NULL,
-- ke ca luot lam sai), thay vi chi dem luot DAT (quiz_passed). Backfill cac
-- dong tien do cu chua duoc tinh lai (chi bat TRUE, khong bao gio tat dong
-- da hoan thanh/dung som).
-- =====================================================================
UPDATE review_video_progress p
SET is_completed = TRUE
FROM review_videos v
WHERE p.review_video_id = v.id
  AND p.is_completed = FALSE
  AND v.review_video_set_id IN (SELECT id FROM review_video_sets WHERE video_type = 'CONNECTION')
  AND (
      SELECT COUNT(*) FROM review_video_watch_sessions s
      WHERE s.review_video_id = p.review_video_id
        AND s.student_id = p.student_id
        AND s.review_video_assignment_id IS NOT DISTINCT FROM p.review_video_assignment_id
        AND s.is_qualified = TRUE AND s.quiz_completed_at IS NOT NULL
  ) >= v.required_view_count;
