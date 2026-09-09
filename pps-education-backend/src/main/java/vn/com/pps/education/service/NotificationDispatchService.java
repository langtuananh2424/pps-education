package vn.com.pps.education.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.com.pps.education.domain.NotificationDelivery;
import vn.com.pps.education.repository.NotificationDeliveryRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * "Background job xử lý delivery" (SDD > Task Management & Thông báo >
 * Notifications > "Logic gửi thông báo" bước 4-5) — mỗi phút quét
 * notification_deliveries đang PENDING hoặc FAILED tới hạn retry, giao TỪNG
 * cái cho {@link NotificationDeliveryDispatchService#dispatch(Long)} xử lý
 * trong transaction riêng.
 *
 * Vòng lặp này KHÔNG {@code @Transactional}: chỉ đọc danh sách id tới hạn rồi
 * lặp. 1 delivery lỗi (kể cả lỗi hạ tầng lúc commit) chỉ log rồi bỏ qua,
 * KHÔNG làm dừng cả vòng và KHÔNG kéo delivery khác rollback — xem Javadoc
 * {@link NotificationDeliveryDispatchService} để biết vì sao bắt buộc tách
 * transaction theo từng delivery.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationDeliveryDispatchService deliveryDispatchService;

    public NotificationDispatchService(NotificationDeliveryRepository notificationDeliveryRepository,
                                       NotificationDeliveryDispatchService deliveryDispatchService) {
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.deliveryDispatchService = deliveryDispatchService;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void processDueDeliveries() {
        OffsetDateTime now = OffsetDateTime.now();

        List<Long> dueIds = new ArrayList<>();
        notificationDeliveryRepository.findByDeliveryStatus(NotificationDelivery.DeliveryStatus.PENDING)
                .forEach(d -> dueIds.add(d.getId()));
        notificationDeliveryRepository
                .findByDeliveryStatusAndNextRetryAtLessThanEqual(NotificationDelivery.DeliveryStatus.FAILED, now)
                .forEach(d -> dueIds.add(d.getId()));

        for (Long deliveryId : dueIds) {
            try {
                deliveryDispatchService.dispatch(deliveryId);
            } catch (RuntimeException ex) {
                // dispatch() đã tự markFailed + commit cho lỗi nghiệp vụ (sender ném / trả false).
                // Tới đây chỉ còn lỗi ngoài dự kiến (VD DB rớt kết nối lúc commit): log ERROR rồi đi
                // tiếp — 1 delivery hỏng không được phép chặn phần còn lại của lô.
                log.error("Bỏ qua notification_delivery id={} do lỗi ngoài dự kiến khi xử lý", deliveryId, ex);
            }
        }
    }
}
