package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng device_tokens (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-08 — xem V104__notification_sms_push_channels.sql) — lưu FCM
 * device token hiện tại của user để PushNotificationSender tra cứu lúc
 * gửi. 1 user có thể có nhiều thiết bị (điện thoại + web) nên không unique
 * theo user_id, unique theo token.
 */
@Getter
@Setter
@Entity
@Table(name = "device_tokens")
public class DeviceToken extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    /** ANDROID / IOS / WEB. */
    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
