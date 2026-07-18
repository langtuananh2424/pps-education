package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng notification_preferences (SDD > Notifications > c) — cấu hình kênh
 * bật/tắt theo user + loại thông báo. Không có record → mặc định in-app +
 * email = enabled (Logic gửi thông báo, bước 2).
 */
@Getter
@Setter
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private Notification.NotificationType notificationType;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = false;

    @Column(name = "zalo_enabled", nullable = false)
    private boolean zaloEnabled = false;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = false;
}
