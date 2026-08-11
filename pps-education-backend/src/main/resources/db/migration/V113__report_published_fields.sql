-- =====================================================================
-- V113: PHAN HE 6 - CHUYEN QUAN LY DANH SACH TRUONG CONG BO (PUBLISHED FIELDS)
-- TU JAVA MEMORY SANG BANG DATABASE REPOSITORY (report_template_published_fields)
-- =====================================================================

CREATE TABLE report_template_published_fields (
    id                BIGSERIAL PRIMARY KEY,
    template_type     VARCHAR(50) NOT NULL,
    field_key         VARCHAR(200) NOT NULL,
    field_label       VARCHAR(200) NOT NULL,
    description       TEXT NULL,
    field_type        VARCHAR(20) NOT NULL DEFAULT 'FIELD',
    display_order     INT NOT NULL DEFAULT 0,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(template_type, field_key)
);

CREATE INDEX idx_report_published_fields_type ON report_template_published_fields(template_type) WHERE is_active = TRUE;

-- Seed dữ liệu công bố ban đầu
INSERT INTO report_template_published_fields (template_type, field_key, field_label, description, field_type, display_order) VALUES
-- DAILY_REPORT
('DAILY_REPORT', 'CLASS_NAME', 'Tên lớp học', 'Tên lớp học từ buổi học', 'FIELD', 1),
('DAILY_REPORT', 'CLASS_DATE', 'Ngày học', 'Ngày diễn ra buổi học (dd/MM/yyyy)', 'FIELD', 2),
('DAILY_REPORT', 'TEACHER_NAME', 'Tên giáo viên', 'Tên GV dạy thực tế hoặc GV chính', 'FIELD', 3),
('DAILY_REPORT', 'LESSON_TOPIC', 'Nội dung bài học', 'Nội dung/chủ đề buổi học', 'FIELD', 4),
('DAILY_REPORT', 'TOTAL_STUDENTS', 'Tổng số học sinh', 'Tổng số HS đang học trong lớp', 'FIELD', 5),
('DAILY_REPORT', 'ABSENT_COUNT', 'Số lượng HS vắng mặt', 'Số học sinh vắng trong buổi học', 'FIELD', 6),
('DAILY_REPORT', 'GENERATED_DATE', 'Ngày xuất báo cáo', 'Ngày tạo file báo cáo (dd/MM/yyyy)', 'FIELD', 7),
('DAILY_REPORT', '[[TABLE:STUDENTS]]', 'Bảng học sinh', 'Bảng danh sách học sinh (STUDENT_NAME, ATTENDANCE_STATUS, STUDENT_COMMENT)', 'TABLE', 8),

-- STUDENT_PROFILE
('STUDENT_PROFILE', 'STUDENT_NAME', 'Họ và tên học sinh', 'Họ và tên đầy đủ của học sinh', 'FIELD', 1),
('STUDENT_PROFILE', 'STUDENT_CODE', 'Mã học sinh', 'Mã số học sinh trong hệ thống', 'FIELD', 2),
('STUDENT_PROFILE', 'DATE_OF_BIRTH', 'Ngày sinh', 'Ngày sinh học sinh (dd/MM/yyyy)', 'FIELD', 3),
('STUDENT_PROFILE', 'GENDER', 'Giới tính', 'Giới tính (MALE/FEMALE)', 'FIELD', 4),
('STUDENT_PROFILE', 'PRIMARY_SITE', 'Điểm trường chính', 'Tên cơ sở / điểm trường', 'FIELD', 5),
('STUDENT_PROFILE', 'STATUS', 'Trạng thái', 'Trạng thái hồ sơ học sinh', 'FIELD', 6),
('STUDENT_PROFILE', 'ENROLLMENT_DATE', 'Ngày nhập học', 'Ngày gia nhập trung tâm (dd/MM/yyyy)', 'FIELD', 7),
('STUDENT_PROFILE', 'PRIMARY_PARENT_NAME', 'Họ tên phụ huynh chính', 'Họ tên PHHS liên hệ chính', 'FIELD', 8),
('STUDENT_PROFILE', 'PRIMARY_PARENT_PHONE', 'SĐT phụ huynh', 'Số điện thoại phụ huynh', 'FIELD', 9),
('STUDENT_PROFILE', 'PRIMARY_PARENT_EMAIL', 'Email phụ huynh', 'Địa chỉ email phụ huynh', 'FIELD', 10),
('STUDENT_PROFILE', 'FINANCIAL_GUARDIAN_NAME', 'Người bảo trợ tài chính', 'Họ tên người trả học phí', 'FIELD', 11),
('STUDENT_PROFILE', 'GENERATED_DATE', 'Ngày xuất báo cáo', 'Ngày xuất file (dd/MM/yyyy)', 'FIELD', 12),

