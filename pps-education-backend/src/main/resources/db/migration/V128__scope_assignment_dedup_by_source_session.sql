-- V128: Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19 — đảo ngược 1 phần quyết định
-- V69/V70/V73 ("giao lại 1 Bài/bộ Video cho lớp = hủy bản giao ACTIVE cũ, tạo bản mới"): quy tắc đó giờ
-- CHỈ áp dụng khi giao lại từ ĐÚNG CÙNG buổi Nhận xét nguồn (source_class_session_id, xem V123). Giao
-- CÙNG 1 Bài/bộ Video từ 2 buổi Nhận xét KHÁC NHAU (VD buổi trước và buổi sau đều chọn lại đúng Bài đó
-- làm BTVN) nay là 2 bài tập ĐỘC LẬP, tồn tại ACTIVE song song, học sinh làm/chấm điểm riêng từng bản —
-- xem Javadoc ExerciseService#deliverToClass/ReviewVideoService#deliverToClass.
--
-- Đổi ràng buộc UNIQUE của V73 (exercise_id/review_video_set_id, class_id, due_at) thêm
-- source_class_session_id vào khóa — nới rộng phạm vi cho phép trùng (exercise/set, class, due_at) MIỄN
-- LÀ khác buổi nguồn, vẫn giữ nguyên tác dụng chống trùng request đồng thời CÙNG 1 buổi (V73 gốc). Không
-- cần dọn dữ liệu trùng trước khi thêm (khác V73) — đây là ràng buộc RỘNG HƠN, dữ liệu hiện có không thể
-- vi phạm ràng buộc mới.
DROP INDEX uq_review_video_assignments_active_target;
DROP INDEX uq_exercise_assignments_active_target;

CREATE UNIQUE INDEX uq_review_video_assignments_active_target
    ON review_video_assignments (review_video_set_id, class_id, source_class_session_id, due_at)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_exercise_assignments_active_target
    ON exercise_assignments (exercise_id, class_id, source_class_session_id, due_at)
    WHERE status = 'ACTIVE';
