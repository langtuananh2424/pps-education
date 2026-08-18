-- =====================================================================
-- V126: UC-71 "Nhận lớp" — Giáo viên xác nhận có mặt để dạy theo TỪNG
-- buổi học (class_sessions), độc lập với chấm công ca hành chính (UC-09).
-- Bổ sung HOÀN TOÀN ngoài SDD/SRS gốc, đã xác nhận với người dùng
-- 2026-08-18. Xem docs/uc/phan-he-06-hoc-thuat.md (UC-71).
--
-- Đồng thời mở rộng quyền hrm.employee-schedule.view (V125) cho
-- SITE_MANAGER — trang "Lịch làm việc" (UC-70) giờ gộp thêm trạng thái
-- nhận lớp, Quản lý điểm trường cần thấy roster của điểm trường mình phụ
-- trách (site-scoping thực hiện ở EmployeeScheduleService, không sửa V125
-- cũ).
-- =====================================================================

CREATE TABLE class_session_check_ins (
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    class_session_id  BIGINT NOT NULL UNIQUE REFERENCES class_sessions(id),
    teacher_id        BIGINT NOT NULL REFERENCES users(id),
    check_in_time     TIMESTAMPTZ NOT NULL,
    status            VARCHAR(20) NOT NULL, -- ON_TIME | LATE
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    site_id           BIGINT NOT NULL REFERENCES sites(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_class_session_check_ins_teacher ON class_session_check_ins(teacher_id);
CREATE INDEX idx_class_session_check_ins_site ON class_session_check_ins(site_id);

INSERT INTO permissions (code, name, module, description) VALUES
('academic.class-session.checkin', 'Nhận lớp (xác nhận có mặt dạy 1 buổi học)', 'ACADEMIC', 'UC-71 (bổ sung ngoài SDD gốc)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'academic.class-session.checkin'
  AND r.code IN ('TEACHER', 'SUPER_ADMIN', 'SYS_ADMIN');

-- Mở rộng hrm.employee-schedule.view (V125) cho SITE_MANAGER — không sửa
-- lại V125, chỉ thêm role_permissions còn thiếu.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'hrm.employee-schedule.view'
  AND r.code = 'SITE_MANAGER';
