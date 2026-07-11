package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng notification_deliveries (SDD > Notifications > b) — 1 notification
 * có thể gửi qua nhiều kênh (in-app + email hiện tại; SMS/Zalo/Push tương
 * lai), mỗi kênh trạng thái/retry riêng.
 */
@Getter
@Setter
@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {

    public enum Channel { IN_APP, EMAIL, SMS, ZALO, PUSH }

    public enum DeliveryStatus { PENDING, QUEUED, SENT, DELIVERED, FAILED, BOUNCED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    /** Snapshot email/SĐT/device token tại thời điểm gửi. */
    @Column(name = "recipient_address", length = 500)
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    /** VD "SMTP-SendGrid". */
    @Column(length = 50)
    private String provider;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;
}