-- STUDENT_COMMENT
('STUDENT_COMMENT', 'STUDENT_NAME', 'Họ và tên học sinh', 'Họ và tên học sinh', 'FIELD', 1),
('STUDENT_COMMENT', 'STUDENT_CODE', 'Mã học sinh', 'Mã số học sinh', 'FIELD', 2),
('STUDENT_COMMENT', 'STUDENT_COMMENT', 'Nội dung nhận xét', 'Nội dung nhận xét của giáo viên', 'FIELD', 3),
('STUDENT_COMMENT', 'GENERATED_DATE', 'Ngày xuất báo cáo', 'Ngày xuất file (dd/MM/yyyy)', 'FIELD', 4),

-- TRANSCRIPT
('TRANSCRIPT', 'STUDENT_NAME', 'Họ tên học sinh', 'Họ tên học sinh', 'FIELD', 1),
('TRANSCRIPT', 'STUDENT_CODE', 'Mã học sinh', 'Mã số học sinh', 'FIELD', 2),
('TRANSCRIPT', 'CLASS_NAME', 'Tên lớp học', 'Tên lớp đang theo học', 'FIELD', 3),
('TRANSCRIPT', 'SCHOOL_NAME', 'Tên cơ sở', 'Tên điểm trường / trung tâm', 'FIELD', 4),
('TRANSCRIPT', 'ACADEMIC_YEAR', 'Năm học', 'Tên năm học (VD: 2025-2026)', 'FIELD', 5),
('TRANSCRIPT', 'PRIMARY_TEACHER_NAME', 'Giáo viên chính', 'Tên giáo viên chủ nhiệm / GV chính', 'FIELD', 6),
('TRANSCRIPT', 'GENERATED_DATE', 'Ngày xuất báo cáo', 'Ngày tạo phiếu (dd/MM/yyyy)', 'FIELD', 7),
('TRANSCRIPT', 'LISTENING_MID1', 'Điểm Nghe - Giữa kỳ 1', 'Điểm kỹ năng Listening đợt Giữa kỳ 1', 'FIELD', 8),
('TRANSCRIPT', 'READING_MID1', 'Điểm Đọc - Giữa kỳ 1', 'Điểm kỹ năng Reading đợt Giữa kỳ 1', 'FIELD', 9),
('TRANSCRIPT', 'SPEAKING_MID1', 'Điểm Nói - Giữa kỳ 1', 'Điểm kỹ năng Speaking đợt Giữa kỳ 1', 'FIELD', 10),
('TRANSCRIPT', 'WRITING_MID1', 'Điểm Viết - Giữa kỳ 1', 'Điểm kỹ năng Writing đợt Giữa kỳ 1', 'FIELD', 11),
('TRANSCRIPT', 'GRAMMAR_MID1', 'Điểm Ngữ pháp - Giữa kỳ 1', 'Điểm kỹ năng Grammar đợt Giữa kỳ 1', 'FIELD', 12),
('TRANSCRIPT', 'OVERALL_MID1', 'Điểm Tổng kết - Giữa kỳ 1', 'Điểm Overall đợt Giữa kỳ 1', 'FIELD', 13),
('TRANSCRIPT', 'LEVEL_MID1', 'Xếp loại - Giữa kỳ 1', 'Trình độ / Level đợt Giữa kỳ 1', 'FIELD', 14),
('TRANSCRIPT', 'COMMENT_MID1', 'Nhận xét - Giữa kỳ 1', 'Lời nhận xét đợt Giữa kỳ 1', 'FIELD', 15),
('TRANSCRIPT', 'LISTENING_END1', 'Điểm Nghe - Cuối kỳ 1', 'Điểm kỹ năng Listening đợt Cuối kỳ 1', 'FIELD', 16),
('TRANSCRIPT', 'READING_END1', 'Điểm Đọc - Cuối kỳ 1', 'Điểm kỹ năng Reading đợt Cuối kỳ 1', 'FIELD', 17),
('TRANSCRIPT', 'SPEAKING_END1', 'Điểm Nói - Cuối kỳ 1', 'Điểm kỹ năng Speaking đợt Cuối kỳ 1', 'FIELD', 18),
('TRANSCRIPT', 'WRITING_END1', 'Điểm Viết - Cuối kỳ 1', 'Điểm kỹ năng Writing đợt Cuối kỳ 1', 'FIELD', 19),
('TRANSCRIPT', 'GRAMMAR_END1', 'Điểm Ngữ pháp - Cuối kỳ 1', 'Điểm kỹ năng Grammar đợt Cuối kỳ 1', 'FIELD', 20),
('TRANSCRIPT', 'OVERALL_END1', 'Điểm Tổng kết - Cuối kỳ 1', 'Điểm Overall đợt Cuối kỳ 1', 'FIELD', 21),
('TRANSCRIPT', 'LEVEL_END1', 'Xếp loại - Cuối kỳ 1', 'Trình độ / Level đợt Cuối kỳ 1', 'FIELD', 22),
('TRANSCRIPT', 'COMMENT_END1', 'Nhận xét - Cuối kỳ 1', 'Lời nhận xét đợt Cuối kỳ 1', 'FIELD', 23),

