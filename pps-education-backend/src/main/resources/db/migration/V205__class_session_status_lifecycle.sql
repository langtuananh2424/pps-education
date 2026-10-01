-- =====================================================================
-- V205: Vòng đời trạng thái buổi học + hủy/sửa buổi đã diễn ra (UC-48 A5–A7).
--
-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-01:
--   1. ClassSessionStatusSchedulerService chạy mỗi phút, chuyển
--      class_sessions SCHEDULED → IN_PROGRESS (tới giờ bắt đầu) và
--      SCHEDULED/IN_PROGRESS → COMPLETED (qua giờ kết thúc). Index một phần
--      bên dưới giữ lệnh UPDATE của job chỉ quét các buổi chưa kết thúc,
--      không quét toàn bộ lịch sử buổi đã dạy.
--   2. academic.class-session.correct-past — hủy (→ CANCELLED) hoặc sửa phân
--      công/tiết cùng ngày của buổi đã IN_PROGRESS/COMPLETED, bắt buộc lý do.
--      Dùng KÈM quyền nút gốc (academic.class-session.cancel /
--      academic.class-session.reschedule). Cấp mặc định cho Trưởng phòng đào
--      tạo; Ban giám đốc + Quản trị viên có mọi quyền (V202 Q4).
--
-- Không có bước chuyển dữ liệu cũ: lần chạy đầu của job tự chuyển mọi buổi
-- SCHEDULED đã qua giờ sang COMPLETED.
-- =====================================================================

INSERT INTO permissions (code, name, module, description) VALUES
('academic.class-session.correct-past', 'Hủy, sửa buổi học đã diễn ra', 'ACADEMIC', 'V205 — hủy/sửa buổi IN_PROGRESS/COMPLETED, bắt buộc lý do')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('HEAD_ACADEMIC', 'EXECUTIVE', 'SYS_ADMIN')
  AND p.code = 'academic.class-session.correct-past'
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_class_sessions_status_pending
    ON class_sessions (session_date)
    WHERE status IN ('SCHEDULED', 'IN_PROGRESS');
