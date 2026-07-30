-- =====================================================================
-- V63: Bo sung permission cho ReviewVideoController (Kho Video On tap,
-- UC-23/23a/23b) - Controller nay tu khi tai cau truc 2026-07-27 (V52)
-- chua tung co @PreAuthorize nao, chi dua vao requireAssignedTeacher
-- (class_teachers) trong Service. He qua: khong co permission nao de cap
-- cho tai khoan khac TEACHER test tinh nang nay (da phat hien khi nguoi
-- dung cap "full quyen" cho sysadmin nhung khong thay tab - quyen chua
-- ton tai trong catalog nen khong co gi de cap).
--
-- Gate dung theo tieu chi da dung o V62: permission moi CHI cho cac thao
-- tac quan ly cua Giao vien (POST/PUT), KHONG gate cac endpoint GET dung
-- chung Hoc sinh+Giao vien (hoc sinh khong nam permission nao trong he
-- Hybrid PBAC nay). Rieng listSubmissionsForTeacher + gradeSubmission tai
-- dung 'lms.grading.manage' da co san (V28) - cung nhom hanh dong "cham
-- bai" nhu ManualGradingController (UC-41)/ListeningPracticeGradingController
-- (UC-26), review-video la domain thu 3 dung chung permission nay.
--
-- KHONG dung Precondition/requireAssignedTeacher trong Service - van giu
-- nguyen theo UC-23 ("Giao vien duoc phan cong giang day lop/khung lien
-- quan"). Permission moi la lop kiem soat THEM, khong thay the.
--
-- Chi gan TEACHER (khop UC-23 + cach V62 gan lms.exercise.*/lms.document.*/
-- lms.listening-practice.* cung chi cho TEACHER) - CO Y khong gan them cho
-- SYS_ADMIN, dung tien le da ghi ro o V28 (dong 73-76): gan them se la cap
-- quyen moi ngoai hanh vi hien tai, vi pham business-fidelity. Can cap cho
-- tai khoan khac de test thi dung UC-04 (user_permission_overrides).
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('lms.review-video.create', 'Tạo bộ video ôn tập', 'LMS', 'UC-23'),
('lms.review-video.update', 'Sửa/công bố bộ video, thêm video/câu hỏi', 'LMS', 'UC-23'),
('lms.review-video.view',   'Xem thống kê xem video theo lớp', 'LMS', 'UC-23a');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN ('lms.review-video.create', 'lms.review-video.update', 'lms.review-video.view')
  AND r.code = 'TEACHER';
