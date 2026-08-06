-- =====================================================================
-- V96: UC-10/UC-11 (Đơn từ) — leave_substitutions: giáo viên dạy thay
-- theo đơn nghỉ (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
-- 2026-08-05). Xem docs/uc/phan-he-04-nhan-su.md UC-10 bước 5 / UC-11 A2
-- và docs/sdd-groups/05-nhan-su.md mục c) Đơn từ.
-- =====================================================================

CREATE TABLE leave_substitutions (
    id                       BIGSERIAL PRIMARY KEY,
    uuid                     UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    leave_request_id         BIGINT NOT NULL REFERENCES leave_requests(id),
    class_session_id         BIGINT NOT NULL REFERENCES class_sessions(id),
    class_teacher_id         BIGINT NOT NULL REFERENCES class_teachers(id),
    original_teacher_id      BIGINT NOT NULL REFERENCES users(id),
    substitute_teacher_id    BIGINT NOT NULL REFERENCES users(id),
    revoked_at               TIMESTAMPTZ NULL, -- NULL = đang dạy thay; có giá trị = đã thu hồi (log)
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_leave_substitutions_leave_request ON leave_substitutions(leave_request_id);
CREATE INDEX idx_leave_substitutions_class_teacher ON leave_substitutions(class_teacher_id);

-- 1 buổi học chỉ có đúng 1 lượt dạy thay ĐANG MỞ tại 1 thời điểm.
CREATE UNIQUE INDEX idx_leave_substitutions_active_session
    ON leave_substitutions(class_session_id)
    WHERE revoked_at IS NULL;
