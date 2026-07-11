-- =====================================================================
-- V10: Module Notification (dùng chung mọi phân hệ) - channel-agnostic:
-- notifications (nội dung) tách khỏi notification_deliveries (kênh gửi).
-- Mở khóa UC-01 A2 (cảnh báo SYS_ADMIN khi khóa tài khoản) và các TODO
-- Notification đã để lại ở UC-09/UC-10/UC-11.
-- =====================================================================

-- a) notifications -- Thông báo (không có history, bản chất là log)
CREATE TABLE notifications (
    id                    BIGSERIAL PRIMARY KEY,
    uuid                  UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    recipient_user_id     BIGINT NOT NULL REFERENCES users(id),
    notification_type     VARCHAR(50) NOT NULL, -- ATTENDANCE_ABSENT / TASK_ASSIGNED / TASK_COMMENT /
                                                 -- INVOICE_DUE / GRADE_PUBLISHED / COMMENT_APPROVED /
                                                 -- PARTNER_FEEDBACK / LEAVE_REQUEST_STATUS /
                                                 -- SYSTEM_ANNOUNCEMENT / OTHER
    title                 VARCHAR(500) NOT NULL,
    content               TEXT NOT NULL,
    metadata              JSONB NULL,
    entity_type           VARCHAR(50) NULL, -- loại object liên quan để click chuyển trang
    entity_id             BIGINT NULL,
    priority              VARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- LOW / NORMAL / HIGH / URGENT
    triggered_by          BIGINT NULL REFERENCES users(id), -- NULL = system
    read_at               TIMESTAMPTZ NULL,
    dismissed_at          TIMESTAMPTZ NULL,
    expires_at            TIMESTAMPTZ NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now() -- SDD không liệt kê cột này trong bảng
                                                              -- nhưng index bên dưới (cũng từ SDD) dùng
                                                              -- created_at -- bắt buộc phải có cột
);
CREATE INDEX idx_notif_recipient_unread ON notifications(recipient_user_id, created_at DESC)
    WHERE read_at IS NULL AND dismissed_at IS NULL;

-- b) notification_deliveries -- Gửi qua kênh (1 notification -> nhiều kênh)
CREATE TABLE notification_deliveries (
    id                     BIGSERIAL PRIMARY KEY,
    notification_id        BIGINT NOT NULL REFERENCES notifications(id),
    channel                 VARCHAR(20) NOT NULL, -- IN_APP / EMAIL / SMS / ZALO / PUSH
    recipient_address       VARCHAR(500) NULL, -- snapshot email/SĐT/device token tại thời điểm gửi
    delivery_status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / QUEUED / SENT / DELIVERED / FAILED / BOUNCED
    sent_at                  TIMESTAMPTZ NULL,
    delivered_at             TIMESTAMPTZ NULL,
    error_message            TEXT NULL,
    retry_count               INT NOT NULL DEFAULT 0,
    next_retry_at             TIMESTAMPTZ NULL,
    provider                  VARCHAR(50) NULL, -- VD "SMTP-SendGrid"
    provider_message_id       VARCHAR(200) NULL
);
CREATE INDEX idx_notif_deliveries_pending ON notification_deliveries(delivery_status, next_retry_at);

-- c) notification_preferences -- Cấu hình theo user
CREATE TABLE notification_preferences (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id),
    notification_type  VARCHAR(50) NOT NULL,
    in_app_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    zalo_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    push_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(user_id, notification_type)
);
