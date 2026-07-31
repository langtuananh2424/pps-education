-- =====================================================================
-- V51: Dọn dẹp permission — loại bỏ 8 permission "bundled" (*.manage)
-- vẫn còn tồn tại sau 5 đợt hạt nhân hóa trước (V44/V45/V46/V49), thay
-- bằng permission chi tiết cho toàn bộ scope còn lại của chúng. Bổ sung
-- ngoài SDD gốc — đã xác nhận với người dùng 2026-07-24.
--
-- Audit @PreAuthorize thật (không chỉ API.md) cho thấy các permission bundled
-- này KHÔNG trùng 100% chức năng với permission con đã tách — mỗi permission
-- vẫn đang là chốt chặn duy nhất cho một số endpoint (chủ yếu GET/xem danh
-- sách, và vài resource CRUD như Employee/Site/Skill/Finance chưa từng được
-- tách quyền). Vì vậy không thể xóa thẳng — phải tách nốt phần scope còn lại
-- thành permission mới trước khi xóa permission bundled.
--
-- QUAN TRỌNG: `academic.grade.manage` và `task.manage` KHÔNG nằm trong danh
-- sách xóa dù ban đầu dự định gộp — audit sâu hơn phát hiện 2 permission này
-- còn được dùng trực tiếp trong business logic ở Service (không chỉ
-- @PreAuthorize Controller): GradeService.requireCanEnterGrades/
-- requireCanPublishGrades/listUnpublishedForSite (cờ "Trưởng phòng đào tạo
-- toàn quyền mọi site") và TaskService.cancelTask/canAssignCompanyWide (cờ
-- "quản trị công việc cao nhất, giao việc toàn công ty"). Đây là quyền
-- override phạm vi rộng, không phải chỉ gộp CRUD — xóa sẽ làm mất khả năng
-- này. 2 permission này giữ nguyên, không đổi gì; chỉ bỏ OR-fallback dư
-- thừa ở GradeController/TaskController (phần đã có permission hạt nhân
-- backfill sẵn từ trước) và trỏ AcademicSettingsController/
-- TaskSettingsController thẳng về đúng 2 permission này (không tạo thêm
-- permission "settings" riêng vì không cần thiết khi permission gốc vẫn còn).
--
-- Thứ tự bắt buộc: (1) tạo permission mới, (2) backfill role_permissions
-- cho MỌI role đang giữ permission bundled — cả permission con đã có sẵn từ
-- trước (V44/V45/V46/V49, hiện 0 role nào giữ trực tiếp) lẫn permission mới
-- ở bước (1), (3) gỡ tham chiếu permission_audit_log (giữ nguyên dòng audit,
-- chỉ NULL cột FK vì details JSONB đã tự chứa snapshot), (4) xóa
-- role_permissions/user_permission_overrides còn trỏ tới permission bundled,
-- (5) xóa hẳn 8 permission bundled khỏi catalog.
-- =====================================================================

-- (1) Permission mới — cho phần scope chưa từng được tách của 8 permission bundled
INSERT INTO permissions (code, name, module, description) VALUES
-- hrm.manage -- EmployeeController (CRUD nhân sự) + EmployeeBatchImportController + PositionController.getDefaultRoles
('hrm.employee.view',   'Xem hồ sơ nhân sự (bằng cấp/khen thưởng/hợp đồng)', 'HRM', 'Tách từ hrm.manage'),
('hrm.employee.create', 'Tạo hồ sơ nhân sự',                                 'HRM', 'Tách từ hrm.manage'),
('hrm.employee.update', 'Sửa hồ sơ nhân sự (bằng cấp/khen thưởng/hợp đồng)', 'HRM', 'Tách từ hrm.manage'),
('hrm.employee.import', 'Nhập nhân sự theo lô',                              'HRM', 'UC-51 (tách từ hrm.manage)'),
('hrm.position.view',   'Xem vai trò mặc định của chức vụ',                  'HRM', 'Tách từ hrm.manage'),

