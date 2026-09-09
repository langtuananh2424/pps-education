-- =====================================================================
-- V169: Chuẩn hoá `name` (nhãn hiển thị trên UI Cấu hình vai trò / danh
-- mục quyền — PermissionChecklist) của 13 permission cũ đang để chữ Việt
-- KHÔNG DẤU (viết tắt kiểu điện tín), cho khớp phong cách các quyền mới
-- (VD "Xem hồ sơ phụ huynh", "Tạo kỳ đánh giá"...).
--
-- Không đổi `code`, `module`, ma trận `role_permissions` hay bất kỳ hành
-- vi phân quyền nào — thuần cập nhật nhãn tiếng Việt.
--
-- Nhóm bị ảnh hưởng (xem docs/sdd-groups/02 — nhóm PBAC):
--   - academic.grade.*        (V38/V39 — Sổ điểm)
--   - finance.expense.approve (V30 — Duyệt chi vận hành)
--   - report.*                (V108/V111/V114/V133 — Báo cáo & thống kê)
--
-- Idempotent: chạy lại chỉ ghi đè đúng giá trị đã đặt, không phát sinh
-- thêm dòng.
-- =====================================================================

UPDATE permissions SET name = 'Duyệt/Từ chối điểm học phần',                      updated_at = now() WHERE code = 'academic.grade.approve';
UPDATE permissions SET name = 'Toàn quyền thêm/sửa/xóa điểm bất kể trạng thái',   updated_at = now() WHERE code = 'academic.grade.edit.override';
UPDATE permissions SET name = 'Duyệt/Từ chối chi vận hành',                       updated_at = now() WHERE code = 'finance.expense.approve';

UPDATE permissions SET name = 'Xem thống kê số tiết thực tế theo lớp',            updated_at = now() WHERE code = 'report.actual-periods.view';
UPDATE permissions SET name = 'Xem thống kê nhận xét ngày',                       updated_at = now() WHERE code = 'report.daily-comment.view';
UPDATE permissions SET name = 'Xem thống kê biến động học sinh các lớp theo kỳ',  updated_at = now() WHERE code = 'report.enrollment-stats.view';
UPDATE permissions SET name = 'Tạo và xuất báo cáo từ mẫu',                       updated_at = now() WHERE code = 'report.generate';
UPDATE permissions SET name = 'Xem thống kê điểm số',                             updated_at = now() WHERE code = 'report.grade.view';
UPDATE permissions SET name = 'Xem hồ sơ quá trình học tập',                      updated_at = now() WHERE code = 'report.student-progress.view';
UPDATE permissions SET name = 'Tạo mẫu báo cáo (upload file + đánh dấu placeholder)', updated_at = now() WHERE code = 'report.template.create';
UPDATE permissions SET name = 'Xóa mẫu báo cáo',                                  updated_at = now() WHERE code = 'report.template.delete';
UPDATE permissions SET name = 'Sửa mẫu báo cáo / cấu hình field mapping',         updated_at = now() WHERE code = 'report.template.update';
UPDATE permissions SET name = 'Xem danh sách/chi tiết mẫu báo cáo',              updated_at = now() WHERE code = 'report.template.view';
