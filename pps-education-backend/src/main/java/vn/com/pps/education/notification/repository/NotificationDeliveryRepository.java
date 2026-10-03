package vn.com.pps.education.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.notification.domain.NotificationDelivery;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    List<NotificationDelivery> findByDeliveryStatus(NotificationDelivery.DeliveryStatus status);

    List<NotificationDelivery> findByDeliveryStatusAndNextRetryAtLessThanEqual(
            NotificationDelivery.DeliveryStatus status, OffsetDateTime asOf);

    List<NotificationDelivery> findByNotificationId(Long notificationId);
}
