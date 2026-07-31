-- =====================================================================
-- V62: Tách 10 permission "bundled" (*.manage) còn lại thành permission
-- chi tiết theo hành động, tiếp nối V51. Bổ sung ngoài SDD gốc, đã xác
-- nhận với người dùng 2026-07-29.
--
-- Đã audit @PreAuthorize thật (không chỉ API.md) cho cả 12 permission còn
-- "manage" — xác nhận KHÔNG có permission nào trong 10 permission dưới
-- đây được dùng trực tiếp trong Service (chỉ chặn Controller). 2 permission
-- CỐ TÌNH KHÔNG tách (không nằm trong migration này):
--   - academic.grade.manage, task.manage: đã xác định ở V51 là cờ override
--     dùng trực tiếp trong GradeService/TaskService (không chỉ chặn
--     Controller) — giữ nguyên, không đổi gì.
--   - student.status.manage: chỉ gate ĐÚNG 1 endpoint, đã là chi tiết nhất
--     có thể — tách thêm không tạo thêm giá trị kiểm soát truy cập.
--   - lms.grading.manage: 4 endpoint (2 domain: chấm bài viết UC-41 + chấm
--     luyện Nói UC-26) nhưng không có khía cạnh create/update/delete để
--     tách theo hành động — giữ nguyên.
--
-- Thứ tự bắt buộc (giống V51): (1) tạo permission mới, (2) backfill
-- role_permissions, (3) backfill user_permission_overrides, (4) gỡ tham
-- chiếu permission_audit_log, (5) xóa role_permissions/
-- user_permission_overrides trỏ permission cũ, (6) xóa permission cũ khỏi
-- catalog.
--
-- QUAN TRỌNG (khác V51): bước (2)/(3) backfill ĐỘNG dựa trên dữ liệu
-- role_permissions/user_permission_overrides HIỆN CÓ của từng database khi
-- chạy migration này — KHÔNG hardcode 1 danh sách role cố định. Lý do: mỗi
-- máy dev/nhánh có thể đã tự gán thêm quyền cũ cho role khác qua UC-03,
-- hoặc override riêng cho 1 tài khoản cụ thể qua UC-04 (VD FE dev đang
-- chạy DB cũ, tự cấp override) — nếu chỉ hardcode theo đúng danh sách
-- role của V28/V51 thì các trường hợp tùy biến này sẽ bị XÓA MẤT ở bước
-- (5) mà không có quyền con thay thế, âm thầm thu hẹp quyền một tài khoản
-- đang dùng thật. Backfill động đảm bảo: "đang có permission bundled X
-- (dù qua role hay override cá nhân) thì được cấp ĐỦ (full) permission
-- con của X" — đúng yêu cầu xác nhận với người dùng 2026-07-30.
-- =====================================================================

-- (1) Permission mới
INSERT INTO permissions (code, name, module, description) VALUES
-- user.manage -- UserController (UC-43/44/47/49/55)
('user.view',   'Xem/tra cứu danh sách & chi tiết tài khoản', 'USER', 'Tách từ user.manage'),
('user.create', 'Khởi tạo tài khoản người dùng',              'USER', 'Tách từ user.manage'),
('user.update', 'Sửa hồ sơ/mật khẩu/email/trạng thái tài khoản', 'USER', 'Tách từ user.manage'),

-- academic.curriculum.manage -- CurriculumController (UC-16/16b/17)
('academic.curriculum.create',  'Tạo khung chương trình chuẩn',        'ACADEMIC', 'Tách từ academic.curriculum.manage'),
('academic.curriculum.update',  'Sửa khung chương trình / thêm môn học', 'ACADEMIC', 'Tách từ academic.curriculum.manage'),
('academic.curriculum.approve', 'Duyệt đề xuất khung chương trình tùy biến', 'ACADEMIC', 'UC-17 (tách từ academic.curriculum.manage)'),

