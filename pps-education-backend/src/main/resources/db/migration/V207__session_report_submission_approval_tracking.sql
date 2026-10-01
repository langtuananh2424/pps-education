-- =====================================================================
-- V207: Theo dõi nộp & duyệt báo cáo buổi học (bổ sung ngoài SDD gốc, đã
-- xác nhận với người dùng 2026-10-01).
--
-- "Báo cáo buổi học" = giáo viên gửi duyệt nhận xét của buổi (UC-21) →
-- Quản lý điểm trường duyệt / từ chối (UC-22) → giáo viên gửi lại nếu bị
-- từ chối. Mọi mốc thời gian đã có sẵn ở approval_flows (mỗi lần gửi duyệt
-- tạo 1 bản ghi mới: submitted_at, decided_at, approver_id, decision) và
-- student_comments — KHÔNG thêm bảng mới. Trạng thái 3 khâu (nộp / duyệt /
-- gửi lại) tính trong SessionReportTrackingService theo các hạn dưới đây.
--
--   1) Cấu hình hạn + cảnh báo (category NOTIFICATION, mirror class_checkin_alert.* V184).
--   2) 5 cột đánh dấu đã gửi cảnh báo trên class_sessions (mirror
--      checkin_*_alert_sent_at) để job quét mỗi phút không gửi lặp. Khâu duyệt
--      và gửi lại có thể lặp nhiều vòng (từ chối → gửi lại → duyệt), nên được
--      gửi lại cảnh báo khi vòng mới bắt đầu sau mốc đã gửi.
--   3) Quyền report.session-report.view (trang "Tình hình nộp & duyệt báo cáo").
-- =====================================================================

ALTER TABLE class_sessions ADD COLUMN report_due_soon_alert_sent_at          TIMESTAMPTZ NULL;
ALTER TABLE class_sessions ADD COLUMN report_overdue_alert_sent_at           TIMESTAMPTZ NULL;
ALTER TABLE class_sessions ADD COLUMN report_approval_due_soon_alert_sent_at TIMESTAMPTZ NULL;
ALTER TABLE class_sessions ADD COLUMN report_approval_overdue_alert_sent_at  TIMESTAMPTZ NULL;
ALTER TABLE class_sessions ADD COLUMN report_resubmit_overdue_alert_sent_at  TIMESTAMPTZ NULL;

-- Backfill: buổi học trước ngày triển khai KHÔNG cảnh báo lại (tránh dội hàng
-- loạt thông báo cho lịch sử cũ khi job chạy lần đầu) — mirror V184.
UPDATE class_sessions
SET report_due_soon_alert_sent_at          = now(),
    report_overdue_alert_sent_at           = now(),
    report_approval_due_soon_alert_sent_at = now(),
    report_approval_overdue_alert_sent_at  = now(),
    report_resubmit_overdue_alert_sent_at  = now()
WHERE session_date < CURRENT_DATE;

INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('session_report_alert.enabled', 'true', 'Bật/tắt cảnh báo nộp & duyệt báo cáo buổi học (nhắc giáo viên, nhắc Quản lý điểm trường, báo Trưởng phòng đào tạo, tổng hợp hằng ngày)', 'NOTIFICATION'),
('session_report_alert.submit_deadline_hours', '24', 'Hạn giáo viên gửi duyệt nhận xét (báo cáo) của buổi học: số giờ tính từ lúc buổi học kết thúc', 'NOTIFICATION'),
('session_report_alert.submit_due_soon_minutes', '60', 'Nhắc giáo viên trước hạn nộp báo cáo bao nhiêu phút (0 = không nhắc trước)', 'NOTIFICATION'),
('session_report_alert.approval_deadline_hours', '24', 'Hạn Quản lý điểm trường duyệt/từ chối, tính từ lúc giáo viên gửi duyệt (giờ)', 'NOTIFICATION'),
('session_report_alert.approval_due_soon_minutes', '120', 'Nhắc Quản lý điểm trường trước hạn duyệt bao nhiêu phút (0 = không nhắc trước)', 'NOTIFICATION'),
('session_report_alert.resubmit_deadline_hours', '12', 'Hạn giáo viên sửa và gửi lại sau khi bị từ chối, tính từ lúc bị từ chối (giờ)', 'NOTIFICATION'),
('session_report_alert.daily_digest_hour', '7', 'Giờ gửi bản tổng hợp hằng ngày (hôm qua) cho Trưởng phòng đào tạo (0-23, giờ VN; -1 = không gửi)', 'NOTIFICATION');

INSERT INTO permissions (code, name, module, description) VALUES
('report.session-report.view', 'Xem, xuất tình hình nộp & duyệt báo cáo buổi học', 'ACADEMIC', 'V207 — theo dõi nộp & duyệt báo cáo')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('HEAD_ACADEMIC', 'EXECUTIVE', 'SYS_ADMIN')
  AND p.code = 'report.session-report.view'
ON CONFLICT (role_id, permission_id) DO NOTHING;
