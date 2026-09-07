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

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — UUID sinh + lưu localStorage
     * phía client, định danh 1 thiết bị vật lý cụ thể (không phải loại hệ điều hành). Dùng để dedupe
     * token cũ khi đăng ký token mới (NotificationService.registerDeviceToken) — 2 thiết bị khác
     * nhau cùng platform (VD 2 điện thoại Android) không giành nhau 1 "suất" push. Nullable vì token
     * đăng ký trước migration này (V163) chưa có giá trị.
     */
    @Column(name = "device_id", length = 100)
    private String deviceId;

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — User-Agent đọc từ HTTP header
     * lúc đăng ký. Dùng làm dấu hiệu nhận diện thiết bị BỀN HƠN deviceId: xoá app cài lại làm mất
     * localStorage nên deviceId đổi mới hoàn toàn, còn User-Agent thì không đổi.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
