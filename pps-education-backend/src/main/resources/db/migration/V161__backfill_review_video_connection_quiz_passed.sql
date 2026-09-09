-- =====================================================================
-- V161: Backfill du lieu sau V160 - UC-23a
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-05.
--
-- V160 doi 2 dieu: (a) mot luot chi tinh "dat" (view_count) khi DUNG 100%
-- cau hoi (truoc day chi can "da nop", dung/sai deu tinh), (b) "hoan thanh"
-- (completed) quay lai cong thuc SO LUOT TUYET DOI (truoc day tinh sai theo
-- TY LE %, xem V93/V101 da revert). Migration V160 chi ALTER TABLE them cot
-- moi (quiz_attempt_count/quiz_passed) voi DEFAULT - khong tu suy lai duoc
-- du lieu CU da ton tai truoc do, khien cac session/rollup tao TRUOC V160
-- con dung sai ngu nghia cu cho toi khi co hanh dong moi kich hoat tinh lai
-- (recomputeProgress). Migration nay backfill lai NGAY cho dung tu du lieu
-- correctness da co san tren review_video_connection_answers, chi anh huong
-- video CONNECTION (REFLEX khong dung cot quiz_passed, khong doi).
-- =====================================================================

-- 1) Suy lai quiz_passed dung cho cac luot da hoan tat (quiz_completed_at
--    khac NULL) TRUOC V160 - dat = khong co cau tra loi nao SAI trong luot do.
UPDATE review_video_watch_sessions s
SET quiz_passed = TRUE
WHERE s.quiz_completed_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM review_video_connection_answers a
      WHERE a.watch_session_id = s.id AND a.is_correct = FALSE
  );

-- 2) Tinh lai view_count cho tung dong review_video_progress cua video CONNECTION
--    theo dung dinh nghia moi (qualified=true AND quiz_passed=true). Tach 2 UPDATE (thay vi 1 UPDATE
--    voi LEFT JOIN) vi Postgres KHONG cho JOIN trong FROM cua UPDATE tham chieu nguoc lai chinh bang
--    dich (p) trong dieu kien ON - da gap loi that "invalid reference to FROM-clause entry for table p".
-- 2a) Cac dong CO session dat - lay dung so luong.
UPDATE review_video_progress p
SET view_count = sub.cnt
FROM (
    SELECT s.review_video_id, s.student_id, s.review_video_assignment_id, COUNT(*) AS cnt
    FROM review_video_watch_sessions s
    WHERE s.is_qualified = TRUE AND s.quiz_passed = TRUE
    GROUP BY s.review_video_id, s.student_id, s.review_video_assignment_id
) sub
WHERE p.review_video_id = sub.review_video_id
  AND p.student_id = sub.student_id
  AND p.review_video_assignment_id IS NOT DISTINCT FROM sub.review_video_assignment_id;

-- 2b) Cac dong video CONNECTION KHONG CON session nao dat (truoc day co the co, nay bi backfill
--     quiz_passed=false vi tra loi sai) - dat ve 0.
UPDATE review_video_progress p
SET view_count = 0
FROM review_videos v
WHERE p.review_video_id = v.id
  AND v.review_video_set_id IN (SELECT id FROM review_video_sets WHERE video_type = 'CONNECTION')
  AND p.view_count <> 0
  AND NOT EXISTS (
      SELECT 1 FROM review_video_watch_sessions s
      WHERE s.review_video_id = p.review_video_id
        AND s.student_id = p.student_id
        AND s.review_video_assignment_id IS NOT DISTINCT FROM p.review_video_assignment_id
        AND s.is_qualified = TRUE AND s.quiz_passed = TRUE
  );

-- 3) Tinh lai is_completed = du so luot tuyet doi (chi CONNECTION - REFLEX
--    chua bao gio dung cong thuc % nen khong can dong tay).
UPDATE review_video_progress p
SET is_completed = (p.view_count >= v.required_view_count)
FROM review_videos v
WHERE p.review_video_id = v.id
  AND v.review_video_set_id IN (SELECT id FROM review_video_sets WHERE video_type = 'CONNECTION');
