-- =====================================================================
-- V49: Tách permission hạt nhân (create/update/delete riêng) cho 9 case
-- đang dùng chung 1 permission bundled cho Tạo/Sửa/Xoá (hoặc Gán/Gỡ) —
-- audit FE trên toàn bộ 47 permission + @PreAuthorize thật của mọi
-- Controller (không chỉ API.md). Bổ sung ngoài SDD gốc — đã xác nhận với
-- người dùng 2026-07-24. Áp dụng đúng chuẩn hạt nhân đã dùng cho Điểm danh
-- (V45: academic.attendance.create/update/delete) và Sổ điểm (V46:
-- academic.grade.period.*/academic.grade.component.*).
--
-- Pattern OR-fallback (giống GradeController): mỗi endpoint check quyền
-- hạt nhân mới HOẶC quyền bundled cũ:
--   @PreAuthorize("hasPermission(null,'x.y.create') or hasPermission(null,'x.y.manage')")
-- Role nào đang có quyền bundled cũ vẫn hoạt động y hệt hiện tại.
--
-- KHÁC V45/V46: KHÔNG backfill role_permissions cho quyền mới ở đây —
-- role hiện tại tiếp tục hoạt động qua quyền bundled cũ, quyền mới chỉ
-- dùng khi cần phân quyền chi tiết hơn (gán tay qua UI phân quyền sau
-- này). Không xoá/đổi quyền bundled cũ.
--
-- 4 Controller đổi từ class-level sang method-level @PreAuthorize để mỗi
-- endpoint check đúng quyền riêng (Role/Override/UserRole theo yêu cầu
-- ticket; Permission cũng đang class-level trên thực tế dù ticket ghi
-- nhầm là "vốn đã method-level" — chuyển luôn cho nhất quán, không đổi
-- hành vi GET). Department/Position/PartnerContract/Site/Student vốn đã
-- method-level, chỉ thêm OR-fallback vào 3 action liên quan.
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
-- 1. Danh mục quyền — PermissionController (tách từ permission.catalog.manage)
('permission.catalog.create', 'Tạo quyền mới',   'USER', 'FR-PER-01 (tách từ permission.catalog.manage)'),
('permission.catalog.update', 'Sửa quyền',        'USER', 'FR-PER-01 (tách từ permission.catalog.manage)'),
('permission.catalog.delete', 'Xoá quyền',        'USER', 'FR-PER-01 (tách từ permission.catalog.manage)'),

-- 2. Vai trò tuỳ biến — RoleController (tách từ permission.role.manage)
('permission.role.create', 'Tạo vai trò tuỳ biến',        'USER', 'FR-PER-02 (tách từ permission.role.manage)'),
('permission.role.update', 'Sửa quyền của vai trò',        'USER', 'FR-PER-02 (tách từ permission.role.manage)'),
('permission.role.delete', 'Xoá vai trò tuỳ biến',         'USER', 'FR-PER-02 (tách từ permission.role.manage)'),

-- 3. Override quyền cá nhân — UserPermissionOverrideController (tách từ permission.override.manage)
('permission.override.set',    'Gán quyền riêng cho tài khoản', 'USER', 'FR-PER-03 (tách từ permission.override.manage)'),
('permission.override.delete', 'Gỡ quyền riêng của tài khoản',  'USER', 'FR-PER-03 (tách từ permission.override.manage)'),

-- 4. Gán vai trò cho user — UserRoleController (tách từ user.role.manage)
('user.role.assign', 'Gán vai trò cho tài khoản',    'USER', 'FR-PER-05 (tách từ user.role.manage)'),
('user.role.revoke', 'Thu hồi vai trò của tài khoản', 'USER', 'FR-PER-05 (tách từ user.role.manage)'),

-- 5. Phòng ban — DepartmentController (tách từ hrm.manage)
('hrm.department.create', 'Tạo phòng ban', 'HRM', 'Tách từ hrm.manage'),
('hrm.department.update', 'Sửa phòng ban', 'HRM', 'Tách từ hrm.manage'),
('hrm.department.delete', 'Xoá phòng ban', 'HRM', 'Tách từ hrm.manage'),

-- 6. Chức vụ — PositionController (tách từ hrm.manage)
('hrm.position.create', 'Tạo chức vụ',                                'HRM', 'FR-HRM-06 (tách từ hrm.manage)'),
('hrm.position.update', 'Sửa chức vụ / cấu hình vai trò mặc định',     'HRM', 'FR-HRM-06 (tách từ hrm.manage)'),
('hrm.position.delete', 'Xoá chức vụ',                                'HRM', 'FR-HRM-06 (tách từ hrm.manage)'),

-- 7. Hợp đồng đối tác — PartnerContractController (tách từ facility.manage)
('facility.partner-contract.create', 'Tạo hợp đồng liên kết trường',           'FACILITY', 'FR-FAC-01 (tách từ facility.manage)'),
('facility.partner-contract.update', 'Sửa/Chấm dứt hợp đồng liên kết trường',  'FACILITY', 'FR-FAC-01 (tách từ facility.manage)'),
('facility.partner-contract.delete', 'Xoá hợp đồng liên kết trường',           'FACILITY', 'FR-FAC-01 (tách từ facility.manage)'),

-- 8. Gán GV vào điểm trường — SiteController (tách từ facility.manage)
('facility.site-teacher.assign', 'Gán giáo viên vào điểm trường', 'FACILITY', 'FR-FAC-01 (tách từ facility.manage)'),
('facility.site-teacher.remove', 'Gỡ giáo viên khỏi điểm trường', 'FACILITY', 'FR-FAC-01 (tách từ facility.manage)'),

-- 9. Liên kết phụ huynh–học sinh — StudentController (tách từ student.parent.manage;
--    student.parent.manage giữ nguyên nghĩa cho tạo/sửa hồ sơ phụ huynh, không đổi)
('student.parent.link.create', 'Liên kết phụ huynh với học sinh', 'STUDENT', 'UC-13/UC-50 (tách từ student.parent.manage)'),
('student.parent.link.delete', 'Gỡ liên kết phụ huynh–học sinh',  'STUDENT', 'UC-13/UC-50 (tách từ student.parent.manage)');
