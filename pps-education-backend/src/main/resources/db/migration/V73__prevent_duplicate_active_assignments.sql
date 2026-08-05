-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03 — fix bug thông báo bị gửi lặp N lần
-- khi "Gửi nhận xét" hàng loạt cho nhiều học sinh CÙNG buổi, CÙNG chọn 1 nguồn BTVN (Bài/Video):
-- ReviewVideoService#deliverToClass / ExerciseService#deliverToClass (V70) chỉ chống trùng bằng
-- SELECT-rồi-INSERT ở tầng ứng dụng, không đủ an toàn khi FE gửi N request đồng thời (Promise.allSettled)
-- — 2 request có thể cùng SELECT thấy "chưa có bản giao" trước khi request kia kịp INSERT xong, tạo ra
-- N bản giao trùng + N thông báo giống hệt cho toàn bộ học sinh lớp (đã tái hiện được thực tế). Thêm
-- ràng buộc UNIQUE ở DB làm chốt chặn cuối cùng, đúng ý định nghiệp vụ "tối đa 1 lần giao ACTIVE cho
-- 1 (Bài/bộ Video, lớp, hạn nộp)" — Service tầng ứng dụng bắt DataIntegrityViolationException khi thua
-- race, đọc lại bản ghi đã thắng thay vì tạo mới/báo lại (xem ReviewVideoService/ExerciseService).

-- Dọn dữ liệu trùng đã phát sinh THẬT trước khi có ràng buộc này (đúng bug đang fix) — giữ lại bản ghi
-- id nhỏ nhất (tạo trước) trong mỗi nhóm trùng, chuyển các bản ghi trùng còn lại sang CANCELLED (không
-- xóa cứng — vẫn có thể đang bị student_comments/review_video_question_submissions tham chiếu qua FK).
-- Chỉ xét due_at IS NOT NULL để khớp đúng phạm vi ràng buộc UNIQUE bên dưới (NULL không bị chặn trùng).
UPDATE review_video_assignments
SET status = 'CANCELLED'
WHERE status = 'ACTIVE'
  AND due_at IS NOT NULL
  AND id NOT IN (
      SELECT MIN(id) FROM review_video_assignments
      WHERE status = 'ACTIVE' AND due_at IS NOT NULL
      GROUP BY review_video_set_id, class_id, due_at
  );

UPDATE exercise_assignments
SET status = 'CANCELLED'
WHERE status = 'ACTIVE'
  AND due_at IS NOT NULL
  AND id NOT IN (
      SELECT MIN(id) FROM exercise_assignments
      WHERE status = 'ACTIVE' AND due_at IS NOT NULL
      GROUP BY exercise_id, class_id, due_at
  );

CREATE UNIQUE INDEX uq_review_video_assignments_active_target
    ON review_video_assignments (review_video_set_id, class_id, due_at)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_exercise_assignments_active_target
    ON exercise_assignments (exercise_id, class_id, due_at)
    WHERE status = 'ACTIVE';