-- crm.lead.manage -- LeadController (UC-33/34)
('crm.lead.create',  'Tạo lead mới',                    'CRM', 'Tách từ crm.lead.manage'),
('crm.lead.update',  'Cập nhật trạng thái lead',        'CRM', 'Tách từ crm.lead.manage'),
('crm.lead.convert', 'Chuyển đổi lead thành học sinh',  'CRM', 'UC-34 (tách từ crm.lead.manage)'),

-- facility.room.manage -- RoomController + EquipmentController (UC-37)
('facility.room.create',      'Tạo phòng học',              'FACILITY', 'Tách từ facility.room.manage'),
('facility.room.update',      'Sửa phòng học',              'FACILITY', 'Tách từ facility.room.manage'),
('facility.equipment.create', 'Tạo thiết bị dạy học',       'FACILITY', 'Tách từ facility.room.manage'),
('facility.equipment.update', 'Sửa trạng thái thiết bị',    'FACILITY', 'Tách từ facility.room.manage'),

-- finance.invoice.manage -- InvoiceController
('finance.invoice.generate',       'Sinh hóa đơn thủ công',            'FINANCE', 'Tách từ finance.invoice.manage'),
('finance.invoice.payment.record', 'Ghi nhận thanh toán thủ công',     'FINANCE', 'Tách từ finance.invoice.manage'),

-- finance.scholarship.manage -- ScholarshipController
('finance.scholarship.create', 'Cấp học bổng/miễn giảm',   'FINANCE', 'Tách từ finance.scholarship.manage'),
('finance.scholarship.revoke', 'Thu hồi học bổng/miễn giảm', 'FINANCE', 'Tách từ finance.scholarship.manage'),

-- finance.tuition-plan.manage -- TuitionPlanController
('finance.tuition-plan.create', 'Tạo gói học phí',                    'FINANCE', 'Tách từ finance.tuition-plan.manage'),
('finance.tuition-plan.view',   'Xem chi tiết gói học phí',           'FINANCE', 'Tách từ finance.tuition-plan.manage'),
('finance.tuition-plan.assign', 'Gán gói học phí cho lớp/học sinh',   'FINANCE', 'Tách từ finance.tuition-plan.manage'),
('finance.tuition-plan.update', 'Sửa trạng thái gói học phí',         'FINANCE', 'Tách từ finance.tuition-plan.manage'),

-- lms.exercise.manage -- ExerciseController (đề kiểm tra) + QuestionBankController (ngân hàng câu hỏi, 2 resource khác nhau)
('lms.exercise.create',      'Tạo đề kiểm tra',                  'LMS', 'Tách từ lms.exercise.manage'),
('lms.exercise.update',      'Thêm câu hỏi vào đề kiểm tra',     'LMS', 'Tách từ lms.exercise.manage'),
('lms.exercise.publish',     'Publish đề kiểm tra cho lớp',      'LMS', 'Tách từ lms.exercise.manage'),
('lms.question-bank.create', 'Tạo ngân hàng câu hỏi / câu hỏi',  'LMS', 'Tách từ lms.exercise.manage'),
('lms.question-bank.update', 'Sửa ngân hàng câu hỏi / câu hỏi',  'LMS', 'Tách từ lms.exercise.manage'),
('lms.question-bank.view',   'Xem câu hỏi kèm đáp án đúng',      'LMS', 'Tách từ lms.exercise.manage'),

-- lms.document.manage -- CurriculumDocumentController (UC-60)
('lms.document.create', 'Thêm tài liệu tham khảo',   'LMS', 'Tách từ lms.document.manage'),
('lms.document.update', 'Sửa tài liệu tham khảo',    'LMS', 'Tách từ lms.document.manage'),
('lms.document.view',   'Xem danh sách tài liệu',    'LMS', 'Tách từ lms.document.manage'),

-- lms.listening-practice.manage -- ListeningPracticeController (UC-26)
('lms.listening-practice.create', 'Tạo bài luyện Nghe-Nói',   'LMS', 'Tách từ lms.listening-practice.manage'),
('lms.listening-practice.update', 'Sửa bài luyện Nghe-Nói',   'LMS', 'Tách từ lms.listening-practice.manage'),
('lms.listening-practice.view',   'Xem danh sách bài luyện',  'LMS', 'Tách từ lms.listening-practice.manage');

