-- =====================================================================
-- V55: GIAO BTVN LINH HOAT ONLINE/OFFLINE THEO TUNG HOC SINH TRONG NHAN
-- XET HANG NGAY (UC-21 mo rong) - bo sung ngoai SDD goc, da chot voi
-- nguoi dung qua nhieu vong thao luan, 2026-07-28:
--   1. Homework theo TUNG HOC SINH (khong theo ca lop) - de phong truong
--      hop co hoc sinh khong can giao bai.
--   2. 2 kenh doc lap, deu co the giao cung luc cho 1 HS o 1 buoi:
--      (a) Ngu phap - ONLINE (FK exercise_assignments, tu cham ra %) hoac
--          OFFLINE (van dung homework_next/homework_previous_score cu -
--          text tu do, nhap tay % sau khi cham giay).
--      (b) Video On tap (Ket noi/Phan xa) - LUON chon tu Kho Video On tap
--          (FK review_video_sets), khong co khai niem offline.
--   3. Don vi giao = 1 ExerciseAssignment (bo de da gan lop) / 1
--      ReviewVideoSet (bo video da gan lop) - KHONG phai tung cau
--      hoi/tung video le.
--   4. NULL o 2 cot FK = kenh do OFFLINE hoac khong giao gi cho HOC SINH
--      NAY o buoi nay - quyet dinh tren TUNG DONG hoc sinh, dung y "co
--      hoc sinh khong can giao bai".
-- Dong cua session N luu "se giao gi cho session N+1"; % buoi truoc cua
-- session N+1 duoc tinh nguoc lai tu FK tren dong cua session N (xem
-- StudentCommentService).
-- =====================================================================

ALTER TABLE student_comments
    ADD COLUMN homework_next_exercise_assignment_id BIGINT NULL REFERENCES exercise_assignments(id),
    ADD COLUMN homework_next_review_video_set_id BIGINT NULL REFERENCES review_video_sets(id);
