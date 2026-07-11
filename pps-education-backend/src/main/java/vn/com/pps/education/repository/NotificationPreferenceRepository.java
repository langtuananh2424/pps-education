package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.NotificationPreference;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUserIdAndNotificationType(
            Long userId, Notification.NotificationType notificationType);
}
