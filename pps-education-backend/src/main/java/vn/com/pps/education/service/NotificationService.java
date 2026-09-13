package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.DeviceToken;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.domain.NotificationPreference;
import vn.com.pps.education.domain.PushSetupLog;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.DeviceTokenCountResponse;
import vn.com.pps.education.dto.DeviceTokenRequest;
import vn.com.pps.education.dto.NotificationPreferenceRequest;
import vn.com.pps.education.dto.NotificationPreferenceResponse;
import vn.com.pps.education.dto.NotificationResponse;
import vn.com.pps.education.dto.PushSetupLogRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.DeviceTokenRepository;
import vn.com.pps.education.repository.PushSetupLogRepository;
import vn.com.pps.education.repository.NotificationDeliveryRepository;
import vn.com.pps.education.repository.NotificationPreferenceRepository;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.service.notification.NotificationChannelSender;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSetupLogRepository pushSetupLogRepository;
    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final List<NotificationChannelSender> senders;

    public NotificationService(UserRepository userRepository,
                                NotificationRepository notificationRepository,
                                NotificationDeliveryRepository notificationDeliveryRepository,
                                NotificationPreferenceRepository notificationPreferenceRepository,
                                DeviceTokenRepository deviceTokenRepository,
                                PushSetupLogRepository pushSetupLogRepository,
                                ParentRepository parentRepository,
                                StudentRepository studentRepository,
                                List<NotificationChannelSender> senders) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushSetupLogRepository = pushSetupLogRepository;
        this.parentRepository = parentRepository;
        this.studentRepository = studentRepository;
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
        return notifyInternal(recipientUserId, type, title, content, metadata, entityType, entityId,
                priority, triggeredByUserId, null);
    }

    /**
     * Dùng cho công cụ "Gửi thông báo thủ công" (bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng 2026-09-12) — {@code forceChannels} khi không
     * rỗng ÉP gửi đúng các kênh đó, bỏ qua notification_preferences của
     * người nhận, để cô lập test 1 kênh cụ thể (VD chỉ PUSH). Xem
     * {@link vn.com.pps.education.service.ManualNotificationSendService}.
     */
    @Transactional
    public Notification notifyWithForcedChannels(Long recipientUserId, Notification.NotificationType type, String title, String content,
                                                  Notification.Priority priority, Long triggeredByUserId,
                                                  Set<NotificationDelivery.Channel> forceChannels) {
        return notifyInternal(recipientUserId, type, title, content, null, null, null,
                priority, triggeredByUserId, forceChannels);
    }

    private Notification notifyInternal(Long recipientUserId, Notification.NotificationType type, String title, String content,
                                         Map<String, Object> metadata, String entityType, Long entityId,
                                         Notification.Priority priority, Long triggeredByUserId,
                                         Set<NotificationDelivery.Channel> forceChannels) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.notification.accountNotFound", new Object[]{recipientUserId},
                        "Không tìm thấy tài khoản id=" + recipientUserId));
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

        dispatchByPreference(notification, recipientUserId, type, forceChannels);
        return notification;
    }

    private void dispatchByPreference(Notification notification, Long recipientUserId, Notification.NotificationType type,
                                       Set<NotificationDelivery.Channel> forceChannels) {
        if (forceChannels != null && !forceChannels.isEmpty()) {
            for (NotificationDelivery.Channel channel : forceChannels) {
                if (channel == NotificationDelivery.Channel.IN_APP) {
                    createInAppDelivery(notification);
                } else {
                    createPendingDelivery(notification, channel);
                }
            }
            return;
        }

        NotificationPreference pref = notificationPreferenceRepository
                .findByUserIdAndNotificationType(recipientUserId, type).orElse(null);

        // Không có record -> mặc định in-app + email = enabled (Logic gửi thông báo bước 2).
        boolean inAppEnabled = pref == null || pref.isInAppEnabled();
        if (inAppEnabled) {
            createInAppDelivery(notification);
        }

        for (NotificationChannelSender sender : senders) {
            if (isChannelEnabled(sender.channel(), pref, recipientUserId, type)) {
                createPendingDelivery(notification, sender.channel());
            }
        }
    }

    /**
     * Mặc định khi user chưa có notification_preferences (bổ sung ngoài SDD
     * gốc — SDD chỉ nói mặc định in-app+email=enabled, không phân biệt theo
     * kênh PUSH/SMS/vai trò — đã xác nhận với người dùng 2026-08-08): PUSH
     * dùng cho thông báo hàng ngày nên mặc định bật cho MỌI user; EMAIL mặc
     * định bật cho MỌI user NGOẠI TRỪ Học sinh (mặc định tắt email cho Học
     * sinh khi chưa thiết lập preference — đã xác nhận 2026-08-08); SMS mặc
     * định chỉ bật riêng cho Phụ huynh/Học sinh (xem {@link #isParentOrStudent});
     * ZALO chưa trong phạm vi, giữ mặc định tắt. User vẫn có thể tự đổi qua
     * upsertPreference() — chỉ ảnh hưởng khi CHƯA có bản ghi.
     */
    private boolean isChannelEnabled(NotificationDelivery.Channel channel, NotificationPreference pref,
                                      Long recipientUserId, Notification.NotificationType type) {
        return switch (channel) {
            // Sửa đổi nghiệp vụ 2026-08-27 (đã xác nhận với người dùng, xem
            // docs/uc/phan-he-05-hoc-sinh.md): điểm danh PRESENT chỉ gửi
            // in-app, KHÔNG gửi email — bất kể notification_preferences của
            // Phụ huynh (quy tắc cứng, không phải preference có thể tự bật lại).
            case EMAIL -> type != Notification.NotificationType.ATTENDANCE_PRESENT
                    && (pref != null ? pref.isEmailEnabled() : !isStudent(recipientUserId));
            case PUSH -> pref == null || pref.isPushEnabled();
            case SMS -> pref != null ? pref.isSmsEnabled() : isParentOrStudent(recipientUserId);
            case ZALO -> pref != null && pref.isZaloEnabled();
            case IN_APP -> false; // xử lý riêng ở createInAppDelivery, không qua sender
        };
    }

    private boolean isStudent(Long userId) {
        return studentRepository.findByUserId(userId).isPresent();
    }

    private boolean isParentOrStudent(Long userId) {
        return parentRepository.findByUserId(userId).isPresent() || isStudent(userId);
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

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12 — xem DeviceTokenCountResponse. */
    @Transactional(readOnly = true)
    public List<DeviceTokenCountResponse> getActiveDeviceTokenCounts(List<Long> userIds) {
        return userIds.stream()
                .map(id -> new DeviceTokenCountResponse(id, deviceTokenRepository.findByUserIdAndActiveTrue(id).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMine(Long recipientUserId, Pageable pageable) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public NotificationResponse markRead(Long recipientUserId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notification.notFoundById",
                        new Object[]{notificationId}, "Không tìm thấy thông báo id=" + notificationId));
        if (!notification.getRecipientUser().getId().equals(recipientUserId)) {
            throw new ResourceNotFoundException("error.notification.notFoundById",
                    new Object[]{notificationId}, "Không tìm thấy thông báo id=" + notificationId);
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
                .orElseGet(() -> new NotificationPreferenceResponse(
                        type.name(), true, !isStudent(userId), isParentOrStudent(userId), false, true));
    }

    @Transactional
    public NotificationPreferenceResponse upsertPreference(Long userId, Notification.NotificationType type,
                                                             NotificationPreferenceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notification.accountNotFound",
                        new Object[]{userId}, "Không tìm thấy tài khoản id=" + userId));
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

    /**
     * Đăng ký/refresh device token cho kênh PUSH (xem PushNotificationSender,
     * bổ sung ngoài SDD gốc đã xác nhận 2026-08-08). Token là UNIQUE toàn hệ
     * thống — nếu client đăng nhập bằng tài khoản khác trên cùng thiết bị,
     * token cũ được gán lại sang user mới thay vì tạo bản ghi trùng.
     *
     * Vô hiệu hoá mọi token active KHÁC được coi là CÙNG 1 THIẾT BỊ — bổ sung
     * ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07. Nhận diện theo 2
     * dấu hiệu, khớp 1 trong 2 là coi như cùng thiết bị:
     *
     * 1. deviceId — UUID sinh + lưu localStorage phía client. Chính xác nhất
     *    khi còn dữ liệu, nhưng XOÁ APP CÀI LẠI là mất localStorage nên sinh
     *    deviceId mới hoàn toàn, token cũ của chính điện thoại đó không còn
     *    cách nào nhận ra.
     * 2. userAgent — server đọc thẳng từ HTTP header, KHÔNG phụ thuộc
     *    localStorage nên vẫn nhận ra đúng thiết bị sau khi cài lại app. Đây
     *    là dấu hiệu bù cho điểm yếu của deviceId (phát hiện qua thực tế:
     *    người dùng xoá/cài lại shortcut nhiều lần vẫn tích luỹ token, có lúc
     *    lên tới 7 token active).
     *
     * Đánh đổi đã cân nhắc: 2 thiết bị VẬT LÝ KHÁC NHAU nhưng cùng model/OS/
     * trình duyệt sẽ có userAgent giống hệt nhau nên bị coi là 1 — thiết bị
     * đăng nhập sau sẽ tắt push của thiết bị trước. Chấp nhận được vì trường
     * hợp 1 tài khoản dùng 2 máy giống hệt nhau rất hiếm, trong khi tác hại
     * của việc tích luỹ token (spam thông báo lặp) đã xảy ra thật.
     */
    @Transactional
    public void registerDeviceToken(Long userId, DeviceTokenRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notification.accountNotFound",
                        new Object[]{userId}, "Không tìm thấy tài khoản id=" + userId));

        String userAgent = httpRequest == null ? null : httpRequest.getHeader("User-Agent");
        deactivateSameDeviceTokens(userId, request, userAgent);

        DeviceToken deviceToken = deviceTokenRepository.findByToken(request.token())
                .orElseGet(DeviceToken::new);
        deviceToken.setUser(user);
        deviceToken.setToken(request.token());
        deviceToken.setPlatform(request.platform());
        deviceToken.setDeviceId(request.deviceId());
        deviceToken.setUserAgent(userAgent);
        deviceToken.setActive(true);
        deviceTokenRepository.save(deviceToken);

        enforceMaxActiveTokens(userId);
    }

    /** Vô hiệu hoá token active cũ của cùng thiết bị — khớp deviceId HOẶC userAgent (xem Javadoc trên). */
    private void deactivateSameDeviceTokens(Long userId, DeviceTokenRequest request, String userAgent) {
        boolean hasDeviceId = request.deviceId() != null && !request.deviceId().isBlank();
        boolean hasUserAgent = userAgent != null && !userAgent.isBlank();
        if (!hasDeviceId && !hasUserAgent) {
            return;
        }

        List<DeviceToken> sameDevice = deviceTokenRepository.findByUserIdAndActiveTrue(userId).stream()
                .filter(dt -> !dt.getToken().equals(request.token()))
                .filter(dt -> (hasDeviceId && request.deviceId().equals(dt.getDeviceId()))
                        || (hasUserAgent && userAgent.equals(dt.getUserAgent())))
                .toList();
        sameDevice.forEach(dt -> dt.setActive(false));
        deviceTokenRepository.saveAll(sameDevice);
    }

    /**
     * Giữ tối đa {@value #MAX_ACTIVE_TOKENS_PER_USER} token active gần nhất mỗi user — bổ sung ngoài
     * SDD gốc, đã xác nhận với người dùng 2026-09-07 sau sự cố THẬT trên staging: dedupe theo
     * deviceId không dọn được token cũ khi deviceId đổi (gỡ app cài lại → localStorage mất → sinh
     * deviceId mới) hay token đăng ký trước migration V163 (deviceId NULL), khiến 1 user tích luỹ 7
     * token active. Hệ quả dây chuyền: chuỗi token trong notification_deliveries.recipient_address
     * (VARCHAR(500)) bị tràn → transaction rollback → delivery kẹt PENDING → job nền gửi lại push
     * mỗi phút, người dùng bị spam thông báo lặp vô hạn.
     *
     * Chọn mốc 3: đủ cho người dùng thật (điện thoại + máy tính + 1 thiết bị phụ), và 3 token nối
     * bằng dấu phẩy (~480 ký tự) vẫn nằm gọn trong giới hạn VARCHAR(500) của recipient_address.
     */
    private static final int MAX_ACTIVE_TOKENS_PER_USER = 3;

    private void enforceMaxActiveTokens(Long userId) {
        List<DeviceToken> active = deviceTokenRepository.findByUserIdAndActiveTrue(userId);
        if (active.size() <= MAX_ACTIVE_TOKENS_PER_USER) {
            return;
        }
        List<DeviceToken> tooOld = active.stream()
                .sorted(Comparator.comparing(DeviceToken::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .skip(MAX_ACTIVE_TOKENS_PER_USER)
                .toList();
        tooOld.forEach(dt -> dt.setActive(false));
        deviceTokenRepository.saveAll(tooOld);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07): ghi log kết quả 1 lần chạy
     * setupPushNotifications() phía client. Best-effort — user không tồn tại (không nên xảy ra vì
     * đã qua xác thực JWT) chỉ bỏ qua, không throw, tránh ảnh hưởng luồng login chính.
     */
    @Transactional
    public void logPushSetupResult(Long userId, PushSetupLogRequest request, HttpServletRequest httpRequest) {
        userRepository.findById(userId).ifPresent(user -> {
            PushSetupLog log = new PushSetupLog();
            log.setUser(user);
            log.setStatus(request.status());
            log.setErrorMessage(request.errorMessage());
            log.setPlatform(request.platform());
            log.setUserAgent(httpRequest.getHeader("User-Agent"));
            pushSetupLogRepository.save(log);
        });
    }

    /** Vô hiệu hoá device token (VD lúc logout) — không xoá, giữ lịch sử. */
    @Transactional
    public void deactivateDeviceToken(Long userId, String token) {
        deviceTokenRepository.findByToken(token)
                .filter(dt -> dt.getUser().getId().equals(userId))
                .ifPresent(dt -> {
                    dt.setActive(false);
                    deviceTokenRepository.save(dt);
                });
    }
}
