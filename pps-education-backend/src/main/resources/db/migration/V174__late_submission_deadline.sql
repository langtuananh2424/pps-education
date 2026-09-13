-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — cho phép Giáo viên set 1 hạn chót cụ
-- thể cho việc "nộp muộn" (khác hẳn due_at gốc), thay vì chỉ có 2 lựa chọn "không cho nộp muộn" / "cho
-- nộp muộn KHÔNG GIỚI HẠN" như V165. NULL (mặc định) = giữ đúng hành vi V165 (nộp muộn không giới hạn
-- khi late_submission_allowed=true) — cột mới hoàn toàn optional, không đổi hành vi các bản giao cũ.
ALTER TABLE exercise_assignments
    ADD COLUMN late_submission_deadline TIMESTAMPTZ;

ALTER TABLE review_video_assignments
    ADD COLUMN late_submission_deadline TIMESTAMPTZ;