-- GRADE_REPORT (1 tài liệu/học sinh, giống TRANSCRIPT — cần định danh học sinh)
('GRADE_REPORT', 'STUDENT_NAME', 'Họ tên học sinh', 'Họ tên học sinh', 'FIELD', 1),
('GRADE_REPORT', 'STUDENT_CODE', 'Mã học sinh', 'Mã số học sinh', 'FIELD', 2),
('GRADE_REPORT', 'CLASS_NAME', 'Tên lớp học', 'Tên lớp học', 'FIELD', 3),
('GRADE_REPORT', 'ACADEMIC_YEAR', 'Năm học', 'Năm học', 'FIELD', 4),
('GRADE_REPORT', 'PRIMARY_TEACHER_NAME', 'Giáo viên chính', 'Tên giáo viên chính', 'FIELD', 5),
('GRADE_REPORT', 'GENERATED_DATE', 'Ngày xuất báo cáo', 'Ngày tạo file', 'FIELD', 6),
('GRADE_REPORT', 'LISTENING_MID1', 'Điểm Nghe - Giữa kỳ 1', 'Điểm kỹ năng Listening đợt Giữa kỳ 1', 'FIELD', 7),
('GRADE_REPORT', 'READING_MID1', 'Điểm Đọc - Giữa kỳ 1', 'Điểm kỹ năng Reading đợt Giữa kỳ 1', 'FIELD', 8),
('GRADE_REPORT', 'SPEAKING_MID1', 'Điểm Nói - Giữa kỳ 1', 'Điểm kỹ năng Speaking đợt Giữa kỳ 1', 'FIELD', 9),
('GRADE_REPORT', 'WRITING_MID1', 'Điểm Viết - Giữa kỳ 1', 'Điểm kỹ năng Writing đợt Giữa kỳ 1', 'FIELD', 10),
('GRADE_REPORT', 'GRAMMAR_MID1', 'Điểm Ngữ pháp - Giữa kỳ 1', 'Điểm kỹ năng Grammar đợt Giữa kỳ 1', 'FIELD', 11),
('GRADE_REPORT', 'OVERALL_MID1', 'Điểm Tổng kết - Giữa kỳ 1', 'Điểm Overall đợt Giữa kỳ 1', 'FIELD', 12),
('GRADE_REPORT', 'LEVEL_MID1', 'Xếp loại - Giữa kỳ 1', 'Trình độ / Level đợt Giữa kỳ 1', 'FIELD', 13),
('GRADE_REPORT', 'COMMENT_MID1', 'Nhận xét - Giữa kỳ 1', 'Lời nhận xét đợt Giữa kỳ 1', 'FIELD', 14),
('GRADE_REPORT', 'LISTENING_END1', 'Điểm Nghe - Cuối kỳ 1', 'Điểm kỹ năng Listening đợt Cuối kỳ 1', 'FIELD', 15),
('GRADE_REPORT', 'READING_END1', 'Điểm Đọc - Cuối kỳ 1', 'Điểm kỹ năng Reading đợt Cuối kỳ 1', 'FIELD', 16),
('GRADE_REPORT', 'SPEAKING_END1', 'Điểm Nói - Cuối kỳ 1', 'Điểm kỹ năng Speaking đợt Cuối kỳ 1', 'FIELD', 17),
('GRADE_REPORT', 'WRITING_END1', 'Điểm Viết - Cuối kỳ 1', 'Điểm kỹ năng Writing đợt Cuối kỳ 1', 'FIELD', 18),
('GRADE_REPORT', 'GRAMMAR_END1', 'Điểm Ngữ pháp - Cuối kỳ 1', 'Điểm kỹ năng Grammar đợt Cuối kỳ 1', 'FIELD', 19),
('GRADE_REPORT', 'OVERALL_END1', 'Điểm Tổng kết - Cuối kỳ 1', 'Điểm Overall đợt Cuối kỳ 1', 'FIELD', 20),
('GRADE_REPORT', 'LEVEL_END1', 'Xếp loại - Cuối kỳ 1', 'Trình độ / Level đợt Cuối kỳ 1', 'FIELD', 21),
('GRADE_REPORT', 'COMMENT_END1', 'Nhận xét - Cuối kỳ 1', 'Lời nhận xét đợt Cuối kỳ 1', 'FIELD', 22);
