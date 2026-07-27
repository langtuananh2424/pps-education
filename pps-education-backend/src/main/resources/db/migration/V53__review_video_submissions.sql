-- =====================================================================
-- V53: NOP AUDIO CHO VIDEO PHAN XA + GIAO VIEN CHAM DIEM (UC-23b)
-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-07-27: UC-23a
-- (Kho Video On tap) chi moi co theo doi % xem (review_video_progress),
-- chua co noi nao de Hoc sinh nop audio tra loi cho "Video phan xa"
-- (REFLEX) va Giao vien cham diem. 3 quyet dinh nghiep vu da chot:
--   1. Chi ap dung REFLEX (khong CONNECTION) - kiem tra o tang Service
--      (video_type nam tren review_video_sets, khong dat CHECK truc tiep
--      len bang nay duoc vi phai join qua review_videos->review_video_sets).
--   2. Giao vien cham diem day du: score + max_score + feedback (khong
--      chi nghe khong cham).
--   3. Hoc sinh duoc nop lai - GHI DE ban cu (UNIQUE review_video_id +
--      student_id, giong review_video_progress), khong giu lich su cac
--      lan nop truoc; nop lai xoa luon diem/nhan xet cu (Service tu xoa).
--
-- KHONG tai dung grade_entries/GradeEntry (gan so diem chinh thuc, luong
-- DRAFT->PROVISIONAL_PUBLISHED->APPEAL->OFFICIAL qua nang cho audio on
-- tap tu nguyen) va KHONG tai dung StudentAnswerGrading (gan UC-40/41,
-- co versioning is_final/latest + partial unique index khong can thiet o
-- day). score/max_score/feedback/graded_by/graded_at nam PHANG ngay tren
-- dong submission vi nop lai da ghi de, khong can lich su diem.
-- =====================================================================

CREATE TABLE review_video_submissions (
    id                BIGSERIAL PRIMARY KEY,
    review_video_id   BIGINT NOT NULL REFERENCES review_videos(id),
    student_id        BIGINT NOT NULL REFERENCES students(id),
    audio_url         VARCHAR(1000) NOT NULL,
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT now(), -- cap nhat lai moi lan nop lai (resubmit)
    score             DECIMAL(5,2) NULL, -- NULL = chua cham
    max_score         DECIMAL(5,2) NULL,
    feedback          TEXT NULL,
    graded_by         BIGINT NULL REFERENCES users(id),
    graded_at         TIMESTAMPTZ NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_video_id, student_id)
);
CREATE INDEX idx_review_video_submissions_video ON review_video_submissions(review_video_id);
CREATE INDEX idx_review_video_submissions_student ON review_video_submissions(student_id);
