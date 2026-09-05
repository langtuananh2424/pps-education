package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.repository.NotificationDeliveryRepository;
import vn.com.pps.education.service.notification.NotificationChannelSender;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Background job xử lý delivery" (SDD > Notifications > Logic gửi thông
 * báo bước 4-5) — xử lý các notification_deliveries đang PENDING hoặc
 * FAILED tới hạn retry qua đúng NotificationChannelSender theo channel.
 * max-retry-count/retry-interval-minutes: SDD không quy định số cụ thể,
 * mặc định hợp lý (3 lần / 5 phút), chỉnh qua
 * NOTIFICATION_MAX_RETRY/NOTIFICATION_RETRY_INTERVAL_MINUTES.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final Map<NotificationDelivery.Channel, NotificationChannelSender> sendersByChannel;
    private final int maxRetryCount;
    private final long retryIntervalMinutes;

    public NotificationDispatchService(NotificationDeliveryRepository notificationDeliveryRepository,
                                        List<NotificationChannelSender> senders,
                                        @Value("${app.notification.max-retry-count}") int maxRetryCount,
                                        @Value("${app.notification.retry-interval-minutes}") long retryIntervalMinutes) {
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.sendersByChannel = senders.stream()
                .collect(Collectors.toMap(NotificationChannelSender::channel, Function.identity()));
        this.maxRetryCount = maxRetryCount;
        this.retryIntervalMinutes = retryIntervalMinutes;
    }

    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    public void processDueDeliveries() {
        OffsetDateTime now = OffsetDateTime.now();
        List<NotificationDelivery> due = notificationDeliveryRepository
                .findByDeliveryStatus(NotificationDelivery.DeliveryStatus.PENDING);
        due.addAll(notificationDeliveryRepository
                .findByDeliveryStatusAndNextRetryAtLessThanEqual(NotificationDelivery.DeliveryStatus.FAILED, now));
        due.forEach(this::dispatch);
    }

    private void dispatch(NotificationDelivery delivery) {
        NotificationChannelSender sender = sendersByChannel.get(delivery.getChannel());
        if (sender == null) {
            markFailed(delivery, "Không có sender đăng ký cho kênh " + delivery.getChannel());
            notificationDeliveryRepository.save(delivery);
            return;
        }
        try {
            boolean sent = sender.send(delivery, delivery.getNotification(), delivery.getNotification().getRecipientUser());
            if (sent) {
                delivery.setDeliveryStatus(NotificationDelivery.DeliveryStatus.SENT);
                delivery.setSentAt(OffsetDateTime.now());
            } else {
                // Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-05): trước đây nhánh này
                // không log gì (chỉ nhánh throw exception ở catch bên dưới mới log) — sender trả về
                // false (VD PushNotificationSender khi user không có device_tokens active) thất bại
                // HOÀN TOÀN ÂM THẦM, phải tra thẳng DB mới biết. Log WARN để thấy ngay trong log server.
                log.warn("Gửi notification_delivery id={} qua kênh {} cho recipient_user_id={} thất bại: sender trả về false",
                        delivery.getId(), delivery.getChannel(), delivery.getNotification().getRecipientUser().getId());
                markFailed(delivery, "Gửi thất bại (sender trả về false)");
            }
        } catch (Exception ex) {
            log.warn("Gửi notification_delivery id={} qua kênh {} thất bại: {}",
                    delivery.getId(), delivery.getChannel(), ex.getMessage());
            markFailed(delivery, ex.getMessage());
        }
        notificationDeliveryRepository.save(delivery);
    }

    private void markFailed(NotificationDelivery delivery, String errorMessage) {
        delivery.setDeliveryStatus(NotificationDelivery.DeliveryStatus.FAILED);
        delivery.setErrorMessage(errorMessage);
        delivery.setRetryCount(delivery.getRetryCount() + 1);
        delivery.setNextRetryAt(delivery.getRetryCount() < maxRetryCount
                ? OffsetDateTime.now().plusMinutes(retryIntervalMinutes)
                : null); // hết lượt retry -- không lên lịch thử lại nữa
    }
}
