-- =====================================================================
-- V46: Phân rã quyền cấu hình sổ điểm academic.grade.manage thành các
-- quyền chi tiết theo resource (kỳ đánh giá / thành phần điểm) + CRUD, và
-- bổ sung quyền xoá (delete) cho 2 API DELETE mới (UC-19). Bổ sung ngoài
-- SDD gốc — đã xác nhận với người dùng 2026-07-22. Cập nhật lại
-- docs/uc/phan-he-06-hoc-thuat.md (ghi chú UC-19).
--
-- KHÁC V44 (student.manage): KHÔNG xoá academic.grade.manage vì FE hiện
-- vẫn gate menu/nút "Cấu hình sổ điểm" bằng hasPermission("academic.grade
-- .manage") (GradesPage.tsx, navigation.ts) và chưa đổi FE trong đợt này.
-- Giữ nguyên umbrella; các endpoint cấu hình chấp nhận "quyền chi tiết
-- HOẶC academic.grade.manage" (xem GradeController @PreAuthorize) nên:
--   - Tài khoản cũ có academic.grade.manage (HEAD_ACADEMIC): không đổi gì.
--   - Có thể gán riêng lẻ 1 quyền con cho tài khoản khác về sau (VD chỉ
--     academic.grade.component.delete) mà không phải cấp cả umbrella.
--
-- Giữ ma trận truy cập hiện tại: cấp đủ 6 quyền con cho đúng role đang có
-- academic.grade.manage (HEAD_ACADEMIC) + SUPER_ADMIN.
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('academic.grade.period.create',    'Tạo kỳ đánh giá',        'ACADEMIC', 'UC-19 (tách từ academic.grade.manage)'),
('academic.grade.period.update',    'Sửa kỳ đánh giá',        'ACADEMIC', 'UC-19 (tách từ academic.grade.manage)'),
('academic.grade.period.delete',    'Xoá kỳ đánh giá',        'ACADEMIC', 'UC-19 (xoá kỳ rỗng)'),
('academic.grade.component.create', 'Tạo thành phần điểm',    'ACADEMIC', 'UC-19 (tách từ academic.grade.manage)'),
('academic.grade.component.update', 'Sửa thành phần điểm',    'ACADEMIC', 'UC-19 (tách từ academic.grade.manage)'),
('academic.grade.component.delete', 'Xoá thành phần điểm',    'ACADEMIC', 'UC-19 (xoá thành phần chưa có điểm)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code IN ('academic.grade.period.create','academic.grade.period.update','academic.grade.period.delete',
                 'academic.grade.component.create','academic.grade.component.update','academic.grade.component.delete')
  AND r.code IN ('HEAD_ACADEMIC','SUPER_ADMIN');
