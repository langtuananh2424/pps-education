-- =====================================================================
-- V59: VIDEO KET NOI (CONNECTION) - NGUONG % CAU HINH DUOC + SO LAN XEM
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-07-28:
-- watched_seconds hien la moc CAO NHAT tung dat (watermark, khong phan
-- biet duoc "lan" nao voi "lan" nao) - can khai niem LUOT XEM (session)
-- rieng de dem duoc so lan xem dat nguong. review_video_progress giu
-- nguyen lam bang TONG HOP (rollup) de khong pha API %/completed hien co.
-- =====================================================================

ALTER TABLE review_videos
    ADD COLUMN completion_threshold_percent INT NOT NULL DEFAULT 80,
    ADD COLUMN required_view_count INT NOT NULL DEFAULT 1;

-- 1 dong = 1 luot xem (session) - watched_seconds la moc CAO NHAT trong
-- CHINH luot do (khong phai suot doi nhu review_video_progress).
-- is_qualified = da dat threshold trong luot nay.
CREATE TABLE review_video_watch_sessions (
    id                BIGSERIAL PRIMARY KEY,
    review_video_id   BIGINT NOT NULL REFERENCES review_videos(id),
    student_id        BIGINT NOT NULL REFERENCES students(id),
    watched_seconds   INT NOT NULL DEFAULT 0,
    is_qualified      BOOLEAN NOT NULL DEFAULT FALSE,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_video_watch_sessions_video_student ON review_video_watch_sessions(review_video_id, student_id);

-- Rollup: dem so luot da qualified. is_completed (da co) doi y nghia
-- (tinh lai o Service) = view_count >= review_video.required_view_count.
ALTER TABLE review_video_progress ADD COLUMN view_count INT NOT NULL DEFAULT 0;
