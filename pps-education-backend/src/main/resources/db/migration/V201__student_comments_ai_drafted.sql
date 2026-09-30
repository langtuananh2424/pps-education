-- Bổ sung ngoài SDD gốc (UC-74 Trợ lý AI soạn nháp nhận xét), đã xác nhận với người dùng 2026-09-29.
--
-- Đánh dấu nhận xét có nội dung xuất phát từ bản nháp của trợ lý AI (giáo viên bấm "Áp dụng vào bảng"/"Lưu nháp"
-- từ trợ lý; giáo viên vẫn có thể đã sửa tay sau đó). Dùng để đo chất lượng nhận xét AI trên dữ liệu đã duyệt/từ
-- chối thật (tỷ lệ bị từ chối, lộ chữ số, độ dài...) so với nhận xét giáo viên tự viết — xem
-- scripts/comment-ai-approved-metrics.sql. Chỉ bật lên, không tắt lại (nguồn gốc nội dung); dòng cũ = FALSE.
ALTER TABLE student_comments ADD COLUMN ai_drafted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN student_comments.ai_drafted IS
    'TRUE nếu nội dung nhận xét xuất phát từ bản nháp của trợ lý AI (UC-74); FALSE = giáo viên tự viết hoặc dữ liệu trước V201.';
