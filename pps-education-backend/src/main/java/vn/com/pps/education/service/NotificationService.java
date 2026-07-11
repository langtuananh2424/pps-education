package vn.com.pps.education.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.NotificationPreference;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.NotificationPreferenceRequest;
import vn.com.pps.education.dto.NotificationPreferenceResponse;
import vn.com.pps.education.dto.NotificationResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.NotificationDeliveryRepository;
import vn.com.pps.education.repository.NotificationPreferenceRepository;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.service.notification.NotificationChannelSender;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Module Notification (SDD > Task Management & Thông báo > Notifications).
 * "Logic gửi thông báo": (1) tạo notifications, (2) đọc
 * notification_preferences (không có record → mặc định in-app+email =
 * enabled), (3) mỗi kênh enabled + có sender đã đăng ký → tạo
 * notification_deliveries. IN_APP không cần gửi thật — đánh dấu SENT ngay
 * lúc tạo; các kênh khác (EMAIL...) ở PENDING, NotificationDispatchService
 * xử lý thật.
 */
@Service
public class NotificationService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final List<NotificationChannelSender> senders;

    public NotificationService(UserRepository userRepository,
                                NotificationRepository notificationRepository,
                                NotificationDeliveryRepository notificationDeliveryRepository,
                                NotificationPreferenceRepository notificationPreferenceRepository,
                                List<NotificationChannelSender> senders) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.senders = senders;
    }

    /** Overload gọn cho các trường hợp không cần metadata/entity liên kết. */
    @Transactional
    public Notification notify(Long recipientUserId, Notification.NotificationType type, String title, String content) {
        return notify(recipientUserId, type, title, content, null, null, null,
                Notification.Priority.NORMAL, null);
    }

    @Transactional
    public Notification notify(Long recipientUserId, Notification.NotificationType type, String title, String content,
                                Map<String, Object> metadata, String entityType, Long entityId,
                                Notification.Priority priority, Long triggeredByUserId) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + recipientUserId));
        User triggeredBy = triggeredByUserId == null ? null : userRepository.findById(triggeredByUserId).orElse(null);

        Notification notification = new Notification();
        notification.setRecipientUser(recipient);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setMetadata(metadata);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        notification.setPriority(priority == null ? Notification.Priority.NORMAL : priority);
        notification.setTriggeredBy(triggeredBy);
        notification = notificationRepository.save(notification);

        dispatchByPreference(notification, recipientUserId, type);
        return notification;
    }

    private void dispatchByPreference(Notification notification, Long recipientUserId, Notification.NotificationType type) {
        NotificationPreference pref = notificationPreferenceRepository
                .findByUserIdAndNotificationType(recipientUserId, type).orElse(null);

        // Không có record -> mặc định in-app + email = enabled (Logic gửi thông báo bước 2).
        boolean inAppEnabled = pref == null || pref.isInAppEnabled();
        if (inAppEnabled) {
            createInAppDelivery(notification);
        }

        for (NotificationChannelSender sender : senders) {
            if (isChannelEnabled(sender.channel(), pref)) {
                createPendingDelivery(notification, sender.channel());
            }
        }
    }

    private boolean isChannelEnabled(NotificationDelivery.Channel channel, NotificationPreference pref) {
        return switch (channel) {
            case EMAIL -> pref == null || pref.isEmailEnabled();
            case SMS -> pref != null && pref.isSmsEnabled();
            case ZALO -> pref != null && pref.isZaloEnabled();
            case PUSH -> pref != null && pref.isPushEnabled();
            case IN_APP -> false; // xử lý riêng ở createInAppDelivery, không qua sender
        };
    }

    private void createInAppDelivery(Notification notification) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotification(notification);
        delivery.setChannel(NotificationDelivery.Channel.IN_APP);
        delivery.setDeliveryStatus(NotificationDelivery.DeliveryStatus.SENT);
        delivery.setSentAt(OffsetDateTime.now());
        notificationDeliveryRepository.save(delivery);
    }

    private void createPendingDelivery(Notification notification, NotificationDelivery.Channel channel) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotification(notification);
        delivery.setChannel(channel);
        delivery.setDeliveryStatus(NotificationDelivery.DeliveryStatus.PENDING);
        notificationDeliveryRepository.save(delivery);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMine(Long recipientUserId, Pageable pageable) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public NotificationResponse markRead(Long recipientUserId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo id=" + notificationId));
        if (!notification.getRecipientUser().getId().equals(recipientUserId)) {
            throw new ResourceNotFoundException("Không tìm thấy thông báo id=" + notificationId);
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreference(Long userId, Notification.NotificationType type) {
        return notificationPreferenceRepository.findByUserIdAndNotificationType(userId, type)
                .map(this::toResponse)
                .orElseGet(() -> new NotificationPreferenceResponse(type.name(), true, true, false, false, false));
    }

    @Transactional
    public NotificationPreferenceResponse upsertPreference(Long userId, Notification.NotificationType type,
                                                             NotificationPreferenceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
        NotificationPreference pref = notificationPreferenceRepository.findByUserIdAndNotificationType(userId, type)
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setUser(user);
                    created.setNotificationType(type);
                    return created;
                });
        pref.setInAppEnabled(request.inAppEnabled());
        pref.setEmailEnabled(request.emailEnabled());
        pref.setSmsEnabled(request.smsEnabled());
        pref.setZaloEnabled(request.zaloEnabled());
        pref.setPushEnabled(request.pushEnabled());
        return toResponse(notificationPreferenceRepository.save(pref));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getNotificationType().name(), n.getTitle(), n.getContent(),
                n.getEntityType(), n.getEntityId(), n.getPriority().name(), n.getCreatedAt(), n.getReadAt());
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference p) {
        return new NotificationPreferenceResponse(
                p.getNotificationType().name(), p.isInAppEnabled(), p.isEmailEnabled(),
                p.isSmsEnabled(), p.isZaloEnabled(), p.isPushEnabled());
    }
}
