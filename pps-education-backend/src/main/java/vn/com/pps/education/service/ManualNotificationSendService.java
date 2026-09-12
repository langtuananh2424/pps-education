package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.dto.SendNotificationRequest;
import vn.com.pps.education.dto.SendNotificationResponse;
import vn.com.pps.education.repository.NotificationDeliveryRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gửi thông báo thủ công tới danh sách user được chọn — công cụ test/gửi
 * tay của Quản trị viên (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-08; nâng cấp 2026-09-12 để ép kênh + dispatch đồng bộ, trả kết
 * quả từng kênh ngay trong response thay vì chờ job quét mỗi phút).
 *
 * KHÔNG {@code @Transactional} ở class/method này — bắt buộc, vì mỗi lượt
 * {@link NotificationService#notify}/{@code notifyWithForcedChannels} phải
 * COMMIT xong (tạo notification + notification_deliveries PENDING) TRƯỚC
 * khi gọi {@link NotificationDeliveryDispatchService#dispatch} (chạy
 * PROPAGATION_REQUIRES_NEW, mở transaction/connection MỚI). Nếu method này
 * tự bọc {@code @Transactional}, toàn bộ vòng lặp sẽ nằm trong 1 transaction
 * chưa commit — dispatch() ở transaction khác sẽ KHÔNG thấy được delivery
 * vừa tạo (findById trả rỗng, âm thầm không gửi gì cả).
 */
@Service
public class ManualNotificationSendService {

    private final NotificationService notificationService;
    private final NotificationDeliveryDispatchService deliveryDispatchService;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

    public ManualNotificationSendService(NotificationService notificationService,
                                          NotificationDeliveryDispatchService deliveryDispatchService,
                                          NotificationDeliveryRepository notificationDeliveryRepository) {
        this.notificationService = notificationService;
        this.deliveryDispatchService = deliveryDispatchService;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
    }

    public SendNotificationResponse sendManual(SendNotificationRequest request, Long triggeredByUserId) {
        Set<NotificationDelivery.Channel> forceChannels = request.channels() == null
                ? null : new HashSet<>(request.channels());

        int succeeded = 0;
        List<SendNotificationResponse.SendNotificationFailure> failures = new ArrayList<>();
        List<SendNotificationResponse.ChannelResult> channelResults = new ArrayList<>();

        for (Long recipientUserId : request.recipientUserIds()) {
            try {
                Notification notification = notificationService.notifyWithForcedChannels(
                        recipientUserId, request.notificationType(), request.title(), request.content(),
                        Notification.Priority.NORMAL, triggeredByUserId, forceChannels);
                succeeded++;

                // Dispatch NGAY (đồng bộ) thay vì chờ NotificationDispatchService quét mỗi phút — công cụ
                // test cần thấy kết quả thật (SENT/FAILED) ngay trong response, không phải tra DB sau 1 phút.
                for (NotificationDelivery delivery : notificationDeliveryRepository.findByNotificationId(notification.getId())) {
                    if (delivery.getChannel() != NotificationDelivery.Channel.IN_APP) {
                        deliveryDispatchService.dispatch(delivery.getId());
                    }
                }

                for (NotificationDelivery delivery : notificationDeliveryRepository.findByNotificationId(notification.getId())) {
                    channelResults.add(new SendNotificationResponse.ChannelResult(
                            recipientUserId, delivery.getChannel().name(), delivery.getDeliveryStatus().name(),
                            delivery.getErrorMessage()));
                }
            } catch (Exception ex) {
                failures.add(new SendNotificationResponse.SendNotificationFailure(recipientUserId, ex.getMessage()));
            }
        }

        return new SendNotificationResponse(request.recipientUserIds().size(), succeeded, failures, channelResults);
    }
}