-- Bảng ánh xạ "permission bundled cũ -> tập permission con mới" dùng chung cho cả bước (2) và (3).
-- Liệt kê ĐẦY ĐỦ 33 quyền con mới, mỗi quyền cũ trỏ tới TOÀN BỘ quyền con của nó (full quyền).
-- (2) Backfill role_permissions: bất kỳ role nào ĐANG giữ permission cũ (qua role_permissions hiện
-- có trong CHÍNH database đang chạy migration này — không phải danh sách cố định) đều được gán đủ
-- toàn bộ permission con mới tương ứng.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, newp.id
FROM role_permissions rp
JOIN permissions oldp ON oldp.id = rp.permission_id
JOIN (VALUES
    ('user.manage','user.view'), ('user.manage','user.create'), ('user.manage','user.update'),
    ('academic.curriculum.manage','academic.curriculum.create'), ('academic.curriculum.manage','academic.curriculum.update'), ('academic.curriculum.manage','academic.curriculum.approve'),
    ('crm.lead.manage','crm.lead.create'), ('crm.lead.manage','crm.lead.update'), ('crm.lead.manage','crm.lead.convert'),
    ('facility.room.manage','facility.room.create'), ('facility.room.manage','facility.room.update'), ('facility.room.manage','facility.equipment.create'), ('facility.room.manage','facility.equipment.update'),
    ('finance.invoice.manage','finance.invoice.generate'), ('finance.invoice.manage','finance.invoice.payment.record'),
    ('finance.scholarship.manage','finance.scholarship.create'), ('finance.scholarship.manage','finance.scholarship.revoke'),
    ('finance.tuition-plan.manage','finance.tuition-plan.create'), ('finance.tuition-plan.manage','finance.tuition-plan.view'), ('finance.tuition-plan.manage','finance.tuition-plan.assign'), ('finance.tuition-plan.manage','finance.tuition-plan.update'),
    ('lms.exercise.manage','lms.exercise.create'), ('lms.exercise.manage','lms.exercise.update'), ('lms.exercise.manage','lms.exercise.publish'),
    ('lms.exercise.manage','lms.question-bank.create'), ('lms.exercise.manage','lms.question-bank.update'), ('lms.exercise.manage','lms.question-bank.view'),
    ('lms.document.manage','lms.document.create'), ('lms.document.manage','lms.document.update'), ('lms.document.manage','lms.document.view'),
    ('lms.listening-practice.manage','lms.listening-practice.create'), ('lms.listening-practice.manage','lms.listening-practice.update'), ('lms.listening-practice.manage','lms.listening-practice.view')
) AS split_map(old_code, new_code) ON split_map.old_code = oldp.code
JOIN permissions newp ON newp.code = split_map.new_code
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2 WHERE rp2.role_id = rp.role_id AND rp2.permission_id = newp.id
);

-- (3) Backfill user_permission_overrides: TƯƠNG TỰ bước (2) nhưng cho override cá nhân (UC-04) —
-- giữ NGUYÊN override_type (GRANT/REVOKE): tài khoản đang bị REVOKE quyền cũ thì bị REVOKE toàn bộ
-- quyền con mới; đang được GRANT thì được GRANT toàn bộ. Bổ sung so với V51 (V51 không có bước này,
-- nhưng lúc đó chưa phát hiện rủi ro override cá nhân bị xóa mất không thay thế).
INSERT INTO user_permission_overrides (user_id, permission_id, override_type, reason, granted_by, granted_at, expires_at)
SELECT DISTINCT upo.user_id, newp.id, upo.override_type,
       COALESCE(upo.reason || ' — ', '') || '(tự động chuyển từ ' || oldp.code || ' khi tách quyền V62)',
       upo.granted_by, upo.granted_at, upo.expires_at
