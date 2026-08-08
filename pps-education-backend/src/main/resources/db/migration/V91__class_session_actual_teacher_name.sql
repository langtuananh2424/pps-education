-- "Tên giáo viên giảng dạy" thực tế của 1 buổi học — bổ sung ngoài SDD gốc, đã xác nhận với người
-- dùng 2026-08-06. KHÁC primary_teacher_id (FK tài khoản hệ thống): với buổi Giáo viên nước ngoài,
-- nhân sự "chăm sóc lớp" nhập hộ vào Excel (Giáo viên nước ngoài không tự thao tác hệ thống) — nên
-- đây là text nhập tay, 1 giá trị dùng chung cả buổi (mirror lesson_content), để bên quản lý theo
-- dõi buổi đó thực tế ai dạy. Tùy chọn (nullable), buổi cũ để trống, không set default.
ALTER TABLE class_sessions ADD COLUMN actual_teacher_name VARCHAR(255) NULL;
