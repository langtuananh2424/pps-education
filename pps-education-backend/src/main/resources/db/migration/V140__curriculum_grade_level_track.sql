-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — rubric chấm AI (Speaking/Writing)
-- do giáo viên cung cấp khác nhau theo TỪNG Khối (6/7/8/9) VÀ chương trình (IELTS/CAMBRIDGE), nhưng
-- curriculums trước đây không có trường nào lưu 2 thông tin này (level là free-text, luôn bỏ trống
-- trong thực tế) — AI chấm không thể tự biết chọn đúng bảng rubric nào cho học sinh. Cả 2 cột đều
-- NULL = "chưa phân loại" (dữ liệu cũ trước V140), khi đó các service chấm AI theo Khối/track sẽ bỏ
-- qua (rơi lại hàng chờ chấm tay) thay vì đoán bừa rubric — xem Javadoc RubricByGradeTrackLoader.
ALTER TABLE curriculums ADD COLUMN grade_level VARCHAR(10);
ALTER TABLE curriculums ADD COLUMN track VARCHAR(20);
