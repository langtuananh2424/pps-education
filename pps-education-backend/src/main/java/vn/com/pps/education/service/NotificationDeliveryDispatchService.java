package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 * Xử lý ĐÚNG 1 notification_delivery trong 1 TRANSACTION RIÊNG
 * (PROPAGATION_REQUIRES_NEW) — tách khỏi vòng lặp quét của
 * {@link NotificationDispatchService#processDueDeliveries()}.
 *
 * Lý do bắt buộc tách (cùng pattern với StudentBatchImportRowService): trước
 * đây cả {@code processDueDeliveries()} là 1 {@code @Transactional} bọc toàn
 * bộ lô PENDING. Chỉ cần 1 delivery ném lỗi lúc flush/commit (VD chuỗi token
 * vượt {@code recipient_address VARCHAR(500)} — sự cố THẬT 2026-09-07) là CẢ
 * transaction rollback → mọi delivery trong lô quay lại PENDING → phút sau
 * job quét lại và GỬI LẠI tất → người dùng bị spam thông báo trùng (push đã
 * bay ra FCM thì không rollback được). Mỗi delivery 1 transaction riêng: 1
 * cái lỗi chỉ rollback đúng nó, các delivery khác vẫn commit trạng thái
 * SENT/FAILED bình thường.
 *
 * Nhận {@code deliveryId} (không nhận entity) để nạp lại delivery trong
 * transaction của chính method này — entity nạp ở vòng quét cha đã detached.
 *
 * max-retry-count / retry-interval-minutes: SDD không quy định số cụ thể,
 * mặc định 3 lần / 5 phút, chỉnh qua NOTIFICATION_MAX_RETRY /
 * NOTIFICATION_RETRY_INTERVAL_MINUTES.
 */
@Service
public class NotificationDeliveryDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryDispatchService.class);

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final Map<NotificationDelivery.Channel, NotificationChannelSender> sendersByChannel;
    private final int maxRetryCount;
    private final long retryIntervalMinutes;

    public NotificationDeliveryDispatchService(NotificationDeliveryRepository notificationDeliveryRepository,
                                               List<NotificationChannelSender> senders,
                                               @Value("${app.notification.max-retry-count}") int maxRetryCount,
                                               @Value("${app.notification.retry-interval-minutes}") long retryIntervalMinutes) {
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.sendersByChannel = senders.stream()
                .collect(Collectors.toMap(NotificationChannelSender::channel, Function.identity()));
        this.maxRetryCount = maxRetryCount;
        this.retryIntervalMinutes = retryIntervalMinutes;
    }

    /**
     * Gửi 1 delivery qua đúng sender theo channel, cập nhật trạng thái + lịch retry.
     * Nuốt exception nghiệp vụ (sender ném / trả false) và ghi FAILED — chỉ lỗi hạ
     * tầng lúc commit mới thoát ra ngoài để vòng quét cha log rồi bỏ qua.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Long deliveryId) {
        NotificationDelivery delivery = notificationDeliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            // đã bị xoá giữa 2 lần quét — không còn gì để làm
            return;
        }

        NotificationChannelSender sender = sendersByChannel.get(delivery.getChannel());
        if (sender == null) {
            markFailed(delivery, "Không có sender đăng ký cho kênh " + delivery.getChannel());
            notificationDeliveryRepository.save(delivery);
            return;
        }
        try {
            boolean sent = sender.send(delivery, delivery.getNotification(),
                    delivery.getNotification().getRecipientUser());
            if (sent) {
                delivery.setDeliveryStatus(NotificationDelivery.DeliveryStatus.SENT);
                delivery.setSentAt(OffsetDateTime.now());
            } else {
                // Sender trả về false (VD PushNotificationSender khi user không có device_tokens
                // active) thất bại HOÀN TOÀN ÂM THẦM — log WARN để thấy ngay trong log server thay
                // vì phải tra thẳng DB (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-05).
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
