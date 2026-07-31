-- =====================================================================
-- V65: Chuyen diem phat sinh viec giao bai (Ngu phap Online + Video On
-- tap) tu "Soan & Giao de" (UC-40) / "Kho Video On tap" (UC-23) sang
-- "Nhan xet hoc vien" (UC-21) - bo sung ngoai SDD goc, da xac nhan voi
-- nguoi dung 2026-07-30 (thay the/mo rong quyet dinh V55).
--
-- Thay doi mo hinh:
--   1. Publish 1 de (ASSIGNED)/1 bo Video On tap (ca CONNECTION lan
--      REFLEX) tu nay CHI danh dau "du dieu kien dung lam nguon", KHONG
--      con tu dong giao cho hoc sinh xem/lam ngay (xem ExerciseService/
--      ReviewVideoService).
--   2. Giao bai chi phat sinh khi Giao vien chon 1 de/1 video-set lam
--      "BTVN buoi sau" trong luc viet Nhan xet HANG NGAY (DAILY) cho BAT
--      KY 1 hoc sinh nao trong 1 buoi hoc - he thong tu tao ban giao cho
--      TOAN BO hoc sinh dang ACTIVE trong lop (khong chi rieng hoc sinh
--      dang duoc nhan xet - DAO NGUOC quyet dinh V55 diem 1 "homework
--      theo TUNG hoc sinh"), han nop = buoi hoc KE TIEP cua lop.
--   3. Kenh Ngu phap Online da co san "ban giao" (exercise_assignments,
--      V18) - chi doi Y NGHIA cot FK tren student_comments (xem duoi).
--      Kenh Video On tap CHUA TUNG co khai niem "ban giao theo lop" -
--      review_video_sets chi co scope tinh (curriculum-wide HOAC
--      1-lop-cu-the) + status, khong co class_id/due_at/target rieng cho
--      tung lan giao - can bang moi hoan toan, mirror exercise_assignments
--      (bo lateSubmissionAllowed/latePenaltyPercent vi khong ap dung cho
--      video).
-- =====================================================================

CREATE TABLE review_video_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    review_video_set_id BIGINT NOT NULL REFERENCES review_video_sets(id),
    class_id            BIGINT NOT NULL REFERENCES classes(id),
    assigned_by         BIGINT NOT NULL REFERENCES users(id),
    available_from      TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at              TIMESTAMPTZ NULL,
    target_student_ids  JSONB NULL, -- NULL = ca lop (mac dinh sau V65)
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' -- ACTIVE / CANCELLED / COMPLETED
);
CREATE INDEX idx_review_video_assignments_class ON review_video_assignments(class_id);

-- student_comments.homework_next_exercise_assignment_id (V55) da tro san
-- vao exercise_assignments.id - GIU NGUYEN ten/kieu cot, chi doi Y NGHIA
-- o tang Service (FE gio gui Exercise.id, Service tu tao/tai su dung
-- ExerciseAssignment thay vi FE tu chon 1 ban da giao san).
--
-- homework_next_review_video_set_id (V55, tro thang review_video_sets)
-- doi thanh homework_next_review_video_assignment_id (tro
-- review_video_assignments) de dong bo voi kenh Ngu phap - ca 2 kenh cung
-- luu "ban giao nao duoc tao ra do buoi nay chon", phuc vu huy/tao lai
-- khi Giao vien doi lua chon luc con DRAFT. An toan doi cot (khong chi
-- them cot song song) vi du an con o Sprint 0/1, chua co du lieu that.
ALTER TABLE student_comments
    DROP COLUMN homework_next_review_video_set_id,
    ADD COLUMN homework_next_review_video_assignment_id BIGINT NULL
        REFERENCES review_video_assignments(id);