-- facility.manage -- SiteController (CRUD điểm trường) + PartnerContractController (xem danh sách)
('facility.site.create',           'Tạo điểm trường',                    'FACILITY', 'Tách từ facility.manage'),
('facility.site.update',           'Sửa điểm trường / gán quản lý điểm trường', 'FACILITY', 'Tách từ facility.manage'),
('facility.partner-contract.view', 'Xem hợp đồng liên kết trường',        'FACILITY', 'Tách từ facility.manage'),

-- permission.catalog/role/override.manage + user.role.manage -- các endpoint GET chưa từng tách quyền xem riêng
('permission.catalog.view',  'Xem danh mục quyền',           'USER', 'Tách từ permission.catalog.manage'),
('permission.role.view',     'Xem vai trò & ma trận quyền',  'USER', 'Tách từ permission.role.manage'),
('user.role.view',           'Xem vai trò đã gán cho tài khoản', 'USER', 'Tách từ user.role.manage'),
('permission.override.view', 'Xem quyền hiệu lực của tài khoản', 'USER', 'Tách từ permission.override.manage'),

-- academic.grade.manage (KHÔNG xóa) -- SkillController (CRUD kỹ năng) tách riêng vì không liên quan gì tới
-- quyền override phạm vi site của academic.grade.manage, hợp lý để có permission chi tiết riêng
('academic.skill.create', 'Tạo kỹ năng', 'ACADEMIC', 'Tách từ academic.grade.manage'),
('academic.skill.update', 'Sửa kỹ năng', 'ACADEMIC', 'Tách từ academic.grade.manage'),

-- finance.manage -- Invoice/Scholarship/TuitionPlan/OperatingExpense (chưa từng tách theo sub-resource)
('finance.invoice.manage',      'Xuất hóa đơn & ghi nhận thanh toán',           'FINANCE', 'Tách từ finance.manage'),
('finance.scholarship.manage',  'Cấp/thu hồi học bổng',                         'FINANCE', 'Tách từ finance.manage'),
('finance.tuition-plan.manage', 'Quản lý gói học phí & gán gói cho học sinh',   'FINANCE', 'Tách từ finance.manage'),
('finance.expense.create',      'Tạo đề nghị chi vận hành',                     'FINANCE', 'Tách từ finance.manage'),

-- student.parent.manage -- ParentController (CRUD hồ sơ) + ParentBatchImportController
('student.parent.create', 'Tạo hồ sơ phụ huynh',       'STUDENT', 'Tách từ student.parent.manage'),
('student.parent.update', 'Sửa hồ sơ phụ huynh',       'STUDENT', 'Tách từ student.parent.manage'),
('student.parent.import', 'Nhập phụ huynh theo lô',    'STUDENT', 'UC-53 (tách từ student.parent.manage)');

