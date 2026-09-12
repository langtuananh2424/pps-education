-- Lưu tên GV tại thời điểm xếp/sửa lịch qua Lịch làm việc (bổ sung ngoài
-- SDD gốc, xác nhận với người dùng 2026-09-12) — làm mốc so sánh với
-- actual_teacher_name (V91): khi CM cập nhật lại actual_teacher_name qua
-- Nhận xét học viên (VD GVNN nghỉ đột xuất, đổi người dạy) mà khác giá trị
-- này, Lịch làm việc hiển thị gạch tên gốc + tên mới để nhận biết.
ALTER TABLE class_sessions ADD COLUMN original_teacher_name VARCHAR(255) NULL;
