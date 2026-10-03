package vn.com.pps.education.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.notification.domain.Notification;
import vn.com.pps.education.notification.domain.NotificationPreference;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUserIdAndNotificationType(
            Long userId, Notification.NotificationType notificationType);
}