-- (2) Backfill role_permissions cho mọi role đang giữ permission bundled --
-- cả permission con đã có sẵn (V44/V45/V46/V49) lẫn permission mới ở bước (1).
-- Không backfill gì cho academic.grade.manage/task.manage -- 2 permission này
-- không xóa, role_permissions hiện tại giữ nguyên không đổi.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    -- hrm.manage -> HR_MANAGER, SUPER_ADMIN, SYS_ADMIN
    ('HR_MANAGER','hrm.department.create'), ('HR_MANAGER','hrm.department.update'), ('HR_MANAGER','hrm.department.delete'),
    ('HR_MANAGER','hrm.position.create'), ('HR_MANAGER','hrm.position.update'), ('HR_MANAGER','hrm.position.delete'),
    ('HR_MANAGER','hrm.employee.view'), ('HR_MANAGER','hrm.employee.create'), ('HR_MANAGER','hrm.employee.update'), ('HR_MANAGER','hrm.employee.import'), ('HR_MANAGER','hrm.position.view'),
    ('SUPER_ADMIN','hrm.department.create'), ('SUPER_ADMIN','hrm.department.update'), ('SUPER_ADMIN','hrm.department.delete'),
    ('SUPER_ADMIN','hrm.position.create'), ('SUPER_ADMIN','hrm.position.update'), ('SUPER_ADMIN','hrm.position.delete'),
    ('SUPER_ADMIN','hrm.employee.view'), ('SUPER_ADMIN','hrm.employee.create'), ('SUPER_ADMIN','hrm.employee.update'), ('SUPER_ADMIN','hrm.employee.import'), ('SUPER_ADMIN','hrm.position.view'),
    ('SYS_ADMIN','hrm.department.create'), ('SYS_ADMIN','hrm.department.update'), ('SYS_ADMIN','hrm.department.delete'),
    ('SYS_ADMIN','hrm.position.create'), ('SYS_ADMIN','hrm.position.update'), ('SYS_ADMIN','hrm.position.delete'),
    ('SYS_ADMIN','hrm.employee.view'), ('SYS_ADMIN','hrm.employee.create'), ('SYS_ADMIN','hrm.employee.update'), ('SYS_ADMIN','hrm.employee.import'), ('SYS_ADMIN','hrm.position.view'),

    -- facility.manage -> OPS_MANAGER, SUPER_ADMIN, SYS_ADMIN
    ('OPS_MANAGER','facility.partner-contract.create'), ('OPS_MANAGER','facility.partner-contract.update'), ('OPS_MANAGER','facility.partner-contract.delete'),
    ('OPS_MANAGER','facility.site-teacher.assign'), ('OPS_MANAGER','facility.site-teacher.remove'),
    ('OPS_MANAGER','facility.site.create'), ('OPS_MANAGER','facility.site.update'), ('OPS_MANAGER','facility.partner-contract.view'),
    ('SUPER_ADMIN','facility.partner-contract.create'), ('SUPER_ADMIN','facility.partner-contract.update'), ('SUPER_ADMIN','facility.partner-contract.delete'),
    ('SUPER_ADMIN','facility.site-teacher.assign'), ('SUPER_ADMIN','facility.site-teacher.remove'),
    ('SUPER_ADMIN','facility.site.create'), ('SUPER_ADMIN','facility.site.update'), ('SUPER_ADMIN','facility.partner-contract.view'),
    ('SYS_ADMIN','facility.partner-contract.create'), ('SYS_ADMIN','facility.partner-contract.update'), ('SYS_ADMIN','facility.partner-contract.delete'),
    ('SYS_ADMIN','facility.site-teacher.assign'), ('SYS_ADMIN','facility.site-teacher.remove'),
    ('SYS_ADMIN','facility.site.create'), ('SYS_ADMIN','facility.site.update'), ('SYS_ADMIN','facility.partner-contract.view'),

    -- permission.catalog.manage -> SUPER_ADMIN, SYS_ADMIN
    ('SUPER_ADMIN','permission.catalog.create'), ('SUPER_ADMIN','permission.catalog.update'), ('SUPER_ADMIN','permission.catalog.delete'), ('SUPER_ADMIN','permission.catalog.view'),
    ('SYS_ADMIN','permission.catalog.create'), ('SYS_ADMIN','permission.catalog.update'), ('SYS_ADMIN','permission.catalog.delete'), ('SYS_ADMIN','permission.catalog.view'),

    -- permission.role.manage -> SUPER_ADMIN, SYS_ADMIN
    ('SUPER_ADMIN','permission.role.create'), ('SUPER_ADMIN','permission.role.update'), ('SUPER_ADMIN','permission.role.delete'), ('SUPER_ADMIN','permission.role.view'),
    ('SYS_ADMIN','permission.role.create'), ('SYS_ADMIN','permission.role.update'), ('SYS_ADMIN','permission.role.delete'), ('SYS_ADMIN','permission.role.view'),

    -- user.role.manage -> SUPER_ADMIN, SYS_ADMIN
    ('SUPER_ADMIN','user.role.assign'), ('SUPER_ADMIN','user.role.revoke'), ('SUPER_ADMIN','user.role.view'),
    ('SYS_ADMIN','user.role.assign'), ('SYS_ADMIN','user.role.revoke'), ('SYS_ADMIN','user.role.view'),

    -- permission.override.manage -> SUPER_ADMIN, SYS_ADMIN
    ('SUPER_ADMIN','permission.override.set'), ('SUPER_ADMIN','permission.override.delete'), ('SUPER_ADMIN','permission.override.view'),
    ('SYS_ADMIN','permission.override.set'), ('SYS_ADMIN','permission.override.delete'), ('SYS_ADMIN','permission.override.view'),

    -- academic.skill.* mới (KHÔNG liên quan tới việc academic.grade.manage giữ nguyên) -> HEAD_ACADEMIC, SUPER_ADMIN
    ('HEAD_ACADEMIC','academic.skill.create'), ('HEAD_ACADEMIC','academic.skill.update'),
    ('SUPER_ADMIN','academic.skill.create'), ('SUPER_ADMIN','academic.skill.update'),

    -- finance.manage -> STAFF, SUPER_ADMIN
    ('STAFF','finance.invoice.manage'), ('STAFF','finance.scholarship.manage'), ('STAFF','finance.tuition-plan.manage'), ('STAFF','finance.expense.create'),
    ('SUPER_ADMIN','finance.invoice.manage'), ('SUPER_ADMIN','finance.scholarship.manage'), ('SUPER_ADMIN','finance.tuition-plan.manage'), ('SUPER_ADMIN','finance.expense.create'),

    -- student.parent.manage -> SITE_MANAGER, STAFF, SUPER_ADMIN
    ('SITE_MANAGER','student.parent.link.create'), ('SITE_MANAGER','student.parent.link.delete'),
    ('SITE_MANAGER','student.parent.create'), ('SITE_MANAGER','student.parent.update'), ('SITE_MANAGER','student.parent.import'),
    ('STAFF','student.parent.link.create'), ('STAFF','student.parent.link.delete'),
    ('STAFF','student.parent.create'), ('STAFF','student.parent.update'), ('STAFF','student.parent.import'),
    ('SUPER_ADMIN','student.parent.link.create'), ('SUPER_ADMIN','student.parent.link.delete'),
    ('SUPER_ADMIN','student.parent.create'), ('SUPER_ADMIN','student.parent.update'), ('SUPER_ADMIN','student.parent.import')
) AS backfill(role_code, permission_code)
JOIN roles r ON r.code = backfill.role_code
JOIN permissions p ON p.code = backfill.permission_code
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

