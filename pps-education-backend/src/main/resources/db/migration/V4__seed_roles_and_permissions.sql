-- =====================================================================
-- V4: SEED DỮ LIỆU NỀN - 11 vai trò hệ thống (SRS > Các tác nhân)
-- =====================================================================

INSERT INTO roles (code, name, description, is_system) VALUES
('STUDENT',       'Học sinh',                 'Người trực tiếp sử dụng dịch vụ và tham gia lớp học.', TRUE),
('PARENT',        'Phụ huynh',                 'Người giám hộ, theo dõi tiến độ và thanh toán tài chính.', TRUE),
('TEACHER',       'Giáo viên',                 'Trực tiếp giảng dạy và đánh giá học sinh.', TRUE),
('HEAD_ACADEMIC', 'Trưởng phòng đào tạo',      'Quyền hạn cao nhất của Phòng Đào tạo.', TRUE),
('SITE_MANAGER',  'Quản lý điểm trường',       'Phụ trách vận hành một hoặc nhiều điểm trường.', TRUE),
('HR_MANAGER',    'Quản lý nhân sự',           'Phụ trách vòng đời nhân sự cấp hệ thống.', TRUE),
('STAFF',         'Nhân viên',                 'Tư vấn tuyển sinh, CSKH, hoặc giáo vụ.', TRUE),
('SYS_ADMIN',     'Quản trị viên',              'Phụ trách kỹ thuật và cấu hình hệ thống.', TRUE),
('PARTNER_REP',   'Đại diện trường liên kết',  'Xem báo cáo học sinh trường mình, gửi phản hồi.', TRUE),
('OPS_MANAGER',   'Quản lý vận hành',           'Điều phối hoạt động chung, hợp đồng trường liên kết.', TRUE),
('EXECUTIVE',     'Ban giám đốc',               'Cấp quản lý cao nhất, xem báo cáo tổng hợp.', TRUE);

-- Danh mục quyền khởi điểm cho module AUTH/USER (UC-02) — sẽ mở rộng dần theo từng phân hệ khi triển khai
INSERT INTO permissions (code, name, module, description) VALUES
('auth.login',            'Đăng nhập hệ thống',            'AUTH', 'FR-AUT-01'),
('user.manage',           'Quản trị tài khoản người dùng', 'USER', 'Tạo/sửa tài khoản'),
('permission.catalog.manage',  'Quản lý danh mục quyền',   'USER', 'FR-PER-01'),
('permission.role.manage',     'Cấu hình nhóm quyền mặc định', 'USER', 'FR-PER-02'),
('permission.override.manage', 'Tùy chỉnh quyền riêng theo tài khoản', 'USER', 'FR-PER-03'),
('permission.audit.view',      'Xem nhật ký thay đổi quyền', 'USER', 'FR-PER-04');

-- Gán toàn bộ quyền USER/AUTH cho SYS_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'SYS_ADMIN' AND p.module IN ('AUTH', 'USER');

-- Mọi role đều có quyền đăng nhập
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'auth.login';

-- system_settings mặc định liên quan tới UC-01/UC-09 (đầy đủ hơn sẽ bổ sung ở Phase B khi làm Chấm công)
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('security.brute_force.max_attempts', '5', 'Số lần sai mật khẩu tối đa trước khi khóa (FR-AUT-02)', 'SECURITY'),
('security.brute_force.lock_minutes', '15', 'Thời gian khóa tài khoản (phút)', 'SECURITY');
