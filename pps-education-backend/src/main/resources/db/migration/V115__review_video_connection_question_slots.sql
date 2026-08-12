-- =====================================================================
-- V115: Video tu ket noi (CONNECTION) - chia cau hoi trac nghiem theo tung
-- luot xem (bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-08-11).
--
-- Truoc day: moi luot xem phai tra loi TOAN BO N cau hoi cua video. Gio:
-- giao vien cau hinh N cau hoi + M luot xem bat buoc (M = required_view_count
-- da co san tren review_videos), he thong chia N cau hoi thanh M nhom NGAU
-- NHIEN RIENG theo TUNG hoc sinh (chong hoc sinh hoi bai nhau), luu co dinh
-- vao bang moi review_video_connection_question_slots - hoc sinh xem lai
-- dung luot nao thi nhan lai dung nhom cau hoi cua luot do (khong random
-- lai). slot_index moi review_video_watch_sessions ghi nhan luot do ung
-- voi nhom cau hoi nao (chu ky lap lai theo modulo M neu xem qua M luot).
--
-- completion_threshold_percent (review_videos) doi y nghia CHO CONNECTION:
-- tu "nguong % xem de 1 luot duoc tinh hop le" sang "nguong % pass diem
-- trac nghiem tong" (tong so cau dung / tong N cau, gop tat ca luot) - xu
-- ly hoan toan o tang Service (ReviewVideoService), KHONG doi ten/kieu cot
-- nay vi REFLEX van dung dung y nghia cu (nguong % xem, khong doi gi).
-- =====================================================================

ALTER TABLE review_video_watch_sessions ADD COLUMN slot_index INT;

CREATE TABLE review_video_connection_question_slots (
    id                                     BIGSERIAL PRIMARY KEY,
    review_video_connection_question_id    BIGINT NOT NULL REFERENCES review_video_connection_questions(id),
    student_id                             BIGINT NOT NULL REFERENCES students(id),
    slot_index                             INT NOT NULL,
    created_at                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_video_connection_question_id, student_id)
);
CREATE INDEX idx_rv_conn_question_slots_student ON review_video_connection_question_slots(student_id);
CREATE INDEX idx_rv_conn_question_slots_question ON review_video_connection_question_slots(review_video_connection_question_id);
