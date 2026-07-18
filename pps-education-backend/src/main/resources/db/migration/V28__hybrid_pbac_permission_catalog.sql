-- =====================================================================
-- V28: Bổ sung permission code còn thiếu cho mô hình Hybrid PBAC (SDD >
-- 00-intro-va-kien-truc.md mục e; SRS "Lưu ý bản chất mô tả tác nhân" —
-- actor trong UC chỉ là vai trò mặc định/FR-PER-02, không phải ràng buộc
-- kỹ thuật cứng, phải đi qua permission + override được mới đúng FR-PER-03).
--
-- 12 Service trước đây hardcode role-check trực tiếp trong code (không qua
-- role_permissions/user_permission_overrides) cho các UC mà Precondition
-- chỉ nêu actor, không nêu tên quyền cụ thể. Mỗi permission dưới đây được
-- gán ĐÚNG bằng tập role đang hardcode trong code (không mở rộng/thu hẹp),
-- để hành vi mặc định giữ nguyên — chỉ thêm khả năng override qua UC-04.
--
-- Không dùng chung permission "manage" rộng theo cả module (kiểu
-- facility.manage/finance.manage đã có) vì trong cùng 1 Phân hệ, các UC có
-- tập role khác nhau (VD Phân hệ 6: ClassService cho phép STAFF, nhưng
-- GradeService/CurriculumService chỉ HEAD_ACADEMIC) — gộp chung sẽ vô tình
-- cấp thêm quyền chưa từng có, vi phạm business-fidelity.
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('academic.class.manage',     'Xếp lớp & gán khóa học',              'ACADEMIC', 'UC-18'),
('academic.curriculum.manage','Quản lý khung chương trình',          'ACADEMIC', 'UC-16/16b/17'),
('academic.grade.manage',     'Cấu hình sổ điểm',                    'ACADEMIC', 'UC-19/20 (cấu hình kỳ/thành phần điểm)'),
('student.status.manage',     'Cập nhật trạng thái học tập',         'STUDENT',  'UC-14'),
('crm.lead.manage',           'Quản lý lead & tư vấn tuyển sinh',    'CRM',      'UC-33/34'),
('crm.lead.assign',           'Phân phối lead cho nhân viên tư vấn', 'CRM',      'UC-33 Main Flow bước 2'),
('facility.room.manage',      'Quản lý phòng học & thiết bị',        'FACILITY', 'UC-37'),
('lms.exercise.manage',       'Soạn ngân hàng câu hỏi & đề kiểm tra','LMS',      'UC-40'),
('lms.grading.manage',        'Chấm bài thủ công',                   'LMS',      'UC-41'),
('finance.report.view',       'Xem báo cáo tài chính tổng hợp toàn chuỗi', 'FINANCE', 'UC-32 Main Flow bước 3 (Ban giám đốc)');

-- Gán role mặc định — khớp đúng tập role đang hardcode trong Service trước khi refactor.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.class.manage' AND r.code IN ('HEAD_ACADEMIC', 'STAFF');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.curriculum.manage' AND r.code IN ('HEAD_ACADEMIC');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.grade.manage' AND r.code IN ('HEAD_ACADEMIC');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'student.status.manage' AND r.code IN ('SITE_MANAGER', 'STAFF');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'crm.lead.manage' AND r.code IN ('STAFF');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'crm.lead.assign' AND r.code IN ('STAFF', 'SITE_MANAGER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'facility.room.manage' AND r.code IN ('STAFF', 'HEAD_ACADEMIC');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.exercise.manage' AND r.code IN ('TEACHER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'lms.grading.manage' AND r.code IN ('TEACHER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'finance.report.view' AND r.code IN ('EXECUTIVE');

-- Cố tình KHÔNG gán thêm cho SYS_ADMIN: trước refactor, SYS_ADMIN không nằm
-- trong bất kỳ AUTHORIZED_ROLE_CODES nào của 12 Service này — gán thêm ở
-- đây sẽ là cấp quyền mới ngoài hành vi hiện tại (business-fidelity). Nếu
-- cần, dùng UC-04 (user_permission_overrides) để cấp ngoại lệ đích danh.
