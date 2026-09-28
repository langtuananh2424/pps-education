-- =====================================================================
-- V196: BO SUNG report_template_published_fields (V113) cho DAILY_REPORT
-- - ASSISTANT_TEACHER_NAME: tro giang cua buoi hoc (class_sessions.assistant_teacher_id)
-- - PRESENT_COUNT: so HS co mat = PRESENT + LATE + EARLY_LEAVE
-- - ABSENT_COUNT doi quy tac: tinh ca Vang co phep (ABSENT + EXCUSED)
-- - ABSENT_STUDENT_NAMES: ho ten HS vang (dung nhom HS dem trong ABSENT_COUNT)
-- - MISSING_HOMEWORK_STUDENT_NAMES: ho ten HS co "BTVN buoi truoc" kenh online
--   Ngu phap/Nghe = "Chua lam bai"
-- - HOMEWORK_CONTENT: BTVN giao cho buoi sau, gop moi kenh, moi muc 1 dong
-- Da xac nhan voi nguoi dung 2026-09-28. Chi doi du lieu cong bo, khong doi schema.
-- =====================================================================

INSERT INTO report_template_published_fields (template_type, field_key, field_label, description, field_type, display_order) VALUES
('DAILY_REPORT', 'ASSISTANT_TEACHER_NAME', 'Tên trợ giảng', 'Trợ giảng của buổi học (để trống nếu buổi không có trợ giảng)', 'FIELD', 4),
('DAILY_REPORT', 'HOMEWORK_CONTENT', 'Bài tập về nhà', 'BTVN giao cho buổi sau, gộp offline + online, mỗi mục 1 dòng (để trống nếu chưa giao)', 'FIELD', 6),
('DAILY_REPORT', 'PRESENT_COUNT', 'Số lượng HS có mặt', 'Số HS Có mặt + Đi trễ + Về sớm trong buổi học', 'FIELD', 8),
('DAILY_REPORT', 'ABSENT_STUDENT_NAMES', 'Danh sách HS vắng', 'Họ tên HS Vắng + Vắng có phép, cách nhau dấu phẩy (để trống nếu không ai vắng)', 'FIELD', 10),
('DAILY_REPORT', 'MISSING_HOMEWORK_STUDENT_NAMES', 'Danh sách HS thiếu BTVN', 'Họ tên HS "Chưa làm bài" BTVN online Ngữ pháp/Nghe của buổi trước, cách nhau dấu phẩy', 'FIELD', 11);

UPDATE report_template_published_fields
SET description = 'Số HS Vắng + Vắng có phép trong buổi học', updated_at = now()
WHERE template_type = 'DAILY_REPORT' AND field_key = 'ABSENT_COUNT';

-- Sap lai thu tu hien thi DAILY_REPORT sau khi chen cac truong moi.
UPDATE report_template_published_fields AS f
SET display_order = v.display_order, updated_at = now()
FROM (VALUES
    ('CLASS_NAME', 1),
    ('CLASS_DATE', 2),
    ('TEACHER_NAME', 3),
    ('ASSISTANT_TEACHER_NAME', 4),
    ('LESSON_TOPIC', 5),
    ('HOMEWORK_CONTENT', 6),
    ('TOTAL_STUDENTS', 7),
    ('PRESENT_COUNT', 8),
    ('ABSENT_COUNT', 9),
    ('ABSENT_STUDENT_NAMES', 10),
    ('MISSING_HOMEWORK_STUDENT_NAMES', 11),
    ('GENERATED_DATE', 12),
    ('[[TABLE:STUDENTS]]', 13)
) AS v(field_key, display_order)
WHERE f.template_type = 'DAILY_REPORT' AND f.field_key = v.field_key;
