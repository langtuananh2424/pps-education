-- =====================================================================
-- V50: Nhận xét Hàng ngày kiểu mới (UC-21, CHỈ áp dụng comment_type=DAILY
-- — Giữa kỳ/Cuối kỳ giữ nguyên 100%) — bổ sung ngoài SDD gốc, đã xác nhận
-- với người dùng 2026-07-24 (kết luận họp).
--
-- a) class_sessions.lesson_content — "bài học hôm nay" (TEXT), Giáo viên
-- điền 1 lần/buổi, dùng chung cho cả lớp (khác với nhận xét — riêng từng
-- học sinh).
--
-- b) student_comments — 4 cột mới CHỈ có ý nghĩa khi comment_type=DAILY:
--    attitude (VARCHAR(20), enum POOR/AVERAGE/GOOD — Kém/Trung bình/Tốt,
--    dự kiến bổ sung enum sau này nên không dùng CHECK constraint cứng),
--    homework_previous_score (VARCHAR(10) — chấm BTVN buổi TRƯỚC, VD
--    "80%", theo đúng yêu cầu kiểu dữ liệu của người dùng),
--    homework_next (TEXT — mô tả BTVN giao cho buổi SAU, hạn nộp ngầm
--    hiểu là ngày buổi học kế tiếp, không lưu cột deadline riêng),
--    note (TEXT — ghi chú thêm).
--
-- c) system_settings.academic.comment_edit_window_days — hạn X ngày GV
-- được nhập/sửa nhận xét Hàng ngày kể từ NGÀY BUỔI HỌC DIỄN RA (khác
-- academic.grade_edit_window_days tính từ "lần đầu nhập", ở đây neo cố
-- định theo session_date — đơn giản hơn, không cần bảng edit_windows
-- riêng). Actor có academic.comment.approve (đã có sẵn từ V44) bỏ qua cả
-- hạn này lẫn bước chờ duyệt — không cần permission mới.
-- =====================================================================

ALTER TABLE class_sessions ADD COLUMN lesson_content TEXT;

ALTER TABLE student_comments ADD COLUMN attitude VARCHAR(20);
ALTER TABLE student_comments ADD COLUMN homework_previous_score VARCHAR(10);
ALTER TABLE student_comments ADD COLUMN homework_next TEXT;
ALTER TABLE student_comments ADD COLUMN note TEXT;

INSERT INTO system_settings (setting_key, setting_value, category, description) VALUES
    ('academic.comment_edit_window_days', '7', 'ACADEMIC',
     'So ngay GV duoc nhap/sua nhan xet Hang ngay ke tu ngay buoi hoc dien ra (UC-21)');