FROM user_permission_overrides upo
JOIN permissions oldp ON oldp.id = upo.permission_id
JOIN (VALUES
    ('user.manage','user.view'), ('user.manage','user.create'), ('user.manage','user.update'),
    ('academic.curriculum.manage','academic.curriculum.create'), ('academic.curriculum.manage','academic.curriculum.update'), ('academic.curriculum.manage','academic.curriculum.approve'),
    ('crm.lead.manage','crm.lead.create'), ('crm.lead.manage','crm.lead.update'), ('crm.lead.manage','crm.lead.convert'),
    ('facility.room.manage','facility.room.create'), ('facility.room.manage','facility.room.update'), ('facility.room.manage','facility.equipment.create'), ('facility.room.manage','facility.equipment.update'),
    ('finance.invoice.manage','finance.invoice.generate'), ('finance.invoice.manage','finance.invoice.payment.record'),
    ('finance.scholarship.manage','finance.scholarship.create'), ('finance.scholarship.manage','finance.scholarship.revoke'),
    ('finance.tuition-plan.manage','finance.tuition-plan.create'), ('finance.tuition-plan.manage','finance.tuition-plan.view'), ('finance.tuition-plan.manage','finance.tuition-plan.assign'), ('finance.tuition-plan.manage','finance.tuition-plan.update'),
    ('lms.exercise.manage','lms.exercise.create'), ('lms.exercise.manage','lms.exercise.update'), ('lms.exercise.manage','lms.exercise.publish'),
    ('lms.exercise.manage','lms.question-bank.create'), ('lms.exercise.manage','lms.question-bank.update'), ('lms.exercise.manage','lms.question-bank.view'),
    ('lms.document.manage','lms.document.create'), ('lms.document.manage','lms.document.update'), ('lms.document.manage','lms.document.view'),
    ('lms.listening-practice.manage','lms.listening-practice.create'), ('lms.listening-practice.manage','lms.listening-practice.update'), ('lms.listening-practice.manage','lms.listening-practice.view')
) AS split_map(old_code, new_code) ON split_map.old_code = oldp.code
JOIN permissions newp ON newp.code = split_map.new_code
WHERE NOT EXISTS (
    SELECT 1 FROM user_permission_overrides upo2 WHERE upo2.user_id = upo.user_id AND upo2.permission_id = newp.id
);

-- (4) Gỡ tham chiếu permission_audit_log tới permission cũ sắp xóa -- GIỮ NGUYÊN dòng audit
-- (details JSONB đã tự chứa snapshot code/name lúc phát sinh sự kiện), chỉ NULL cột FK.
UPDATE permission_audit_log
SET target_permission_id = NULL
WHERE target_permission_id IN (
    SELECT id FROM permissions WHERE code IN (
        'user.manage', 'academic.curriculum.manage', 'crm.lead.manage', 'facility.room.manage',
        'finance.invoice.manage', 'finance.scholarship.manage', 'finance.tuition-plan.manage',
        'lms.exercise.manage', 'lms.document.manage', 'lms.listening-practice.manage'
    )
);

-- (5) Xóa role_permissions / user_permission_overrides còn trỏ tới permission cũ
DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id FROM permissions WHERE code IN (
        'user.manage', 'academic.curriculum.manage', 'crm.lead.manage', 'facility.room.manage',
        'finance.invoice.manage', 'finance.scholarship.manage', 'finance.tuition-plan.manage',
        'lms.exercise.manage', 'lms.document.manage', 'lms.listening-practice.manage'
    )
);

DELETE FROM user_permission_overrides
WHERE permission_id IN (
    SELECT id FROM permissions WHERE code IN (
        'user.manage', 'academic.curriculum.manage', 'crm.lead.manage', 'facility.room.manage',
        'finance.invoice.manage', 'finance.scholarship.manage', 'finance.tuition-plan.manage',
        'lms.exercise.manage', 'lms.document.manage', 'lms.listening-practice.manage'
    )
);

-- (6) Xóa hẳn 10 permission "bundled" khỏi catalog
DELETE FROM permissions WHERE code IN (
    'user.manage', 'academic.curriculum.manage', 'crm.lead.manage', 'facility.room.manage',
    'finance.invoice.manage', 'finance.scholarship.manage', 'finance.tuition-plan.manage',
    'lms.exercise.manage', 'lms.document.manage', 'lms.listening-practice.manage'
);
