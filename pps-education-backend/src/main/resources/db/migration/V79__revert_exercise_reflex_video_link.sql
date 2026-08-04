-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04: rollback V77 (exercise_type=
-- REFLEX_VIDEO + exercises.review_video_set_id). Người dùng quyết định KHÔNG bọc Video phản xạ
-- vào Exercise nữa — giao Video phản xạ (REFLEX) cho lớp đi thẳng qua ReviewVideoSet, y hệt Video
-- kết nối (CONNECTION), qua "Nhận xét học viên" (homeworkNextReviewVideoSetId gọi thẳng
-- ReviewVideoService#deliverToClass, không cần Exercise). Không migration cũ nào bị sửa — chỉ thêm
-- migration mới đảo ngược thay đổi V77 theo đúng quy ước Flyway của dự án.

ALTER TABLE exercises DROP COLUMN review_video_set_id;