-- (3) Gỡ tham chiếu permission_audit_log tới permission bundled sắp xóa -- GIỮ NGUYÊN dòng audit
-- (details JSONB đã tự chứa snapshot code/name lúc phát sinh sự kiện), chỉ NULL cột FK.
UPDATE permission_audit_log
SET target_permission_id = NULL
WHERE target_permission_id IN (
    SELECT id FROM permissions WHERE code IN (
        'hrm.manage', 'facility.manage', 'permission.catalog.manage', 'permission.role.manage',
        'user.role.manage', 'permission.override.manage', 'finance.manage', 'student.parent.manage'
    )
);

-- (4) Xóa role_permissions / user_permission_overrides còn trỏ tới permission bundled
DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id FROM permissions WHERE code IN (
        'hrm.manage', 'facility.manage', 'permission.catalog.manage', 'permission.role.manage',
        'user.role.manage', 'permission.override.manage', 'finance.manage', 'student.parent.manage'
    )
);

DELETE FROM user_permission_overrides
WHERE permission_id IN (
    SELECT id FROM permissions WHERE code IN (
        'hrm.manage', 'facility.manage', 'permission.catalog.manage', 'permission.role.manage',
        'user.role.manage', 'permission.override.manage', 'finance.manage', 'student.parent.manage'
    )
);

-- (5) Xóa hẳn 8 permission bundled khỏi catalog (academic.grade.manage và task.manage
-- KHÔNG bị xóa -- xem giải thích ở đầu file)
DELETE FROM permissions WHERE code IN (
    'hrm.manage', 'facility.manage', 'permission.catalog.manage', 'permission.role.manage',
    'user.role.manage', 'permission.override.manage', 'finance.manage', 'student.parent.manage'
);
