-- =====================================================================
-- V44: Tách quyền chi tiết (bổ sung ngoài SDD gốc, đã xác nhận với người
-- dùng — môi trường phát triển, cutover 1 lượt). Cập nhật lại
-- docs/uc/phan-he-02 (danh mục "quyền hành động chi tiết" — UC-02) sau khi
-- migration này ổn định.
--
-- (1) Tách student.manage (đang GỘP toàn bộ CRUD hồ sơ học sinh + phụ
--     huynh vào 1 quyền) thành các quyền hành động chi tiết theo resource
--     + CRUD. GIỮ NGUYÊN ma trận truy cập hiện tại: cấp đủ các quyền con
--     cho đúng tập role cũ (STAFF, SITE_MANAGER) — chưa đổi "ai làm được
--     gì", chỉ tạo hạt mịn để UC-03/04 gán riêng lẻ về sau (VD sau này cấp
--     riêng student.profile.view cho 1 vai trò chỉ-xem).
--
-- (2) Thêm quyền còn THIẾU cho hoạt động của Giáo viên: điểm danh (UC-15)
--     và nhận xét (UC-21/22). Trước đây StudentAttendanceController /
--     StudentCommentController KHÔNG có @PreAuthorize (chỉ dựa ownership
--     check trong Service) nên KHÔNG tồn tại mã quyền để FE gate menu →
--     "Điểm danh nhanh" bị ẩn nhầm sau student.manage mà Giáo viên không
--     có. Nay cấp academic.attendance.mark / academic.comment.write cho
--     TEACHER; academic.comment.approve cho SITE_MANAGER (UC-22).
--
-- Ma trận role→truy cập của MockMvc controller-test giữ nguyên (test assert
-- theo role, không theo mã quyền) nên không phải sửa test.
--
-- SUPER_ADMIN (role tạo tay ngoài migration ở DB dev) được cấp đủ mọi quyền
-- mới ở dưới; nếu môi trường không có role này, phép JOIN tự bỏ qua (no-op).
-- =====================================================================

-- ---------- (1) Danh mục quyền chi tiết module STUDENT ----------
INSERT INTO permissions (code, name, module, description) VALUES
('student.profile.view',   'Xem hồ sơ học sinh',            'STUDENT', 'UC-13 (tách từ student.manage)'),
('student.profile.create', 'Tạo hồ sơ học sinh',            'STUDENT', 'UC-13 (tách từ student.manage)'),
('student.profile.update', 'Sửa hồ sơ học sinh',            'STUDENT', 'UC-13 (tách từ student.manage)'),
('student.profile.import', 'Nhập học sinh theo lô',         'STUDENT', 'UC-35 (tách từ student.manage)'),
('student.parent.view',    'Xem hồ sơ phụ huynh',           'STUDENT', 'UC-13 (tách từ student.manage)'),
('student.parent.manage',  'Tạo/sửa & liên kết phụ huynh',  'STUDENT', 'UC-13/UC-50 (tách từ student.manage)');

-- ---------- (2) Quyền còn thiếu cho Giáo viên (điểm danh & nhận xét) ----------
INSERT INTO permissions (code, name, module, description) VALUES
('academic.attendance.mark', 'Điểm danh học sinh',     'ACADEMIC', 'UC-15'),
('academic.comment.write',   'Viết & gửi nhận xét',    'ACADEMIC', 'UC-21'),
('academic.comment.approve', 'Duyệt/từ chối nhận xét', 'ACADEMIC', 'UC-22');

-- ---------- Gán role STUDENT: giữ nguyên ma trận truy cập cũ của student.manage ----------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN ('student.profile.view','student.profile.create','student.profile.update',
                 'student.profile.import','student.parent.view','student.parent.manage')
  AND r.code IN ('STAFF','SITE_MANAGER','SUPER_ADMIN');

-- ---------- Gán role mới cho Giáo viên / Quản lý điểm trường ----------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.attendance.mark' AND r.code IN ('TEACHER','HEAD_ACADEMIC','SUPER_ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.comment.write' AND r.code IN ('TEACHER','SUPER_ADMIN');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.comment.approve' AND r.code IN ('SITE_MANAGER','SUPER_ADMIN');

-- ---------- Gỡ quyền gộp cũ student.manage (cutover dev — không dual-run) ----------
DELETE FROM user_permission_overrides WHERE permission_id = (SELECT id FROM permissions WHERE code = 'student.manage');
DELETE FROM role_permissions          WHERE permission_id = (SELECT id FROM permissions WHERE code = 'student.manage');
DELETE FROM permissions               WHERE code = 'student.manage';
