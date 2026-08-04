-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: Đề (Exam) loại FOREIGN giờ có 1
-- loại "Bài" mới (exercise_type = REFLEX_VIDEO) đại diện cho 1 video REFLEX (Kho Video Ôn tập) thay
-- vì câu hỏi thường — cho phép GV nước ngoài soạn "Video phản xạ" ngay trong luồng Soạn & giao đề,
-- tái dùng nguyên hệ ReviewVideoSet/ReviewVideo/ReviewVideoAssignment đã có (không tạo hệ song song).
-- Xem ExerciseService#createReflexVideoExercise/publishExercise/deliverToClass.
ALTER TABLE exercises ADD COLUMN review_video_set_id BIGINT NULL REFERENCES review_video_sets(id);
